package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns card search, host communication, and printer. EMV after entry selection is
 * delegated to {@link EmvBehavior}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)   [fires, returns immediately]
 *          └── EmvBehavior.prepare + searchCard(config, listener)   ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── EmvBehavior.start(card)                ← vendor EMV, from inside the callback
 * </pre>
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final EmvBehavior behavior;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    private final AtomicBoolean searchCancelled = new AtomicBoolean(false);

    protected PosTerminal(EmvEngine engine,
            EmvBehavior behavior,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        this.engine = engine;
        this.behavior = behavior;
        this.communication = communication;
        this.printer = printer;
        this.engine.attachBehavior(behavior);
        this.engine.attachCommunication(communication);
        this.engine.attachPrinter(printer);
        initializeVendor();
    }

    public CommunicationBehavior communication() {
        return communication;
    }

    public PrinterBehavior printer() {
        return printer;
    }

    /**
     * Host authorize via the terminal-owned {@link CommunicationBehavior}, through
     * {@link EmvEngine#authorize}. Same call {@link EmvBehavior} implementations use — one
     * implementation, whether it's the terminal or a vendor asking.
     */
    public AuthResult authorize(TransactionConfig config) {
        return engine.authorize(config);
    }

    /** Print receipt lines via the terminal-owned {@link PrinterBehavior}, through {@link EmvEngine#print}. */
    public void printReceipt(List<String> lines) {
        engine.print(lines);
    }

    // ─── Public API (vendor-agnostic) ────────────────────────────────────

    public Observable<TransactionStepEvent> transactionSteps() {
        return engine.transactionSteps();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return engine.emvSteps();
    }

    public TransactionStepEvent currentState() {
        return engine.currentTransactionState();
    }

    /**
     * Accept any presented card (chip / tap / swipe / manual as enabled by the vendor).
     * Preferred UI entry point.
     */
    public final void acceptCard(String procCode, long amountMinor) {
        startTransaction(new TransactionConfig(procCode, amountMinor, EntryMethod.ANY));
    }

    /**
     * Start with an explicit reader mode: triggers card search on a background thread and
     * returns immediately. There is no return value to wait on — every outcome (card found,
     * search cancelled, timed out, or errored) arrives as a {@link CardSearchListener} callback,
     * which is also what starts EMV once a card is actually found (see
     * {@link EngineReportingListener#handleCardFound}).
     */
    public void startTransaction(TransactionConfig config) {
        searchCancelled.set(false);
        new Thread(() -> {
            try {
                beginTransaction(config);
            } catch (Exception e) {
                //noinspection CallToPrintStackTrace
                e.printStackTrace();
                engine.notifyError(e.getMessage() != null ? e.getMessage() : "Transaction failed");
            }
        }, "PosTerminal-search").start();
    }

    public void cancelTransaction() {
        searchCancelled.set(true);
        cancelCardSearch();
        engine.cancel();
    }

    public void dispose() {
        cancelTransaction();
    }

    public final boolean isSearchCancelled() {
        return searchCancelled.get();
    }

    // ─── Vendor must implement ───────────────────────────────────────────

    /**
     * Blocking card search using the vendor's native SDK.
     * Fire {@link CardSearchListener} events; return the selected card or {@code null}.
     */
    @Nullable
    public abstract CardPresence searchCard(TransactionConfig config, CardSearchListener listener);

    protected abstract void cancelCardSearch();

    protected void initializeVendor() {
        // default: no-op
    }

    // ─── Orchestration (fixed for all vendors) ───────────────────────────

    /**
     * One {@link EmvEngine#begin()} guards the whole transaction, including any retries a
     * behavior requests via {@link EmvEngine#requestRetry} (PAX fallback / try-another-
     * interface / try-again).
     */
    private void beginTransaction(TransactionConfig config) {
        if (!engine.begin()) {
            return;
        }
        triggerSearch(config);
    }

    /**
     * Fires card search and returns immediately — search and EMV are not sequenced by a
     * blocking return value here. Every {@link CardSearchListener} implementation (Pax/
     * Ingenico/Fake) always calls exactly one listener method before it stops searching, so
     * that callback — not this method's return — is what drives what happens next:
     * {@link EngineReportingListener#handleCardFound} starts EMV the moment a card is found,
     * and a behavior-requested retry re-enters this same method with the adjusted config —
     * still on this one thread, so it never races the attempt that requested it.
     */
    private void triggerSearch(TransactionConfig config) {
        if (!behavior.prepare(engine, config)) {
            engine.notifyError("Terminal initialization failed");
            return;
        }
        searchCard(config, new EngineReportingListener(config));
    }

    /** Maps reader events → engine subjects for the UI, and starts EMV once a card is found. */
    private final class EngineReportingListener implements CardSearchListener {

        private final TransactionConfig config;

        EngineReportingListener(TransactionConfig config) {
            this.config = config;
        }

        @Override
        public void onSearchStarted(TransactionConfig config) {
            String msg = waitingMessage(config);
            engine.notifyTransactionStep(TransactionStepEvent.of(
                    TransactionStep.WAITING_FOR_CARD, msg));
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, msg);
        }

        @Override
        public void onChipDetected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onContactlessDetected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onMagstripeDetected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onManualEntrySelected(CardPresence card) {
            handleCardFound(card);
        }

        @Override
        public void onCardRemoved() {
            engine.notifyTransactionStep(TransactionStepEvent.of(
                    TransactionStep.WAITING_FOR_CARD, "Card removed"));
        }

        @Override
        public void onSearchTimeout() {
            engine.notifyError("Card search timeout");
        }

        @Override
        public void onSearchCancelled() {
            if (engine.isRunning()) {
                engine.notifyError("Card search cancelled");
            }
        }

        @Override
        public void onReaderError(String message) {
            engine.notifyError(message != null ? message : "Reader error");
        }

        /** Runs EMV right here, then re-triggers search if the behavior asked for a retry. */
        private void handleCardFound(CardPresence card) {
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .put("entryMethod", card.getEntryMethod().name())
                    .build());

            if (searchCancelled.get()) {
                return;
            }

            behavior.start(engine, config, card);

            TransactionConfig retryConfig = engine.consumePendingRetry();
            if (retryConfig != null) {
                triggerSearch(retryConfig);
            }
        }
    }

    private static String waitingMessage(TransactionConfig config) {
        if (config.getMode() == EntryMethod.ANY) {
            return "Insert, tap, or swipe";
        }
        if (config.isContact()) {
            return "Insert card";
        }
        if (config.isMagstripe()) {
            return "Swipe card";
        }
        if (config.isManual()) {
            return "Enter card number";
        }
        return "Tap card";
    }
}

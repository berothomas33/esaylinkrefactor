package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.util.ApduTrace;
import io.reactivex.rxjava3.core.Observable;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns card search, host communication, and printer. EMV after entry selection is
 * delegated to its {@link EmvEngine}, which owns the vendor's {@link EmvBehavior}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)   [fires, returns immediately]
 *          └── engine.prepare + searchCard(config, listener)        ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── engine.start(card)                     ← vendor EMV, from inside the callback
 *                               └── behavior → engine.authorize → PosTerminal.authorize → host
 * </pre>
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;

    private final AtomicBoolean searchCancelled = new AtomicBoolean(false);

    /**
     * @param behavior the vendor's EMV — handed to this terminal's {@link EmvEngine}, which owns
     *                 it; the terminal itself only ever talks to the engine
     */
    protected PosTerminal(EmvBehavior behavior,
            CommunicationBehavior communication,
            PrinterBehavior printer) {
        this.communication = communication;
        this.printer = printer;
        this.engine = new EmvEngine(behavior, this::authorize);
        initializeVendor();
    }

    public CommunicationBehavior communication() {
        return communication;
    }

    public PrinterBehavior printer() {
        return printer;
    }

    /**
     * Host authorize via the terminal-owned {@link CommunicationBehavior}. This is also where a
     * vendor's {@link EmvBehavior} goes online — {@link EmvEngine#authorize} hands its request
     * here — so it's one implementation, whether it's the terminal or a vendor asking.
     *
     * <p>Publishes {@code ONLINE_REQUIRED} / {@code ONLINE_PROCESSING} / {@code ONLINE_COMPLETED}
     * around the call, so a vendor gets the notify sequence for free instead of repeating it.
     */
    public AuthResult authorize(TransactionConfig config) {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        AuthResult result;
        try {
            result = communication.authorize(config).blockingGet();
        } catch (Exception e) {
            // No usable host answer (network, timeout, envelope error): online failed, not a
            // host decline.
            result = AuthResult.failed(e.getMessage() != null ? e.getMessage() : "Online failed");
        }
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, result.getMessage())
                .build());
        return result;
    }

    /**
     * Print receipt lines via the terminal-owned {@link PrinterBehavior}. Runs off the calling
     * (UI) thread — {@code print()} blocks until the job finishes, and a real printer is slow
     * enough that blocking the UI thread would ANR. A failure (no paper, DAL not ready, printer
     * busy) is logged, not thrown — the caller asked to print and walked away; it has no
     * exception handler waiting.
     */
    public void printReceipt(List<String> lines) {
        new Thread(() -> {
            try {
                printer.print(lines).blockingAwait();
            } catch (Throwable t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
            }
        }, "PosTerminal-print").start();
    }

    // ─── Public API (vendor-agnostic) ────────────────────────────────────

    public Observable<TransactionStepEvent> transactionSteps() {
        return engine.transactionSteps();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return engine.emvSteps();
    }

    /**
     * The current transaction's APDU trace — owned by the engine, alongside
     * {@link #transactionSteps()} and {@link #emvSteps()}: observe
     * {@link ApduTrace#changes()} and read {@link ApduTrace#text()}.
     */
    public ApduTrace apduTrace() {
        return engine.apduTrace();
    }

    public TransactionStepEvent currentState() {
        return engine.currentTransactionState();
    }

    /**
     * Accept any presented card (chip / tap / swipe / manual as enabled by the vendor).
     * Preferred UI entry point.
     */
    public final void acceptCard(TransactionType type, long amountMinor) {
        acceptCard(type, amountMinor, null);
    }

    /**
     * {@link #acceptCard(TransactionType, long)} with the original sale's reference number, which
     * a refund needs for the host — see {@link TransactionConfig#getReferenceNumber()}.
     */
    public final void acceptCard(TransactionType type, long amountMinor, @Nullable String referenceNumber) {
        startTransaction(new TransactionConfig(type, amountMinor, EntryMethod.ANY)
                .withReferenceNumber(referenceNumber));
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
            } catch (Throwable t) {
                //noinspection CallToPrintStackTrace
                t.printStackTrace();
                engine.notifyError(t.getMessage() != null ? t.getMessage() : "Transaction failed");
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
        engine.dispose();
    }

    public final boolean isSearchCancelled() {
        return searchCancelled.get();
    }

    /**
     * Debug switch for the {@link #apduTrace()}: whether to record every APDU command/response
     * and step. Always off in a release build (the trace holds card data).
     */
    public final void setApduLoggingEnabled(boolean enabled) {
        engine.apduTrace().setEnabled(enabled);
    }

    public final boolean isApduLoggingEnabled() {
        return engine.apduTrace().isEnabled();
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
        if (!engine.prepare(config)) {
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
            String prompt = engine.consumeRetryPrompt();
            String msg = prompt != null ? prompt : waitingMessage(config);
            TransactionStepEvent.Builder waiting = TransactionStepEvent
                    .builder(TransactionStep.WAITING_FOR_CARD).message(msg);
            if (prompt != null) {
                waiting.put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt);
            }
            engine.notifyTransactionStep(waiting.build());
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
            engine.notifyError("No card presented — card search timed out");
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

        @Override
        public void onSearchRetry(String message) {
            engine.notifyTransactionStep(TransactionStepEvent.builder(
                    TransactionStep.WAITING_FOR_CARD).message(message)
                    .put(TransactionStepEvent.KEY_RETRY_PROMPT, message).build());
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, message);
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

            engine.start(config, card);

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

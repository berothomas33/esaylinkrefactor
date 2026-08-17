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
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The only public hardware API for the app.
 *
 * <p>Owns card search, host communication, and printer. EMV after entry selection is
 * delegated to {@link EmvBehavior}.
 *
 * <pre>
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)
 *          ├── EmvBehavior.prepare
 *          ├── searchCard(config, listener)   ← vendor SDK
 *          └── EmvBehavior.start(card)        ← vendor EMV
 * </pre>
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final EmvBehavior behavior;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;
    protected final CompositeDisposable disposables = new CompositeDisposable();

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
        startTransaction(new TransactionConfig(procCode, amountMinor, TransactionConfig.Mode.ANY));
    }

    /** Start with an explicit reader mode. */
    public void startTransaction(TransactionConfig config) {
        searchCancelled.set(false);
        disposables.add(
                io.reactivex.rxjava3.core.Completable.fromAction(() -> runTransaction(config))
                        .subscribeOn(Schedulers.io())
                        .subscribe(() -> {}, error -> {
                            //noinspection CallToPrintStackTrace
                            error.printStackTrace();
                            engine.notifyError(error.getMessage() != null
                                    ? error.getMessage()
                                    : "Transaction failed");
                        })
        );
    }

    public void cancelTransaction() {
        searchCancelled.set(true);
        cancelCardSearch();
        engine.cancel();
    }

    public void dispose() {
        cancelTransaction();
        disposables.clear();
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

    private void runTransaction(TransactionConfig config) {
        if (!engine.begin()) {
            return;
        }

        if (!behavior.prepare(engine, config)) {
            engine.notifyError("Terminal initialization failed");
            return;
        }

        CardPresence card = searchCard(config, new EngineReportingListener(config));
        if (searchCancelled.get()) {
            return;
        }
        if (card == null) {
            if (engine.isRunning()) {
                engine.notifyError("Card search failed");
            }
            return;
        }

        behavior.start(engine, config, card);
    }

    /** Maps reader events → engine subjects for the UI. */
    private final class EngineReportingListener implements CardSearchListener {

        EngineReportingListener(TransactionConfig ignored) {
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
            reportDetected(card);
        }

        @Override
        public void onContactlessDetected(CardPresence card) {
            reportDetected(card);
        }

        @Override
        public void onMagstripeDetected(CardPresence card) {
            reportDetected(card);
        }

        @Override
        public void onManualEntrySelected(CardPresence card) {
            reportDetected(card);
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

        private void reportDetected(CardPresence card) {
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .put("entryMethod", card.getEntryMethod().name())
                    .build());
        }
    }

    private static String waitingMessage(TransactionConfig config) {
        if (config.getMode() == TransactionConfig.Mode.ANY) {
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

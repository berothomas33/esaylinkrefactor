package com.emvenhance.core;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract POS terminal — owns card-reader hardware and the unified {@link #searchCard} API.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Vendor device initialization
 *   <li>Open/close readers
 *   <li>Unified card search with {@link CardSearchListener} events
 *   <li>Cancel search
 *   <li>Create the matching {@link EmvBehavior}
 * </ul>
 *
 * <p>After an entry method is selected (chip / contactless / mag / manual), the terminal
 * hands off to {@link EmvBehavior#start} for the EMV transaction. No EMV business logic
 * lives here.
 *
 * <pre>
 *   PaxTerminal      → PaxEmvBehavior
 *   IngenicoTerminal → IngenicoEmvBehavior
 *   FakeTerminal     → FakeEmvBehavior
 * </pre>
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final EmvBehavior behavior;
    protected final CompositeDisposable disposables = new CompositeDisposable();

    private final AtomicBoolean searchCancelled = new AtomicBoolean(false);

    protected PosTerminal(EmvEngine engine, EmvBehavior behavior) {
        this.engine = engine;
        this.behavior = behavior;
        this.engine.attachBehavior(behavior);
        initializeVendor();
    }

    // ─── Public API ──────────────────────────────────────────────────────

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
     * Unified transaction entry:
     * <pre>
     *   prepare → searchCard(listener) → EmvBehavior.start(selected entry)
     * </pre>
     */
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

    /** Cancels card search and in-flight EMV. */
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

    // ─── Unified card search (vendor implements with native SDK) ─────────

    /**
     * Blocking card search. Must report progress through {@code listener} and return the
     * selected {@link CardPresence}, or {@code null} on timeout / cancel / error.
     *
     * <p>Implementations must call the matching detection callback before returning a card:
     * {@link CardSearchListener#onChipDetected}, {@code onContactlessDetected},
     * {@code onMagstripeDetected}, or {@code onManualEntrySelected}.
     */
    @Nullable
    public abstract CardPresence searchCard(TransactionConfig config, CardSearchListener listener);

    /** Stops an in-flight {@link #searchCard} loop. */
    protected abstract void cancelCardSearch();

    protected void initializeVendor() {
        // default: no-op
    }

    // ─── Orchestration template ──────────────────────────────────────────

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
            // timeout / error already reported via listener
            if (engine.isRunning()) {
                engine.notifyError("Card search failed");
            }
            return;
        }

        // Entry method selected — EmvBehavior owns the EMV flow from here.
        behavior.start(engine, config, card);
    }

    /**
     * Forwards reader events to the engine subjects so the UI observes search progress.
     */
    private final class EngineReportingListener implements CardSearchListener {
        private final TransactionConfig config;

        EngineReportingListener(TransactionConfig config) {
            this.config = config;
        }

        @Override
        public void onSearchStarted(TransactionConfig config) {
            engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD,
                    waitingMessage(config)));
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, waitingMessage(config));
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
        if (config.isContact()) {
            return "Insert card";
        }
        if (config.isMagstripe()) {
            return "Swipe card";
        }
        if (config.isManual()) {
            return "Enter card number";
        }
        if (config.getMode() == TransactionConfig.Mode.ANY) {
            return "Insert, tap, or swipe";
        }
        return "Tap card";
    }
}

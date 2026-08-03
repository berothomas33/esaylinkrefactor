package com.emvenhance.core;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.List;


public final class PosTerminal {

    private final EmvEngine engine;
    private final CommunicationBehavior communication;
    private final PrinterBehavior printer;
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** The config of the transaction currently in progress, or null when idle. */
    private volatile TransactionConfig activeConfig;

    public PosTerminal(EmvEngine engine, CommunicationBehavior communication,
            PrinterBehavior printer) {
        this.engine = engine;
        this.communication = communication;
        this.printer = printer;

        wireReactiveOrchestration();
    }

    // ─── Public API: observables for the UI ──────────────────────────────

    /**
     * High-level transaction lifecycle.
     * Delegates directly to the engine — no transformation, no extra layer.
     */
    public Observable<TransactionStepEvent> transactionSteps() {
        return engine.transactionSteps();
    }

    /**
     * Fine-grained EMV kernel progress.
     * Delegates directly to the engine.
     */
    public Observable<EmvStepEvent> emvSteps() {
        return engine.emvSteps();
    }

    /** Snapshot of the current transaction state without subscribing. */
    public TransactionStepEvent currentState() {
        return engine.currentTransactionState();
    }

    // ─── Public API: start a transaction ─────────────────────────────────

    /**
     * Starts a new transaction. The full lifecycle runs reactively from here:
     *
     * <pre>
     *   prepare → execute → [ONLINE_REQUIRED → authorize → complete] → print
     * </pre>
     *
     * <p>This method returns immediately; all work happens on background schedulers.
     * Subscribe to {@link #transactionSteps()} and {@link #emvSteps()} to observe
     * progress.
     */
    public void startTransaction(TransactionConfig config) {
        activeConfig = config;

        disposables.add(
                io.reactivex.rxjava3.core.Completable.fromAction(() -> {
                    if (!engine.prepare(config)) {
                        engine.emitError("Terminal initialization failed");
                        return;
                    }
                    engine.execute();
                })
                .subscribeOn(Schedulers.io())
                .subscribe(() -> {}, Throwable::printStackTrace)
        );
    }

    // ─── Internal: reactive wiring ───────────────────────────────────────

    /**
     * Sets up the reactive subscriptions that drive the orchestration.
     *
     * <p>This is the <em>only</em> place that couples the three behaviors together, and
     * it does so purely through subject subscriptions — no callback, no interface
     * implementation, no observer pattern by hand.
     */
    private void wireReactiveOrchestration() {

        // When the engine says "go online" → call the communication behavior,
        // then feed the result back to the engine.
        disposables.add(engine.transactionSteps()
                .filter(e -> e.getStep() == TransactionStep.ONLINE_REQUIRED)
                .flatMapSingle(e -> {
                    engine.emitTransactionStep(
                            TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
                    return communication.authorize(activeConfig)
                            .subscribeOn(Schedulers.io());
                })
                .observeOn(Schedulers.io())
                .subscribe(
                        authResult -> {
                            engine.emitTransactionStep(TransactionStepEvent.builder(
                                    TransactionStep.ONLINE_COMPLETED)
                                    .put(TransactionStepEvent.KEY_RESULT,
                                            authResult.getMessage())
                                    .build());
                            engine.complete(authResult);
                        },
                        Throwable::printStackTrace
                ));

        // When the transaction is approved → print a receipt.
        disposables.add(engine.transactionSteps()
                .filter(e -> e.getStep() == TransactionStep.APPROVED)
                .flatMapCompletable(e -> {
                    List<String> receipt = buildReceipt(e);
                    return printer.print(receipt)
                            .subscribeOn(Schedulers.io())
                            .onErrorComplete(); // print failure is non-fatal
                })
                .subscribe(() -> {}, Throwable::printStackTrace));
    }

    private List<String> buildReceipt(TransactionStepEvent event) {
        return Arrays.asList(
                "=== RECEIPT ===",
                "PAN:    " + event.getString(TransactionStepEvent.KEY_PAN),
                "Issuer: " + event.getString(TransactionStepEvent.KEY_ISSUER_NAME),
                "Amount: " + (activeConfig != null ? activeConfig.getAmountMinor() : "—"),
                "Result: " + event.getString(TransactionStepEvent.KEY_RESULT),
                "==============="
        );
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────

    /** Call when the application is finishing. */
    public void dispose() {
        disposables.clear();
    }
}

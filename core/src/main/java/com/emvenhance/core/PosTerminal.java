package com.emvenhance.core;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.List;

/**
 * Default POS terminal orchestrator.
 *
 * <h3>Architecture</h3>
 *
 * <p>{@link #wireReactiveOrchestration()} subscribes <strong>once</strong> to the
 * transaction-step stream and dispatches each event to a dedicated handler. There is no
 * {@code flatMap} chain linking steps together — each handler owns its own asynchronous
 * work (online authorization, printing, etc.).
 *
 * <h3>Vendor customization (Open/Closed)</h3>
 *
 * <p>POS vendors extend this class and override only the handlers they need:
 * <pre>{@code
 *   public class IngenicoPosTerminal extends PosTerminal {
 *       @Override
 *       protected void handleOnlineRequired(TransactionStepEvent event) {
 *           // Ingenico-specific host protocol
 *       }
 *   }
 *
 *   public class PaxPosTerminal extends PosTerminal {
 *       @Override
 *       protected void handlePrintReceipt(TransactionStepEvent event) {
 *           // PAX printer SDK
 *       }
 *   }
 * }</pre>
 *
 * <p>Vendors must not rewrite {@link #wireReactiveOrchestration()} — the dispatch loop
 * stays closed for modification while individual steps remain open for extension.
 */
public class PosTerminal {

    protected final EmvEngine engine;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;
    protected final CompositeDisposable disposables = new CompositeDisposable();

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

    /** Config of the in-flight transaction, or {@code null} when idle. */
    protected final TransactionConfig getActiveConfig() {
        return activeConfig;
    }

    // ─── Public API: start a transaction ─────────────────────────────────

    /**
     * Starts a new transaction. The full lifecycle runs from here:
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
                .subscribe(() -> {}, this::onHandlerError)
        );
    }

    // ─── Internal: reactive wiring (closed for modification) ─────────────

    /**
     * Subscribes once to the transaction-step stream and dispatches each step to its
     * dedicated handler. Vendors customize behavior by overriding handlers — not this
     * method.
     */
    private void wireReactiveOrchestration() {
        disposables.add(engine.transactionSteps()
                .observeOn(Schedulers.io())
                .subscribe(this::dispatchTransactionStep, this::onHandlerError));
    }

    /**
     * Routes a transaction step event to its dedicated handler.
     *
     * <p>Protected so a vendor that needs entirely custom routing can override, but the
     * default switch covers every {@link TransactionStep}. Prefer overriding individual
     * {@code handle*} methods instead.
     */
    protected void dispatchTransactionStep(TransactionStepEvent event) {
        switch (event.getStep()) {
            case IDLE:
                handleIdle(event);
                break;
            case TRANSACTION_STARTED:
                handleTransactionStarted(event);
                break;
            case WAITING_FOR_CARD:
                handleWaitingForCard(event);
                break;
            case CARD_DETECTED:
                handleCardDetected(event);
                break;
            case APPLICATION_SELECTED:
                handleApplicationSelected(event);
                break;
            case CARD_READ:
                handleCardRead(event);
                break;
            case CARDHOLDER_VERIFIED:
                handleCardholderVerified(event);
                break;
            case ONLINE_REQUIRED:
                handleOnlineRequired(event);
                break;
            case ONLINE_PROCESSING:
                handleOnlineProcessing(event);
                break;
            case ONLINE_COMPLETED:
                handleOnlineCompleted(event);
                break;
            case APPROVED:
                handleApproved(event);
                break;
            case DECLINED:
                handleDeclined(event);
                break;
            case COMPLETED:
                handleCompleted(event);
                break;
            case ERROR:
                handleError(event);
                break;
            default:
                handleUnknownStep(event);
                break;
        }
    }

    // ─── Step handlers (open for extension) ──────────────────────────────

    /** No orchestration work while idle. */
    protected void handleIdle(TransactionStepEvent event) {
        // default: no-op
    }

    /** Transaction has been accepted by the engine. */
    protected void handleTransactionStarted(TransactionStepEvent event) {
        // default: no-op — UI observes the stream directly
    }

    /** Terminal is waiting for a card tap or insert. */
    protected void handleWaitingForCard(TransactionStepEvent event) {
        // default: no-op
    }

    /** Card presence established. */
    protected void handleCardDetected(TransactionStepEvent event) {
        // default: no-op
    }

    /** EMV application selected. */
    protected void handleApplicationSelected(TransactionStepEvent event) {
        // default: no-op
    }

    /** Application data (PAN, issuer, …) available. */
    protected void handleCardRead(TransactionStepEvent event) {
        // default: no-op
    }

    /** Cardholder verification finished. */
    protected void handleCardholderVerified(TransactionStepEvent event) {
        // default: no-op
    }

    /**
     * Kernel asked to go online. Default behavior: emit {@code ONLINE_PROCESSING},
     * authorize against the host, emit {@code ONLINE_COMPLETED}, then call
     * {@link EmvEngine#complete(AuthResult)}.
     *
     * <p>Vendors (e.g. Ingenico) override this method alone to customize host protocol.
     */
    protected void handleOnlineRequired(TransactionStepEvent event) {
        TransactionConfig config = activeConfig;
        if (config == null) {
            engine.emitError("Online required but no active transaction config");
            return;
        }

        engine.emitTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));

        disposables.add(communication.authorize(config)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        authResult -> {
                            engine.emitTransactionStep(TransactionStepEvent.builder(
                                    TransactionStep.ONLINE_COMPLETED)
                                    .put(TransactionStepEvent.KEY_RESULT,
                                            authResult.getMessage())
                                    .build());
                            engine.complete(authResult);
                        },
                        error -> {
                            onHandlerError(error);
                            engine.emitError(error.getMessage() != null
                                    ? error.getMessage()
                                    : "Online authorization failed");
                        }
                ));
    }

    /** Host communication is in flight. */
    protected void handleOnlineProcessing(TransactionStepEvent event) {
        // default: no-op — emitted by handleOnlineRequired for UI progress
    }

    /** Host replied; kernel completion is underway. */
    protected void handleOnlineCompleted(TransactionStepEvent event) {
        // default: no-op
    }

    /**
     * Transaction approved. Default behavior delegates to
     * {@link #handlePrintReceipt(TransactionStepEvent)}.
     *
     * <p>Vendors that customize post-approval logic (but keep default printing) override
     * this and call {@code super.handleApproved(event)} or {@code handlePrintReceipt(event)}.
     */
    protected void handleApproved(TransactionStepEvent event) {
        handlePrintReceipt(event);
    }

    /**
     * Transaction declined. Default: no receipt. Override to print decline slips, beep,
     * or drive vendor LEDs.
     */
    protected void handleDeclined(TransactionStepEvent event) {
        // default: no-op
    }

    /**
     * Prints the merchant/cardholder receipt for the given outcome event.
     *
     * <p>Separated from {@link #handleApproved} so vendors (e.g. PAX) can override only
     * printing without touching approval handling. Print failure is non-fatal.
     */
    protected void handlePrintReceipt(TransactionStepEvent event) {
        List<String> receipt = buildReceipt(event);
        disposables.add(printer.print(receipt)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .subscribe(() -> {}, this::onHandlerError));
    }

    /** All post-processing done; engine is idle again. */
    protected void handleCompleted(TransactionStepEvent event) {
        // default: no-op
    }

    /** Unrecoverable error; transaction aborted. */
    protected void handleError(TransactionStepEvent event) {
        // default: no-op
    }

    /** Fallback for any step not covered by the switch (forward compatibility). */
    protected void handleUnknownStep(TransactionStepEvent event) {
        // default: no-op
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    /** Builds default receipt lines. Vendors may override for custom layouts. */
    protected List<String> buildReceipt(TransactionStepEvent event) {
        return Arrays.asList(
                "=== RECEIPT ===",
                "PAN:    " + event.getString(TransactionStepEvent.KEY_PAN),
                "Issuer: " + event.getString(TransactionStepEvent.KEY_ISSUER_NAME),
                "Amount: " + (activeConfig != null ? activeConfig.getAmountMinor() : "—"),
                "Result: " + event.getString(TransactionStepEvent.KEY_RESULT),
                "==============="
        );
    }

    /** Shared error sink for handler async work. */
    protected void onHandlerError(Throwable error) {
        //noinspection CallToPrintStackTrace
        error.printStackTrace();
    }

    // ─── Cleanup ─────────────────────────────────────────────────────────

    /** Call when the application is finishing. */
    public void dispose() {
        disposables.clear();
    }
}

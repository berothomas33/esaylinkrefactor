package com.emvenhance.core;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract POS terminal — owns hardware operations and the unified transaction API.
 *
 * <h3>Architecture</h3>
 *
 * <pre>
 *   PosTerminal
 *     ├── PaxTerminal        → PaxEmvBehavior  → PaxEmvEngine
 *     ├── IngenicoTerminal   → IngenicoEmvBehavior → …
 *     └── FakeTerminal       → FakeEmvBehavior → FakeEmvEngine
 * </pre>
 *
 * <p>Each vendor terminal is responsible for:
 * <ul>
 *   <li>Vendor initialization
 *   <li>Card detection (contact / contactless / magstripe)
 *   <li>Starting and canceling EMV
 *   <li>Vendor-specific hardware lifecycle
 * </ul>
 *
 * <p>The terminal creates its matching {@link EmvBehavior} and {@link EmvEngine}. Card search
 * never lives in the engine. EMV step hooks live on the engine via {@code dispatchEmvStep}.
 *
 * <p>This base class still orchestrates host authorization and receipt printing when the
 * engine emits the corresponding transaction steps.
 */
public abstract class PosTerminal {

    protected final EmvEngine engine;
    protected final EmvBehavior behavior;
    protected final CommunicationBehavior communication;
    protected final PrinterBehavior printer;
    protected final CompositeDisposable disposables = new CompositeDisposable();

    private volatile TransactionConfig activeConfig;
    private final AtomicBoolean searchCancelled = new AtomicBoolean(false);

    protected PosTerminal(EmvEngine engine, EmvBehavior behavior,
            CommunicationBehavior communication, PrinterBehavior printer) {
        this.engine = engine;
        this.behavior = behavior;
        this.communication = communication;
        this.printer = printer;
        this.engine.attachBehavior(behavior);
        wireTransactionOrchestration();
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

    protected final TransactionConfig getActiveConfig() {
        return activeConfig;
    }

    /**
     * Unified transaction entry point:
     * <pre>
     *   prepare → searchCard → execute(EMV) → [online → complete] → print
     * </pre>
     */
    public void startTransaction(TransactionConfig config) {
        activeConfig = config;
        searchCancelled.set(false);

        disposables.add(
                io.reactivex.rxjava3.core.Completable.fromAction(() -> runTransaction(config))
                        .subscribeOn(Schedulers.io())
                        .subscribe(() -> {}, this::onHandlerError)
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

    // ─── Template flow ───────────────────────────────────────────────────

    private void runTransaction(TransactionConfig config) {
        if (!engine.prepare(config)) {
            return;
        }

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD,
                waitingMessage(config)));
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, waitingMessage(config));

        CardPresence card = searchCard(config);
        if (searchCancelled.get()) {
            // cancelTransaction() already stopped the engine
            return;
        }
        if (card == null) {
            engine.notifyError("Card search timeout");
            return;
        }

        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                .build());

        engine.execute(card);
    }

    private static String waitingMessage(TransactionConfig config) {
        if (config.isContact()) {
            return "Insert card";
        }
        if (config.isMagstripe()) {
            return "Swipe card";
        }
        return "Tap card";
    }

    protected final boolean isSearchCancelled() {
        return searchCancelled.get();
    }

    // ─── Vendor hardware hooks ───────────────────────────────────────────

    /** One-time vendor SDK / device initialization. */
    protected void initializeVendor() {
        // default: no-op
    }

    /**
     * Blocking card search for contact / contactless / magstripe.
     *
     * @return detected card, or {@code null} on timeout
     */
    @Nullable
    protected abstract CardPresence searchCard(TransactionConfig config);

    /** Stops an in-flight {@link #searchCard} loop / reader poll. */
    protected abstract void cancelCardSearch();

    // ─── Transaction-step orchestration (online + print) ─────────────────

    private void wireTransactionOrchestration() {
        disposables.add(engine.transactionSteps()
                .observeOn(Schedulers.io())
                .subscribe(this::dispatchTransactionStep, this::onHandlerError));
    }

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

    protected void handleIdle(TransactionStepEvent event) {
    }

    protected void handleTransactionStarted(TransactionStepEvent event) {
    }

    protected void handleWaitingForCard(TransactionStepEvent event) {
    }

    protected void handleCardDetected(TransactionStepEvent event) {
    }

    protected void handleApplicationSelected(TransactionStepEvent event) {
    }

    protected void handleCardRead(TransactionStepEvent event) {
    }

    protected void handleCardholderVerified(TransactionStepEvent event) {
    }

    protected void handleOnlineRequired(TransactionStepEvent event) {
        TransactionConfig config = activeConfig;
        if (config == null) {
            engine.notifyError("Online required but no active transaction config");
            return;
        }

        engine.notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));

        disposables.add(communication.authorize(config)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        authResult -> {
                            engine.notifyTransactionStep(TransactionStepEvent.builder(
                                    TransactionStep.ONLINE_COMPLETED)
                                    .put(TransactionStepEvent.KEY_RESULT,
                                            authResult.getMessage())
                                    .build());
                            engine.complete(authResult);
                        },
                        error -> {
                            onHandlerError(error);
                            engine.complete(AuthResult.declined("96",
                                    error.getMessage() != null
                                            ? error.getMessage()
                                            : "Online authorization failed"));
                        }
                ));
    }

    protected void handleOnlineProcessing(TransactionStepEvent event) {
    }

    protected void handleOnlineCompleted(TransactionStepEvent event) {
    }

    protected void handleApproved(TransactionStepEvent event) {
        handlePrintReceipt(event);
    }

    protected void handleDeclined(TransactionStepEvent event) {
    }

    protected void handlePrintReceipt(TransactionStepEvent event) {
        List<String> receipt = buildReceipt(event);
        disposables.add(printer.print(receipt)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .subscribe(() -> {}, this::onHandlerError));
    }

    protected void handleCompleted(TransactionStepEvent event) {
    }

    protected void handleError(TransactionStepEvent event) {
    }

    protected void handleUnknownStep(TransactionStepEvent event) {
    }

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

    protected void onHandlerError(Throwable error) {
        //noinspection CallToPrintStackTrace
        error.printStackTrace();
    }
}

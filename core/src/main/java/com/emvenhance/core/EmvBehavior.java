package com.emvenhance.core;

/**
 * Vendor-specific EMV lifecycle — owns the EMV transaction <em>after</em> the terminal
 * has selected an entry method via {@link PosTerminal#searchCard}.
 *
 * <pre>
 *   PaxTerminal      → PaxEmvBehavior
 *   IngenicoTerminal → IngenicoEmvBehavior
 * </pre>
 */
public interface EmvBehavior {

    /**
     * Kernel / parameter initialization before card search.
     *
     * @return true when ready to search for a card
     */
    boolean prepare(EmvEngine engine, TransactionConfig config);

    /**
     * Runs EMV for an already-selected entry method ({@link CardPresence}).
     * Blocking; called on a background thread after {@link PosTerminal#searchCard}.
     */
    void start(EmvEngine engine, TransactionConfig config, CardPresence card);

    /** Cancels in-flight EMV / online wait. */
    void cancel();

    // ─── Transaction-step dispatch (default → onXxx hooks) ───────────────

    /**
     * Routes a high-level transaction step to the matching hook. Invoked by
     * {@link EmvEngine#notifyTransactionStep} after publishing to observers.
     */
    default void dispatchTransactionStep(TransactionStepEvent event) {
        switch (event.getStep()) {
            case IDLE:
                onIdle(event);
                break;
            case TRANSACTION_STARTED:
                onTransactionStarted(event);
                break;
            case WAITING_FOR_CARD:
                onWaitingForCard(event);
                break;
            case CARD_DETECTED:
                onCardDetected(event);
                break;
            case APPLICATION_SELECTED:
                onApplicationSelected(event);
                break;
            case CARD_READ:
                onCardRead(event);
                break;
            case CARDHOLDER_VERIFIED:
                onCardholderVerified(event);
                break;
            case ONLINE_REQUIRED:
                onOnlineRequired(event);
                break;
            case ONLINE_PROCESSING:
                onOnlineProcessing(event);
                break;
            case ONLINE_COMPLETED:
                onOnlineCompleted(event);
                break;
            case APPROVED:
                onApproved(event);
                break;
            case DECLINED:
                onDeclined(event);
                break;
            case COMPLETED:
                onCompleted(event);
                break;
            case ERROR:
                onError(event);
                break;
            default:
                onUnknownStep(event);
                break;
        }
    }

    /**
     * Routes a fine-grained EMV kernel step. Invoked by {@link EmvEngine#notifyEmvStep}.
     */
    default void dispatchEmvStep(EmvStepEvent event) {
        // Vendors override individual hooks or this method; defaults are no-ops via onEmvStep.
        onEmvStep(event);
    }

    // ─── Transaction lifecycle hooks (override what you need) ────────────

    default void onIdle(TransactionStepEvent event) {
    }

    default void onTransactionStarted(TransactionStepEvent event) {
    }

    default void onWaitingForCard(TransactionStepEvent event) {
    }

    default void onCardDetected(TransactionStepEvent event) {
    }

    default void onApplicationSelected(TransactionStepEvent event) {
    }

    default void onCardRead(TransactionStepEvent event) {
    }

    default void onCardholderVerified(TransactionStepEvent event) {
    }

    /**
     * Kernel / flow requires online authorization. Vendor implementations should
     * perform host authorization and deliver the result to unblock the kernel.
     */
    default void onOnlineRequired(TransactionStepEvent event) {
    }

    default void onOnlineProcessing(TransactionStepEvent event) {
    }

    default void onOnlineCompleted(TransactionStepEvent event) {
    }

    /** Override to print a receipt on approval. */
    default void onApproved(TransactionStepEvent event) {
    }

    default void onDeclined(TransactionStepEvent event) {
    }

    default void onPrintReceipt(TransactionStepEvent event) {
    }

    default void onCompleted(TransactionStepEvent event) {
    }

    default void onError(TransactionStepEvent event) {
    }

    default void onUnknownStep(TransactionStepEvent event) {
    }

    /** Optional catch-all for fine-grained EMV kernel steps. */
    default void onEmvStep(EmvStepEvent event) {
    }
}

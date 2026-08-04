package com.emvenhance.core;

/**
 * Vendor-specific EMV lifecycle — owns the full transaction flow for one POS vendor.
 *
 * <p>Each terminal creates its matching behavior:
 * <pre>
 *   PaxTerminal      → PaxEmvBehavior
 *   IngenicoTerminal → IngenicoEmvBehavior
 * </pre>
 *
 * <p>{@link PosTerminal} only performs hardware (card search / cancel). {@link EmvEngine}
 * only coordinates subjects and forwards step notifications here. All EMV business logic
 * and SDK adaptation live in the vendor behavior.
 */
public interface EmvBehavior {

    /**
     * Runs the complete EMV transaction (prepare → search via {@link CardReader} → kernel
     * → online → completion). Blocking; call on a background thread.
     */
    void start(EmvEngine engine, TransactionConfig config, CardReader cardReader);

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
     * Kernel / flow requires online authorization. Default implementations in
     * {@link AbstractEmvBehavior} perform host authorize and unblock the kernel.
     */
    default void onOnlineRequired(TransactionStepEvent event) {
    }

    default void onOnlineProcessing(TransactionStepEvent event) {
    }

    default void onOnlineCompleted(TransactionStepEvent event) {
    }

    /** Default in {@link AbstractEmvBehavior} prints a receipt. */
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

package com.emvenhance.core;

/**
 * Vendor EMV lifecycle <em>after</em> {@link PosTerminal#searchCard} selects an entry method.
 *
 * <p>Implement for each vendor. Shared online authorize / print live in
 * {@link AbstractEmvBehavior}.
 */
public interface EmvBehavior {

    /** Kernel / parameter init before card search. */
    boolean prepare(EmvEngine engine, TransactionConfig config);

    /** Run EMV for the already-selected {@link CardPresence}. Blocking. */
    void start(EmvEngine engine, TransactionConfig config, CardPresence card);

    /** Cancel in-flight EMV / online wait. */
    void cancel();

    // ─── Step dispatch (engine → hooks) ──────────────────────────────────

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

    default void dispatchEmvStep(EmvStepEvent event) {
        onEmvStep(event);
    }

    default void onIdle(TransactionStepEvent event) { }

    default void onTransactionStarted(TransactionStepEvent event) { }

    default void onWaitingForCard(TransactionStepEvent event) { }

    default void onCardDetected(TransactionStepEvent event) { }

    default void onApplicationSelected(TransactionStepEvent event) { }

    default void onCardRead(TransactionStepEvent event) { }

    default void onCardholderVerified(TransactionStepEvent event) { }

    /** Default in {@link AbstractEmvBehavior}: host authorize + unblock kernel. */
    default void onOnlineRequired(TransactionStepEvent event) { }

    default void onOnlineProcessing(TransactionStepEvent event) { }

    default void onOnlineCompleted(TransactionStepEvent event) { }

    /** Default in {@link AbstractEmvBehavior}: print receipt. */
    default void onApproved(TransactionStepEvent event) { }

    default void onDeclined(TransactionStepEvent event) { }

    default void onPrintReceipt(TransactionStepEvent event) { }

    default void onCompleted(TransactionStepEvent event) { }

    default void onError(TransactionStepEvent event) { }

    default void onUnknownStep(TransactionStepEvent event) { }

    default void onEmvStep(EmvStepEvent event) { }
}

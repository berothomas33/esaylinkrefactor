package com.emvenhance.core.terminal;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStepEvent;

/**
 * Vendor EMV lifecycle after {@link PosTerminal#searchCard} selects an entry method.
 *
 * <p>EMV only — no host authorize / printer logic lives here. Implement per vendor
 * (Pax / Ingenico / Fake / …).
 */
public interface EmvBehavior {

    /** Kernel / parameter init before card search. */
    boolean prepare(EmvEngine engine, TransactionConfig config);

    /** Run EMV for the already-selected {@link CardPresence}. Blocking. */
    void start(EmvEngine engine, TransactionConfig config, CardPresence card);

    /** Cancel in-flight EMV. */
    void cancel();

    // ─── Optional step hooks (engine → vendor) ───────────────────────────

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

    default void onOnlineRequired(TransactionStepEvent event) { }

    default void onOnlineProcessing(TransactionStepEvent event) { }

    default void onOnlineCompleted(TransactionStepEvent event) { }

    default void onApproved(TransactionStepEvent event) { }

    default void onDeclined(TransactionStepEvent event) { }

    default void onCompleted(TransactionStepEvent event) { }

    default void onError(TransactionStepEvent event) { }

    default void onUnknownStep(TransactionStepEvent event) { }

    default void onEmvStep(EmvStepEvent event) { }
}

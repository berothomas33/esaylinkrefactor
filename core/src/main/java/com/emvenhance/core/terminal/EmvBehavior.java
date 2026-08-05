package com.emvenhance.core.terminal;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStepEvent;

/**
 * Vendor EMV contract.
 *
 * <p>Lifecycle: {@link #prepare} → (PosTerminal search) → {@link #start} → {@link #cancel}.
 *
 * <p>Each EMV phase is a method ({@code onApplicationSelection}, …), and — with the single
 * exception of {@link #onSearchCard} — <b>every one of them is mandatory</b>: a vendor class
 * that doesn't override all of them fails to compile. This is deliberate. A silently
 * inherited no-op is indistinguishable from "the developer forgot this phase"; a compile
 * error is not. For a phase your kernel drives internally with no callback, the correct body
 * is a one-line comment saying so and, if the base class exposes it,
 * {@link AbstractEmvBehavior#announceStep} — that is a real, considered answer, not a stub.
 *
 * <p>The vendor does its work inside the method, then advances by calling
 * {@link AbstractEmvBehavior#goToStep} (publishes on the EmvStep observable and runs the
 * next method). No central {@code if (!run) return} chain.
 *
 * <p>EMV only — no host / printer logic here.
 */
public interface EmvBehavior {

    boolean prepare(EmvEngine engine, TransactionConfig config);

    void start(EmvEngine engine, TransactionConfig config, CardPresence card);

    void cancel();

    // ─── EMV step hooks — mandatory. Vendor writes action, then goToStep(next) ────

    void onTerminalInitialization(EmvEngine engine, TransactionConfig config);

    /**
     * The one hook that stays optional. {@link PosTerminal#searchCard} is already the real,
     * mandatory search implementation for every vendor — this method fires only if something
     * explicitly {@code goToStep(EmvStep.SEARCH_CARD)}s, which nothing does today. Requiring
     * an override here would force an empty body in every vendor for no benefit.
     */
    default void onSearchCard(EmvEngine engine, TransactionConfig config, CardPresence card) { }

    void onApplicationSelection(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onReadApplicationData(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onSetTransactionData(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onProcessRestrictions(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    void onStartOnlineProcess(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onIssuerAuthentication(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onScriptProcessing(EmvEngine engine, TransactionConfig config, CardPresence card);

    void onTransactionCompletion(EmvEngine engine, TransactionConfig config, CardPresence card);

    // ─── Optional transaction-step observers ─────────────────────────────

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

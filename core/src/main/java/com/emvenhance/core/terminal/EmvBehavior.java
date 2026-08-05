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
 * <p><b>Step methods</b> ({@code onTerminalInitialization} … {@code onTransactionCompletion})
 * are the behavior-driven hooks. Every vendor that extends / implements this type can
 * override each step and write its own action there. A sequential runner
 * ({@link AbstractEmvBehavior}) calls them in EMV order; kernel-driven vendors (PAX)
 * may keep a custom {@link #start} and call the same hooks from callbacks when ready.
 *
 * <p>EMV only — no host authorize / printer logic lives here.
 */
public interface EmvBehavior {

    /** Kernel / parameter init before card search. Typically runs {@link #onTerminalInitialization}. */
    boolean prepare(EmvEngine engine, TransactionConfig config);

    /** Run EMV for the already-selected {@link CardPresence}. Blocking. */
    void start(EmvEngine engine, TransactionConfig config, CardPresence card);

    /** Cancel in-flight EMV. */
    void cancel();

    // ─── EMV step hooks (override per vendor) ────────────────────────────

    /**
     * {@code TERMINAL_INITIALIZATION} — AID/CAPK load, kernel preTrans, terminal caps.
     * Called from {@link #prepare} by {@link AbstractEmvBehavior}.
     */
    default EmvStepResult onTerminalInitialization(EmvEngine engine, TransactionConfig config) {
        return EmvStepResult.continueResult();
    }

    /**
     * {@code SEARCH_CARD} — usually owned by {@link PosTerminal#searchCard}.
     * Default skips; override only if the vendor needs an in-EMV search phase.
     */
    default EmvStepResult onSearchCard(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.skip("Card search owned by PosTerminal");
    }

    /** {@code APPLICATION_SELECTION} — build candidate list / PPSE. */
    default EmvStepResult onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code WAIT_APPLICATION_SELECTION} — UI candidate picker when multiple AIDs. */
    default EmvStepResult onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code FINAL_APPLICATION_SELECTION} — confirm selected AID with card/kernel. */
    default EmvStepResult onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code READ_APPLICATION_DATA} — read records / AFL into TLV store. */
    default EmvStepResult onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code SET_TRANSACTION_DATA} — amount, date, TVR/TSI seeds, terminal TLVs. */
    default EmvStepResult onSetTransactionData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code OFFLINE_DATA_AUTHENTICATION} — SDA / DDA / CDA. */
    default EmvStepResult onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code PROCESS_RESTRICTIONS} — app version, AUC, effective/expiry dates. */
    default EmvStepResult onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code CARDHOLDER_VERIFICATION} — CVM list processing. */
    default EmvStepResult onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code OFFLINE_PIN_VERIFICATION} — offline PIN with PED / card. */
    default EmvStepResult onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code TERMINAL_RISK_MANAGEMENT} — floor limits, random, velocity. */
    default EmvStepResult onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code TERMINAL_ACTION_ANALYSIS} — offline approve / decline / go online. */
    default EmvStepResult onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /**
     * {@code START_ONLINE_PROCESS} — build ARQC path; typically
     * {@link EmvEngine#authorize(TransactionConfig)}.
     */
    default EmvStepResult onStartOnlineProcess(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code ISSUER_AUTHENTICATION} — ARPC / issuer auth data. */
    default EmvStepResult onIssuerAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code SCRIPT_PROCESSING} — issuer scripts 71/72. */
    default EmvStepResult onScriptProcessing(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    /** {@code TRANSACTION_COMPLETION} — 2nd GENERATE AC / finalize approve or decline. */
    default EmvStepResult onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    // ─── Optional transaction-step observers (engine → vendor) ───────────

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

package com.emvenhance.core;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract EMV engine — owns the EMV business flow, reactive subjects, and overridable
 * lifecycle hooks.
 *
 * <h3>Layering</h3>
 *
 * <pre>
 *   PosTerminal (hardware: card search, cancel, vendor lifecycle)
 *     → EmvEngine (business flow + dispatchEmvStep / hooks)
 *       ← EmvBehavior (vendor SDK callbacks → notify*)
 * </pre>
 *
 * <p>Behaviors call the public {@code notify*} API. Each EMV step is published on
 * {@link #emvSteps()} and routed through {@link #dispatchEmvStep} to an overridable hook
 * ({@link #onSearchCard}, {@link #onApplicationSelection}, …). Vendors subclass the engine
 * and override only the hooks they need.
 */
public abstract class EmvEngine {

    private final BehaviorSubject<TransactionStepEvent> transactionSteps =
            BehaviorSubject.createDefault(TransactionStepEvent.idle());

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Nullable
    private EmvBehavior behavior;

    @Nullable
    private CardPresence activeCard;

    // ─── Wiring ──────────────────────────────────────────────────────────

    /** Attaches the vendor behavior created by the terminal. */
    public final void attachBehavior(EmvBehavior behavior) {
        this.behavior = behavior;
    }

    @Nullable
    protected final EmvBehavior getBehavior() {
        return behavior;
    }

    @Nullable
    protected final CardPresence getActiveCard() {
        return activeCard;
    }

    // ─── Observables ─────────────────────────────────────────────────────

    public final Observable<TransactionStepEvent> transactionSteps() {
        return transactionSteps.hide();
    }

    public final Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    public final TransactionStepEvent currentTransactionState() {
        return transactionSteps.getValue();
    }

    public final boolean isRunning() {
        return running.get();
    }

    // ─── Public notify API (used by EmvBehavior — no forwarding bridge) ──

    /**
     * Publishes an EMV step and immediately dispatches it to the matching lifecycle hook.
     */
    public final void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        emvSteps.onNext(event);
        dispatchEmvStep(event);
    }

    public final void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    /** Publishes a high-level transaction milestone and dispatches outcome hooks. */
    public final void notifyTransactionStep(TransactionStepEvent event) {
        transactionSteps.onNext(event);
        dispatchTransactionOutcome(event);
    }

    public final void notifyCardDetected(String pan, String issuerName,
            String cardHolderName, String mode) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_PAN, pan)
                .put(TransactionStepEvent.KEY_ISSUER_NAME, issuerName)
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, cardHolderName)
                .put(TransactionStepEvent.KEY_MODE, mode)
                .build());
    }

    public final void notifyApproved(String result) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, result)
                .build());
    }

    public final void notifyDeclined(String reason) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, reason)
                .build());
    }

    public final void notifyError(String error) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build());
        releaseRunning();
        onTransactionFailed(error);
    }

    public final void notifyCompleted() {
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        releaseRunning();
        onTransactionCompleted();
    }

    // ─── Protected emit helpers (legacy name kept for subclasses) ────────

    protected final void emitTransactionStep(TransactionStepEvent event) {
        notifyTransactionStep(event);
    }

    protected final void emitEmvStep(EmvStep step, String detail) {
        notifyEmvStep(step, detail);
    }

    protected final void emitEmvStep(EmvStep step) {
        notifyEmvStep(step);
    }

    protected final void emitCardDetected(String pan, String issuerName,
            String cardHolderName, String mode) {
        notifyCardDetected(pan, issuerName, cardHolderName, mode);
    }

    protected final void emitApproved(String result) {
        notifyApproved(result);
    }

    protected final void emitDeclined(String reason) {
        notifyDeclined(reason);
    }

    protected final void emitError(String error) {
        notifyError(error);
    }

    // ─── Running lock ────────────────────────────────────────────────────

    protected final boolean acquireRunning() {
        return running.compareAndSet(false, true);
    }

    protected final void releaseRunning() {
        running.set(false);
    }

    // ─── Lifecycle template (terminal calls these) ───────────────────────

    /**
     * Initializes the engine / kernel for a new transaction (no card search).
     */
    public final boolean prepare(TransactionConfig config) {
        if (!acquireRunning()) {
            notifyError("A transaction is already running");
            return false;
        }
        activeCard = null;
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION);
        boolean ok = onPrepare(config);
        if (!ok) {
            notifyError("Terminal initialization failed");
        }
        return ok;
    }

    /**
     * Runs EMV for a card already found by the terminal.
     */
    public final void execute(CardPresence card) {
        this.activeCard = card;
        onCardDetected(card);
        onExecute(card);
    }

    /** Feeds host auth into the vendor behavior / kernel. */
    public final void complete(AuthResult authResult) {
        onComplete(authResult);
    }

    /** Cancels the in-flight EMV process. */
    public final void cancel() {
        onCancel();
        if (behavior != null) {
            behavior.cancel();
        }
        if (isRunning()) {
            notifyError("Transaction cancelled");
        }
    }

    // ─── Vendor overrides — business / kernel lifecycle ──────────────────

    /** Kernel / parameter init. Default delegates to {@link EmvBehavior#prepare}. */
    protected boolean onPrepare(TransactionConfig config) {
        return behavior != null && behavior.prepare(config);
    }

    /** Start EMV via the attached behavior. */
    protected void onExecute(CardPresence card) {
        if (behavior == null) {
            notifyError("No EMV behavior attached");
            return;
        }
        try {
            behavior.startEmv(this, card);
        } catch (Exception e) {
            notifyError(e.getMessage() != null ? e.getMessage() : "EMV execution failed");
        }
    }

    /** Deliver online result. Default delegates to {@link EmvBehavior#completeOnline}. */
    protected void onComplete(AuthResult authResult) {
        if (behavior != null) {
            behavior.completeOnline(authResult);
        }
    }

    /** Vendor-specific cancel hook (before behavior.cancel). */
    protected void onCancel() {
        // default: no-op
    }

    // ─── EMV step dispatch (open for extension) ──────────────────────────

    /**
     * Routes a fine-grained EMV kernel step to its dedicated hook.
     * Prefer overriding individual {@code on*} methods rather than this switch.
     */
    protected void dispatchEmvStep(EmvStepEvent event) {
        switch (event.getStep()) {
            case TERMINAL_INITIALIZATION:
                onTerminalInitialization(event);
                break;
            case SEARCH_CARD:
                onSearchCard(event);
                break;
            case APPLICATION_SELECTION:
                onApplicationSelection(event);
                break;
            case WAIT_APPLICATION_SELECTION:
                onWaitApplicationSelection(event);
                break;
            case FINAL_APPLICATION_SELECTION:
                onFinalApplicationSelection(event);
                break;
            case READ_APPLICATION_DATA:
                onReadApplicationData(event);
                break;
            case SET_TRANSACTION_DATA:
                onSetTransactionData(event);
                break;
            case OFFLINE_DATA_AUTHENTICATION:
                onOfflineDataAuthentication(event);
                break;
            case PROCESS_RESTRICTIONS:
                onProcessRestrictions(event);
                break;
            case CARDHOLDER_VERIFICATION:
                onCardholderVerification(event);
                break;
            case OFFLINE_PIN_VERIFICATION:
                onOfflinePinVerification(event);
                break;
            case TERMINAL_RISK_MANAGEMENT:
                onTerminalRiskManagement(event);
                break;
            case TERMINAL_ACTION_ANALYSIS:
                onTerminalActionAnalysis(event);
                break;
            case START_ONLINE_PROCESS:
                onOnlineProcessing(event);
                break;
            case ISSUER_AUTHENTICATION:
                onIssuerAuthentication(event);
                break;
            case SCRIPT_PROCESSING:
                onScriptProcessing(event);
                break;
            case TRANSACTION_COMPLETION:
                onEmvTransactionCompletion(event);
                break;
            default:
                onUnknownEmvStep(event);
                break;
        }
    }

    private void dispatchTransactionOutcome(TransactionStepEvent event) {
        switch (event.getStep()) {
            case CARD_DETECTED:
                // Hardware detection is reported by the terminal via execute(CardPresence);
                // this branch covers kernel-reported PAN/card details that reuse CARD_DETECTED.
                break;
            case APPROVED:
            case DECLINED:
                break;
            case COMPLETED:
                // release handled by notifyCompleted; hook also called there
                break;
            case ERROR:
                break;
            default:
                break;
        }
    }

    // ─── Overridable EMV lifecycle hooks ─────────────────────────────────

    /** Called when the terminal has established card presence (before startEmv). */
    protected void onCardDetected(CardPresence card) {
        // default: no-op — override for vendor LED / UI
    }

    protected void onTerminalInitialization(EmvStepEvent event) {
        // default: no-op
    }

    protected void onSearchCard(EmvStepEvent event) {
        // default: no-op — physical search is owned by PosTerminal
    }

    protected void onApplicationSelection(EmvStepEvent event) {
        // default: no-op
    }

    protected void onWaitApplicationSelection(EmvStepEvent event) {
        // default: no-op — override to show AID chooser
    }

    protected void onFinalApplicationSelection(EmvStepEvent event) {
        // default: no-op
    }

    protected void onReadApplicationData(EmvStepEvent event) {
        // default: no-op
    }

    protected void onSetTransactionData(EmvStepEvent event) {
        // default: no-op
    }

    protected void onOfflineDataAuthentication(EmvStepEvent event) {
        // default: no-op
    }

    protected void onProcessRestrictions(EmvStepEvent event) {
        // default: no-op
    }

    protected void onCardholderVerification(EmvStepEvent event) {
        // default: no-op
    }

    protected void onOfflinePinVerification(EmvStepEvent event) {
        // default: no-op
    }

    protected void onTerminalRiskManagement(EmvStepEvent event) {
        // default: no-op
    }

    protected void onTerminalActionAnalysis(EmvStepEvent event) {
        // default: no-op
    }

    protected void onOnlineProcessing(EmvStepEvent event) {
        // default: no-op — host work is driven by PosTerminal on ONLINE_REQUIRED
    }

    protected void onIssuerAuthentication(EmvStepEvent event) {
        // default: no-op
    }

    protected void onScriptProcessing(EmvStepEvent event) {
        // default: no-op
    }

    protected void onEmvTransactionCompletion(EmvStepEvent event) {
        // default: no-op
    }

    protected void onTransactionCompleted() {
        // default: no-op
    }

    protected void onTransactionFailed(String reason) {
        // default: no-op
    }

    protected void onUnknownEmvStep(EmvStepEvent event) {
        // default: no-op
    }
}

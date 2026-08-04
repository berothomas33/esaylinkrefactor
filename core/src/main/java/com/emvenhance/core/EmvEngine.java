package com.emvenhance.core;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin EMV coordinator — owns reactive subjects and forwards step notifications to
 * {@link EmvBehavior}. Contains no vendor EMV business logic and no {@code handleXxx} /
 * lifecycle hooks.
 *
 * <pre>
 *   PosTerminal (hardware)
 *     → EmvBehavior.start(...)   (vendor EMV lifecycle)
 *       → EmvEngine.notify*(...) (subjects + dispatch to behavior.onXxx)
 * </pre>
 *
 * <p>Adding a vendor never requires changing this class — only a new Terminal + Behavior.
 */
public final class EmvEngine {

    private final BehaviorSubject<TransactionStepEvent> transactionSteps =
            BehaviorSubject.createDefault(TransactionStepEvent.idle());

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Nullable
    private EmvBehavior behavior;

    public void attachBehavior(EmvBehavior behavior) {
        this.behavior = behavior;
    }

    @Nullable
    public EmvBehavior getBehavior() {
        return behavior;
    }

    // ─── Observables (UI) ────────────────────────────────────────────────

    public Observable<TransactionStepEvent> transactionSteps() {
        return transactionSteps.hide();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    public TransactionStepEvent currentTransactionState() {
        return transactionSteps.getValue();
    }

    public boolean isRunning() {
        return running.get();
    }

    // ─── Coordination ────────────────────────────────────────────────────

    /** Acquires the single-flight lock before {@link EmvBehavior#start}. */
    public boolean begin() {
        if (!running.compareAndSet(false, true)) {
            notifyError("A transaction is already running");
            return false;
        }
        return true;
    }

    public void cancel() {
        if (behavior != null) {
            behavior.cancel();
        }
        if (running.get()) {
            notifyError("Transaction cancelled");
        }
    }

    // ─── Notify API (behavior → subjects → behavior.onXxx) ───────────────

    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        emvSteps.onNext(event);
        if (behavior != null) {
            behavior.dispatchEmvStep(event);
        }
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    public void notifyTransactionStep(TransactionStepEvent event) {
        transactionSteps.onNext(event);
        if (behavior != null) {
            behavior.dispatchTransactionStep(event);
        }
    }

    public void notifyCardDetected(String pan, String issuerName,
            String cardHolderName, String mode) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_PAN, pan)
                .put(TransactionStepEvent.KEY_ISSUER_NAME, issuerName)
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, cardHolderName)
                .put(TransactionStepEvent.KEY_MODE, mode)
                .build());
    }

    public void notifyApproved(String result) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, result)
                .build());
    }

    public void notifyDeclined(String reason) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, reason)
                .build());
    }

    public void notifyError(String error) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build());
        running.set(false);
    }

    public void notifyCompleted() {
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        running.set(false);
    }
}

package com.emvenhance.core.engine;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.terminal.EmvBehavior;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin event bus for one in-flight transaction. No vendor logic.
 *
 * <p>{@link PosTerminal} owns search; {@link EmvBehavior} owns EMV; this class publishes
 * steps on the {@link #transactionSteps()} / {@link #emvSteps()} observables. It also
 * subscribes to its own {@link #transactionSteps()} to run {@code dispatchTransactionStepMethod}
 * — the one place engine-side bookkeeping tied to a specific step (e.g. clearing the running
 * flag on ERROR/COMPLETED) lives, instead of scattering it across the {@code notifyXxx} methods.
 *
 * <p>{@link PosTerminal} also attaches its {@link CommunicationBehavior} and
 * {@link PrinterBehavior} here once, at construction. {@link #authorize} and
 * {@link #print} are pass-throughs to whatever {@code PosTerminal} attached — a vendor's
 * {@link EmvBehavior} calls them without ever holding a reference to either port itself.
 */
public final class EmvEngine {

    private final BehaviorSubject<TransactionStepEvent> transactionSteps =
            BehaviorSubject.createDefault(TransactionStepEvent.idle());

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Nullable
    private EmvBehavior behavior;

    @Nullable
    private CommunicationBehavior communication;

    @Nullable
    private PrinterBehavior printer;

    public EmvEngine() {
        // Mirrors emvSteps' shape: notify → onNext → Observer → single dispatch point.
        // Subscribing here, first, means this runs before any external subscriber
        // (PosTerminal/MainViewModel) sees the same event.
        transactionSteps.subscribe(event -> dispatchTransactionStepMethod(event.getStep()));
    }

    public void attachBehavior(EmvBehavior behavior) {
        this.behavior = behavior;
    }

    @Nullable
    public EmvBehavior getBehavior() {
        return behavior;
    }

    /** Called once by {@link com.emvenhance.core.terminal.PosTerminal}'s constructor. */
    public void attachCommunication(CommunicationBehavior communication) {
        this.communication = communication;
    }

    /** Called once by {@link com.emvenhance.core.terminal.PosTerminal}'s constructor. */
    public void attachPrinter(PrinterBehavior printer) {
        this.printer = printer;
    }

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

    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        EmvLog.d("EmvStep: " + event);
        emvSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    public void notifyTransactionStep(TransactionStepEvent event) {
        EmvLog.d("TransactionStep: " + event);
        transactionSteps.onNext(event);
    }

    /**
     * Single dispatch point for engine-side transaction-step handling — every
     * {@link #notifyTransactionStep} eventually reaches here via the {@link #transactionSteps}
     * subscription above, exactly like {@code AbstractEmvBehavior#dispatchStepMethod} centralizes
     * per-{@link EmvStep} handling instead of scattering it across call sites.
     */
    private void dispatchTransactionStepMethod(TransactionStep step) {
        switch (step) {
            case ERROR:
            case COMPLETED:
                running.set(false);
                break;
            default:
                break;
        }
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
    }

    public void notifyCompleted() {
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
    }

    // ─── Host ports (attached by PosTerminal, called by EmvBehavior) ──────

    /**
     * Requests host authorization through the {@link CommunicationBehavior} attached by
     * {@link com.emvenhance.core.terminal.PosTerminal}. Publishes
     * {@code ONLINE_REQUIRED} / {@code ONLINE_PROCESSING} / {@code ONLINE_COMPLETED} around
     * the call, so a vendor's {@link EmvBehavior} gets the notify sequence for free instead
     * of repeating it.
     */
    public AuthResult authorize(TransactionConfig config) {
        if (communication == null) {
            throw new IllegalStateException("No CommunicationBehavior attached to EmvEngine");
        }
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        AuthResult result;
        try {
            result = communication.authorize(config).blockingGet();
        } catch (Exception e) {
            result = AuthResult.declined("96",
                    e.getMessage() != null ? e.getMessage() : "Online failed");
        }
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, result.getMessage())
                .build());
        return result;
    }

    /** Prints through the {@link PrinterBehavior} attached by {@code PosTerminal}. */
    public void print(List<String> lines) {
        if (printer == null) {
            throw new IllegalStateException("No PrinterBehavior attached to EmvEngine");
        }
        printer.print(lines).blockingAwait();
    }

    // ─── Retry (behavior → PosTerminal's transaction loop) ────────────────

    @Nullable
    private volatile TransactionConfig pendingRetryConfig;

    /**
     * Requests that the current transaction restart with an adjusted config — e.g. a PAX
     * {@code fallback()} forcing magstripe, or a {@code tryAnotherInterface()} forcing
     * contact. Takes effect once the current step returns control to
     * {@link com.emvenhance.core.terminal.PosTerminal}'s transaction loop: no new
     * {@link #begin()} is taken and no new thread is scheduled, so this is safe to call from
     * deep inside a vendor kernel callback — the retry runs on the same thread, right after
     * the call stack that requested it unwinds.
     */
    public void requestRetry(TransactionConfig adjustedConfig) {
        pendingRetryConfig = adjustedConfig;
    }

    /** Called once per attempt by {@code PosTerminal}'s transaction loop. */
    @Nullable
    public TransactionConfig consumePendingRetry() {
        TransactionConfig config = pendingRetryConfig;
        pendingRetryConfig = null;
        return config;
    }
}

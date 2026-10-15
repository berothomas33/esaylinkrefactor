package com.emvenhance.core.engine;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.EmvBehavior;
import com.emvenhance.core.util.ApduTrace;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The EMV side of one terminal: owns the vendor's {@link EmvBehavior}, the running flag, and the
 * {@link #transactionSteps()} / {@link #emvSteps()} observables. No vendor logic.
 *
 * <p>{@code PosTerminal} owns everything outside EMV — card search, the host, the printer — and
 * reaches the behavior only through this class ({@link #prepare}, {@link #start},
 * {@link #cancel}). It also subscribes to its own {@link #transactionSteps()} to run
 * {@code dispatchTransactionStepMethod} — the one place engine-side bookkeeping tied to a specific
 * step (e.g. clearing the running flag on ERROR/COMPLETED) lives, instead of scattering it across
 * the {@code notifyXxx} methods.
 *
 * <p>The one way back out is {@link #authorize}: a behavior goes online from inside the kernel,
 * so the engine hands that request to the {@link Authorizer} {@code PosTerminal} gave it —
 * {@code PosTerminal#authorize}, which holds the communication port. The engine never holds the
 * port itself.
 */
public final class EmvEngine {

    private final BehaviorSubject<TransactionStepEvent> transactionSteps =
            BehaviorSubject.createDefault(TransactionStepEvent.idle());

    /**
     * Every transaction step is published through here, never on {@link #transactionSteps}
     * directly: a cancel reports from the UI thread while the transaction thread is reporting
     * too, and Rx subscribers must never get two {@code onNext} calls at once. One that arrives
     * mid-delivery is queued and delivered right after, by the thread already delivering.
     *
     * <p>{@code emvSteps} deliberately is not serialized: a step method publishes the next step
     * from inside delivery, and it has to run right there, not after the current one returns.
     */
    private final Subject<TransactionStepEvent> transactionStepsSerialized =
            transactionSteps.toSerialized();

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final EmvBehavior behavior;

    private final Authorizer authorizer;

    /** How the engine goes online for its behavior — {@code PosTerminal#authorize}. */
    public interface Authorizer {
        AuthResult authorize(TransactionConfig config);
    }

    public EmvEngine(EmvBehavior behavior, Authorizer authorizer) {
        this.behavior = behavior;
        this.authorizer = authorizer;
        // Same shape as the behavior's emvSteps dispatch: notify → onNext → observer → one
        // dispatch point, here for engine-side bookkeeping.
        // Subscribing here, first, means this runs before any external subscriber
        // (PosTerminal/MainViewModel) sees the same event.
        transactionSteps.subscribe(event -> dispatchTransactionStepMethod(event.getStep()));
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

    /**
     * Claims the engine for a new transaction; {@code false} if one is already running. That one
     * is left alone — reporting the refusal as an ERROR would end it too, since ERROR clears the
     * running flag ({@link #dispatchTransactionStepMethod}).
     */
    public boolean begin() {
        if (!running.compareAndSet(false, true)) {
            EmvLog.d("begin() refused: a transaction is already running");
            return false;
        }
        retryCount.set(0);
        retryPrompt = null;
        pendingRetryConfig = null;
        apduTrace.beginTransaction();
        return true;
    }

    /**
     * This transaction's APDU trace — started fresh by {@link #begin()}, fed by this engine's
     * step notifications and by the vendor device layer (which the vendor behavior hands this
     * instance at transaction start), read by the UI through {@code PosTerminal#apduTrace()}.
     */
    public ApduTrace apduTrace() {
        return apduTrace;
    }

    /** Runs the behavior's terminal initialization; {@code false} if it failed or was cancelled. */
    public boolean prepare(TransactionConfig config) {
        return behavior.prepare(this, config);
    }

    /** Runs EMV on the card search found, from the behavior's first post-search step. */
    public void start(TransactionConfig config, CardPresence card) {
        behavior.start(this, config, card);
    }

    /** Releases the behavior's own subscriptions; the terminal is going away. */
    public void dispose() {
        behavior.dispose();
    }

    public void cancel() {
        behavior.cancel();
        if (running.get()) {
            notifyError("Transaction cancelled");
        }
    }

    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        EmvLog.d("EmvStep: " + event);
        apduTrace.kernelStep(event.toString());
        emvSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    public void notifyTransactionStep(TransactionStepEvent event) {
        EmvLog.d("TransactionStep: " + event);
        traceTransactionStep(event);
        transactionStepsSerialized.onNext(event);
    }

    /** Flags each transaction step in the APDU trace. */
    private void traceTransactionStep(TransactionStepEvent event) {
        TransactionStep step = event.getStep();
        StringBuilder sb = new StringBuilder(step.getLabel().toUpperCase(Locale.US));
        Object mode = event.get(TransactionStepEvent.KEY_MODE);
        if (mode != null) {
            sb.append(" (").append(mode).append(')');
        }
        Object error = event.get(TransactionStepEvent.KEY_ERROR);
        Object result = event.get(TransactionStepEvent.KEY_RESULT);
        String message = event.getMessage();
        if (message != null && !message.equalsIgnoreCase(step.getLabel())) {
            sb.append(": ").append(message);
        } else if (error != null) {
            sb.append(": ").append(error);
        } else if (result != null) {
            sb.append(": ").append(result);
        }
        apduTrace.transactionEvent(sb.toString());
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

    /**
     * Goes online for the behavior ({@code AbstractEmvBehavior#authorizeOnline}) through the
     * {@link Authorizer} {@code PosTerminal} gave this engine — the host call and its
     * {@code ONLINE_*} steps live there.
     */
    public AuthResult authorize(TransactionConfig config) {
        return authorizer.authorize(config);
    }

    // ─── Retry (behavior → PosTerminal's transaction loop) ────────────────

    @Nullable
    private volatile TransactionConfig pendingRetryConfig;
    @Nullable
    private volatile String retryPrompt;
    private final AtomicInteger retryCount = new AtomicInteger();
    private final ApduTrace apduTrace = new ApduTrace();

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
        requestRetry(adjustedConfig, null);
    }

    /**
     * As {@link #requestRetry(TransactionConfig)}, with the prompt the card search should show
     * instead of its usual "insert, tap, or swipe" — e.g. "Card not read — tap again".
     */
    public void requestRetry(TransactionConfig adjustedConfig, @Nullable String prompt) {
        pendingRetryConfig = adjustedConfig;
        retryPrompt = prompt;
    }

    /** Called once per attempt by {@code PosTerminal}'s transaction loop. */
    @Nullable
    public TransactionConfig consumePendingRetry() {
        TransactionConfig config = pendingRetryConfig;
        pendingRetryConfig = null;
        if (config != null) {
            // Same transaction, so the trace carries on: the failed attempt stays above the new one.
            apduTrace.transactionEvent("RETRY #" + retryCount.incrementAndGet());
        }
        return config;
    }

    /** Retries taken so far in the current transaction (0 on the first attempt). */
    public int getRetryCount() {
        return retryCount.get();
    }

    /** The prompt passed with the last retry, once — {@code null} if none. */
    @Nullable
    public String consumeRetryPrompt() {
        String prompt = retryPrompt;
        retryPrompt = null;
        return prompt;
    }
}

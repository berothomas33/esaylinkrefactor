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
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * The EMV side of one terminal: owns the vendor's {@link EmvBehavior}, the running flag, and the
 * {@link #transactionSteps()} / {@link #emvSteps()} observables. No vendor logic.
 *
 * <p>{@code PosTerminal} owns everything outside EMV — card search, the host, the printer, and
 * whether the transaction was cancelled — and reaches the behavior only through this class
 * ({@link #prepare}, {@link #start}, {@link #cancel}).
 *
 * <p>The running flag is the one guard for both streams: {@link #begin()} sets it, the first
 * ERROR/COMPLETED clears it, and while it is clear nothing is published — no EMV step (so no
 * step method runs) and no transaction step (so a transaction ends exactly once).
 *
 * <p>The one way back out is {@link #authorize}: a behavior goes online from inside the kernel,
 * so the engine hands that request to the {@link Authorizer} {@code PosTerminal} gave it —
 * {@code PosTerminal#authorize}, which holds the communication port. The engine never holds the
 * port itself.
 */
public final class EmvEngine {

    /**
     * Serialized: a cancel reports from the UI thread while the transaction thread is reporting
     * too, and Rx subscribers must never get two {@code onNext} calls at once. One that arrives
     * mid-delivery is queued and delivered right after, by the thread already delivering.
     *
     * <p>{@code emvSteps} deliberately is not serialized: a step method publishes the next step
     * from inside delivery, and it has to run right there, not after the current one returns.
     */
    private final Subject<TransactionStepEvent> transactionSteps =
            PublishSubject.<TransactionStepEvent>create().toSerialized();

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final EmvBehavior behavior;

    private final Authorizer authorizer;

    private final BooleanSupplier cancelled;

    /** How the engine goes online for its behavior — {@code PosTerminal#authorize}. */
    public interface Authorizer {
        AuthResult authorize(TransactionConfig config);
    }

    /**
     * @param cancelled whether the current transaction was cancelled — owned by
     *                  {@code PosTerminal}, which cancels it; the behavior reads it through
     *                  {@link #isCancelled()}
     */
    public EmvEngine(EmvBehavior behavior, Authorizer authorizer, BooleanSupplier cancelled) {
        this.behavior = behavior;
        this.authorizer = authorizer;
        this.cancelled = cancelled;
    }

    /** This transaction's steps from now on; nothing is replayed to a new subscriber. */
    public Observable<TransactionStepEvent> transactionSteps() {
        return transactionSteps.hide();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Whether the current transaction was cancelled; its EMV winds down at the next check. */
    public boolean isCancelled() {
        return cancelled.getAsBoolean();
    }

    /**
     * Claims the engine for a new transaction; {@code false} if one is already running. That one
     * is left alone — reporting the refusal as an ERROR would end it too, since ERROR clears the
     * running flag ({@link #notifyTransactionStep}).
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

    /**
     * Ends the running transaction as cancelled, at once, from whatever thread cancels it (the
     * flag itself is {@code PosTerminal}'s). Nothing if none is running.
     */
    public void cancel() {
        notifyError("Transaction cancelled");
    }

    /**
     * Publishes {@code step} — only while a transaction is running. Once it has ended (ERROR or
     * COMPLETED, however it got there) a step from code still unwinding is dropped: it never
     * reaches the UI or the behavior's step methods. The next {@link #begin()} publishes again.
     */
    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        if (!running.get()) {
            EmvLog.d("EmvStep dropped, no transaction running: " + event);
            apduTrace.note("EMV", "step dropped, the transaction has ended: " + event);
            return;
        }
        EmvLog.d("EmvStep: " + event);
        apduTrace.kernelStep(event.toString());
        emvSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    /**
     * Publishes {@code event} — only while a transaction is running, like {@link #notifyEmvStep}.
     * The first ERROR/COMPLETED takes the running flag (compare-and-set: of two ends racing from
     * two threads, one wins) before any subscriber sees it; every step after it is dropped, so a
     * transaction ends exactly once and the next {@link #begin()} starts clean.
     */
    public void notifyTransactionStep(TransactionStepEvent event) {
        boolean published = event.getStep().isEnd()
                ? running.compareAndSet(true, false)
                : running.get();
        if (!published) {
            EmvLog.d("TransactionStep dropped, no transaction running: " + event);
            apduTrace.note("EMV", "transaction step dropped, the transaction has ended: " + event);
            return;
        }
        EmvLog.d("TransactionStep: " + event);
        traceTransactionStep(event);
        transactionSteps.onNext(event);
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

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The EMV side of one terminal: owns the vendor's {@link EmvBehavior}, the {@link #emvSteps()}
 * observable, the APDU trace and the retry request. No vendor logic.
 *
 * <p>{@code PosTerminal} owns the transaction itself — its thread, busy, cancel, its steps and
 * its end — and reaches the behavior only through this class ({@link #prepare},
 * {@link #start}). The engine reads that state through the narrow hooks it is given: whether the
 * transaction is still running ({@link #isRunning()} — no EMV step is published once it ended),
 * whether it was cancelled ({@link #isCancelled()}), and where a transaction step goes
 * ({@link #notifyTransactionStep}).
 *
 * <p>The one way back out is {@link #authorize}: a behavior goes online from inside the kernel,
 * so the engine hands that request to the {@link Authorizer} {@code PosTerminal} gave it —
 * {@code PosTerminal#authorize}, which holds the communication port. The engine never holds the
 * port itself.
 */
public final class EmvEngine {

    /**
     * Not serialized: a step method publishes the next step from inside delivery, and it has to
     * run right there, not after the current one returns.
     */
    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final EmvBehavior behavior;

    private final Authorizer authorizer;

    private final Consumer<TransactionStepEvent> transactionSteps;

    private final BooleanSupplier running;

    private final BooleanSupplier cancelled;

    /** How the engine goes online for its behavior — {@code PosTerminal#authorize}. */
    public interface Authorizer {
        AuthResult authorize(TransactionConfig config);
    }

    /**
     * The transaction's state is {@code PosTerminal}'s; these read it and hand it its steps.
     *
     * @param transactionSteps where a transaction step goes — {@code PosTerminal}, which drops it
     *                         once its transaction has ended
     * @param running          whether the current transaction is still running (not ended)
     * @param cancelled        whether it was cancelled
     */
    public EmvEngine(EmvBehavior behavior, Authorizer authorizer,
            Consumer<TransactionStepEvent> transactionSteps, BooleanSupplier running,
            BooleanSupplier cancelled) {
        this.behavior = behavior;
        this.authorizer = authorizer;
        this.transactionSteps = transactionSteps;
        this.running = running;
        this.cancelled = cancelled;
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    /** Whether the current transaction is still running: false once it has ended. */
    public boolean isRunning() {
        return running.getAsBoolean();
    }

    /** Whether the current transaction was cancelled; its EMV winds down at the next check. */
    public boolean isCancelled() {
        return cancelled.getAsBoolean();
    }

    /** A new transaction: fresh trace, no retry pending. */
    public void begin() {
        retryCount.set(0);
        retryPrompt = null;
        pendingRetryConfig = null;
        apduTrace.beginTransaction();
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
     * Publishes {@code step} — only while a transaction is running. Once it has ended (ERROR or
     * COMPLETED, however it got there) a step from code still unwinding is dropped: it never
     * reaches the UI or the behavior's step methods. The next transaction publishes again.
     */
    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        if (!isRunning()) {
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

    /** Hands {@code event} to {@code PosTerminal}, which publishes it while the transaction runs. */
    public void notifyTransactionStep(TransactionStepEvent event) {
        transactionSteps.accept(event);
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

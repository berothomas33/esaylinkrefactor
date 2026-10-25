package com.emvenhance.core.engine;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.ApduTrace;
import com.emvenhance.core.util.EmvLog;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The EMV side of one terminal: the {@link #emvSteps()} observable, the APDU trace, the retry
 * request and the way online. No vendor logic, and nothing of the transaction: it holds no
 * transaction state and publishes no transaction step. Whether a step may still run is asked of
 * the transaction before it is published here ({@code AbstractEmvBehavior#goToStep},
 * {@code PosTerminal}'s card search).
 *
 * <p>{@code PosTerminal} owns the transaction itself — its thread, busy, cancel, its steps and
 * its end — and runs the vendor's behavior for each attempt, handing it the transaction. It
 * also makes and keeps this engine, the only reference to it; the behavior reaches it through
 * that transaction ({@code PosTerminal.Transaction#engine()}).
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

    private final Authorizer authorizer;

    /** How the engine goes online for its behavior — {@code PosTerminal#authorize}. */
    public interface Authorizer {
        AuthResult authorize(TransactionConfig config);
    }

    public EmvEngine(Authorizer authorizer) {
        this.authorizer = authorizer;
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
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

    /**
     * Publishes {@code step}: every subscriber — the screen, and the behavior's step methods —
     * gets it before this returns. Its caller has already asked the transaction whether a step
     * may still run.
     */
    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        EmvLog.d("EmvStep: " + event);
        apduTrace.kernelStep(event.toString());
        emvSteps.onNext(event);
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
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

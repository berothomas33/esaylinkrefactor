package com.emvenhance.core.engine

import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.util.ApduTrace
import com.emvenhance.core.util.EmvLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * The EMV side of one terminal: the [emvSteps] flow, the APDU trace, the retry request and the
 * way online. No vendor logic, and nothing of the transaction: it holds no transaction state and
 * publishes no transaction step. Whether a step may still run is asked of the transaction before
 * it is published here (`AbstractEmvBehavior.goToStep`, `PosTerminal`'s card search).
 *
 * `PosTerminal` owns the transaction itself — its thread, busy, cancel, its steps and its end —
 * and runs the vendor's behavior for each attempt, handing it this engine and the transaction.
 *
 * [emvSteps] is for observers only (the UI, tests): `tryEmit` into a buffer, never blocking or
 * suspending the transaction thread. The behavior's step dispatch is not a collector: it is a
 * plain call ([setEmvStepHandler]) right after the step is emitted, on the publishing thread,
 * before the publish returns — a step method publishes the next step from inside itself, and a
 * kernel callback needs a step's answer on the same stack (`askStep`).
 *
 * The one way back out is [authorize]: a behavior goes online from inside the kernel, so the
 * engine hands that request to the [Authorizer] `PosTerminal` gave it — `PosTerminal#authorize`,
 * which holds the communication port. The engine never holds the port itself.
 */
class EmvEngine(private val authorizer: Authorizer) {

    /** How the engine goes online for its behavior — `PosTerminal#authorize`. Blocking. */
    fun interface Authorizer {
        fun authorize(config: TransactionConfig): AuthResult
    }

    /** The behavior's step dispatch — see [setEmvStepHandler]. */
    fun interface Listener<T> {
        fun onStep(event: T)
    }

    private val _emvSteps = MutableSharedFlow<EmvStepEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Every EMV step for observers; not replayed. */
    val emvSteps: SharedFlow<EmvStepEvent> = _emvSteps.asSharedFlow()

    /** The behavior's step dispatch; runs after the flow has the step — see [setEmvStepHandler]. */
    @Volatile
    private var emvStepHandler: Listener<EmvStepEvent>? = null

    /**
     * The behavior's step dispatch (`AbstractEmvBehavior`), called for every published step
     * right after [emvSteps] has it — so every observer sees step N before the step N method
     * publishes step N+1. `null` stops it.
     */
    fun setEmvStepHandler(handler: Listener<EmvStepEvent>?) {
        emvStepHandler = handler
    }

    /** Clears the handler, but only if it is still `handler` (not a newer one). */
    fun clearEmvStepHandler(handler: Listener<EmvStepEvent>) {
        if (emvStepHandler === handler) {
            emvStepHandler = null
        }
    }

    /** A new transaction: fresh trace, no retry pending. */
    fun begin() {
        retryCount.set(0)
        retryPrompt = null
        pendingRetryConfig = null
        apduTrace.beginTransaction()
    }

    /**
     * This transaction's APDU trace — started fresh by [begin], fed by this engine's step
     * notifications and by the vendor device layer (which the vendor behavior hands this
     * instance at transaction start), read by the UI through `PosTerminal#apduTrace()`.
     */
    fun apduTrace(): ApduTrace = apduTrace

    /**
     * Publishes `step`: every observer, then the behavior's step handler, gets it before this
     * returns. Its caller has already asked the transaction whether a step may still run.
     *
     * Not serialized, on purpose: a step method publishes the next step from inside the
     * handler, and it has to run right there, before the current one returns.
     */
    @JvmOverloads
    fun notifyEmvStep(step: EmvStep, detail: String? = null) {
        val event = EmvStepEvent(step, detail)
        EmvLog.d("EmvStep: $event")
        apduTrace.kernelStep(event.toString())
        _emvSteps.tryEmit(event)
        emvStepHandler?.onStep(event)
    }

    /**
     * Goes online for the behavior (`AbstractEmvBehavior#authorizeOnline`) through the
     * [Authorizer] `PosTerminal` gave this engine — the host call and its `ONLINE_*` steps live
     * there. Blocking, on the transaction thread.
     */
    fun authorize(config: TransactionConfig): AuthResult = authorizer.authorize(config)

    // ─── Retry (behavior → PosTerminal's transaction loop) ────────────────

    @Volatile
    private var pendingRetryConfig: TransactionConfig? = null

    @Volatile
    private var retryPrompt: String? = null
    private val retryCount = AtomicInteger()
    private val apduTrace = ApduTrace()

    /**
     * Requests that the current transaction restart with an adjusted config — e.g. a PAX
     * `fallback()` forcing magstripe, or a `tryAnotherInterface()` forcing contact, with the
     * prompt the card search should show instead of its usual "insert, tap, or swipe" — e.g.
     * "Card not read — tap again". Takes effect once the current step returns control to
     * `PosTerminal`'s transaction loop: no new [begin] is taken and no new thread is scheduled,
     * so this is safe to call from deep inside a vendor kernel callback — the retry runs on the
     * same thread, right after the call stack that requested it unwinds.
     */
    @JvmOverloads
    fun requestRetry(adjustedConfig: TransactionConfig, prompt: String? = null) {
        pendingRetryConfig = adjustedConfig
        retryPrompt = prompt
    }

    /** Called once per attempt by `PosTerminal`'s transaction loop. */
    fun consumePendingRetry(): TransactionConfig? {
        val config = pendingRetryConfig
        pendingRetryConfig = null
        if (config != null) {
            // Same transaction, so the trace carries on: the failed attempt stays above the new one.
            apduTrace.transactionEvent("RETRY #" + retryCount.incrementAndGet())
            // ...but the card session starts over: the next GENERATE AC is a 1st one again.
            apduTrace.beginAttempt()
        }
        return config
    }

    /** Retries taken so far in the current transaction (0 on the first attempt). */
    fun getRetryCount(): Int = retryCount.get()

    /** The prompt passed with the last retry, once — `null` if none. */
    fun consumeRetryPrompt(): String? {
        val prompt = retryPrompt
        retryPrompt = null
        return prompt
    }
}

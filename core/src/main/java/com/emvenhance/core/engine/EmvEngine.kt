package com.emvenhance.core.engine

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.EmvBehavior
import com.emvenhance.core.util.ApduTrace
import com.emvenhance.core.util.EmvLog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

/**
 * The EMV side of one terminal: owns the vendor's [EmvBehavior], the [emvSteps] flow, the APDU
 * trace and the retry request. No vendor logic, no transaction state.
 *
 * `PosTerminal` owns the transaction itself — its thread, busy, cancel, its steps and its end —
 * and reaches the behavior only through this class ([prepare], [start]). The engine reads that
 * state through the narrow hooks it is given: whether the transaction is still running
 * ([isRunning] — no EMV step is published once it ended), whether it was cancelled
 * ([isCancelled]), and where a transaction step goes ([notifyTransactionStep]).
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
 *
 * @param transactionSteps where a transaction step goes — `PosTerminal`, which drops it once its
 *      transaction has ended
 * @param running whether the current transaction is still running (not ended)
 * @param cancelled whether it was cancelled
 */
class EmvEngine(
    private val behavior: EmvBehavior,
    private val authorizer: Authorizer,
    private val transactionSteps: (TransactionStepEvent) -> Unit,
    private val running: () -> Boolean,
    private val cancelled: () -> Boolean,
) {

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

    /** Whether the current transaction is still running: false once it has ended. */
    fun isRunning(): Boolean = running()

    /** Whether the current transaction was cancelled; its EMV winds down at the next check. */
    fun isCancelled(): Boolean = cancelled()

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

    /** Runs the behavior's terminal initialization; `false` if it failed or was cancelled. */
    fun prepare(config: TransactionConfig): Boolean = behavior.prepare(this, config)

    /** Runs EMV on the card search found, from the behavior's first post-search step. */
    fun start(config: TransactionConfig, card: CardPresence) {
        behavior.start(this, config, card)
    }

    /** Releases the behavior's own step handler; the terminal is going away. */
    fun dispose() {
        behavior.dispose()
    }

    /**
     * Publishes `step` — only while a transaction is running. Once it has ended (ERROR or
     * COMPLETED, however it got there) a step from code still unwinding is dropped: it never
     * reaches the UI or the behavior's step methods. The next transaction publishes again.
     *
     * Not serialized, on purpose: a step method publishes the next step from inside the
     * handler, and it has to run right there, before the current one returns.
     */
    @JvmOverloads
    fun notifyEmvStep(step: EmvStep, detail: String? = null) {
        val event = EmvStepEvent(step, detail)
        if (!isRunning()) {
            EmvLog.d("EmvStep dropped, no transaction running: $event")
            apduTrace.note("EMV", "step dropped, the transaction has ended: $event")
            return
        }
        EmvLog.d("EmvStep: $event")
        apduTrace.kernelStep(event.toString())
        _emvSteps.tryEmit(event)
        emvStepHandler?.onStep(event)
    }

    /** Hands `event` to `PosTerminal`, which publishes it while the transaction runs. */
    fun notifyTransactionStep(event: TransactionStepEvent) {
        transactionSteps(event)
    }

    fun notifyApproved(result: String) {
        notifyTransactionStep(
            TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, result)
                .build(),
        )
    }

    fun notifyDeclined(reason: String) {
        notifyTransactionStep(
            TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, reason)
                .build(),
        )
    }

    fun notifyError(error: String) {
        notifyTransactionStep(
            TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build(),
        )
    }

    fun notifyCompleted() {
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED))
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

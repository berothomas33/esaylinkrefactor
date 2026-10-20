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
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The EMV side of one terminal: owns the vendor's [EmvBehavior], the running flag, and the
 * [transactionSteps] / [emvSteps] flows. No vendor logic.
 *
 * `PosTerminal` owns everything outside EMV — card search, the host, the printer, the
 * transaction's cancel flag — and reaches the behavior only through this class ([prepare],
 * [start]). The behavior reads the terminal's cancel flag through [isCancelled].
 *
 * One end guard for both kinds of step: nothing is published outside [begin]…the first
 * ERROR/COMPLETED, so a transaction ends exactly once. The bookkeeping is a direct
 * compare-and-set inside [notifyTransactionStep], before any collector sees the step — never a
 * collector of its own.
 *
 * The flows are for observers only (the UI, tests): `tryEmit` into a buffer, never blocking or
 * suspending the transaction thread. The behavior's step dispatch is not a collector: it is a
 * plain call ([setEmvStepHandler]) right after the step is emitted, on the publishing thread,
 * before the publish returns — a step method publishes the next step from inside itself, and a
 * kernel callback needs a step's answer on the same stack (`askStep`).
 *
 * The one way back out is [authorize]: a behavior goes online from inside the kernel, so the
 * engine hands that request to the [Authorizer] `PosTerminal` gave it — `PosTerminal#authorize`,
 * which holds the communication port. The engine never holds the port itself.
 *
 * @param cancelled the terminal's cancel flag for the current transaction
 *      (`PosTerminal#isSearchCancelled`)
 */
class EmvEngine(
    private val behavior: EmvBehavior,
    private val authorizer: Authorizer,
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

    /**
     * Every transaction step, from now on: nothing is replayed. Not a `StateFlow`: `APPROVED`
     * and `COMPLETED` are published back to back and a `StateFlow` collector could see only the
     * second, so the result panel would never show. A slow collector loses the oldest buffered
     * step, never the latest; `tryEmit` never waits.
     */
    private val _transactionSteps = MutableSharedFlow<TransactionStepEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _emvSteps = MutableSharedFlow<EmvStepEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val transactionSteps: SharedFlow<TransactionStepEvent> = _transactionSteps.asSharedFlow()

    /** Every EMV step for observers; not replayed. */
    val emvSteps: SharedFlow<EmvStepEvent> = _emvSteps.asSharedFlow()

    /** The behavior's step dispatch; runs after the flow has the step — see [setEmvStepHandler]. */
    @Volatile
    private var emvStepHandler: Listener<EmvStepEvent>? = null

    private val running = AtomicBoolean(false)

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

    fun isRunning(): Boolean = running.get()

    /** Whether the terminal cancelled the current transaction — its one cancel flag. */
    fun isCancelled(): Boolean = cancelled()

    /**
     * Claims the engine for a new transaction; `false` if one is already running. That one is
     * left alone — reporting the refusal as an ERROR would end it too.
     */
    fun begin(): Boolean {
        if (!running.compareAndSet(false, true)) {
            EmvLog.d("begin() refused: a transaction is already running")
            return false
        }
        retryCount.set(0)
        retryPrompt = null
        pendingRetryConfig = null
        apduTrace.beginTransaction()
        return true
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

    /** Ends the current transaction as cancelled — dropped if it has already ended. */
    fun cancel() {
        notifyError("Transaction cancelled")
    }

    /**
     * Publishes `step` — only while a transaction is running. Once it has ended (ERROR or
     * COMPLETED, however it got there) a step from code still unwinding is dropped: it never
     * reaches the UI or the behavior's step methods. The next [begin] publishes again.
     *
     * Not serialized, on purpose: a step method publishes the next step from inside the
     * handler, and it has to run right there, before the current one returns.
     */
    @JvmOverloads
    fun notifyEmvStep(step: EmvStep, detail: String? = null) {
        val event = EmvStepEvent(step, detail)
        if (!running.get()) {
            EmvLog.d("EmvStep dropped, no transaction running: $event")
            apduTrace.note("EMV", "step dropped, the transaction has ended: $event")
            return
        }
        EmvLog.d("EmvStep: $event")
        apduTrace.kernelStep(event.toString())
        _emvSteps.tryEmit(event)
        emvStepHandler?.onStep(event)
    }

    /**
     * Publishes `event` — only while a transaction is running, and the first ERROR/COMPLETED
     * wins `running` (compare-and-set), so a transaction ends exactly once: no second
     * "Transaction cancelled" when a stage notices the cancel, no APPROVED after a cancel's
     * ERROR. Called from the transaction thread and, for a cancel's ERROR, from the UI thread;
     * `tryEmit` is thread-safe and never waits.
     */
    fun notifyTransactionStep(event: TransactionStepEvent) {
        val published = if (event.step.isEnd) running.compareAndSet(true, false) else running.get()
        if (!published) {
            EmvLog.d("TransactionStep dropped, no transaction running: $event")
            apduTrace.note("EMV", "transaction step dropped, the transaction has ended: $event")
            return
        }
        EmvLog.d("TransactionStep: $event")
        traceTransactionStep(event)
        _transactionSteps.tryEmit(event)
    }

    /** Flags each transaction step in the APDU trace. */
    private fun traceTransactionStep(event: TransactionStepEvent) {
        val step = event.step
        val sb = StringBuilder(step.label.uppercase(Locale.US))
        val mode = event.get<Any>(TransactionStepEvent.KEY_MODE)
        if (mode != null) {
            sb.append(" (").append(mode).append(')')
        }
        val error = event.get<Any>(TransactionStepEvent.KEY_ERROR)
        val result = event.get<Any>(TransactionStepEvent.KEY_RESULT)
        val message = event.message
        if (message != null && !message.equals(step.label, ignoreCase = true)) {
            sb.append(": ").append(message)
        } else if (error != null) {
            sb.append(": ").append(error)
        } else if (result != null) {
            sb.append(": ").append(result)
        }
        apduTrace.transactionEvent(sb.toString())
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

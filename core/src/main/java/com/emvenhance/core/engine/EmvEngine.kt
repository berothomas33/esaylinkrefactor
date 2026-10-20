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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * The EMV side of one terminal: owns the vendor's [EmvBehavior], the running flag, and the
 * [transactionSteps] / [emvSteps] flows. No vendor logic.
 *
 * `PosTerminal` owns everything outside EMV — card search, the host, the printer — and reaches
 * the behavior only through this class ([prepare], [start], [cancel]).
 *
 * Two ways to see a step, on purpose:
 * - **Listeners** ([addTransactionStepListener], [addEmvStepListener], [setEmvStepHandler]) are
 *   plain synchronous calls on the publishing thread, before the publish returns. Everything the
 *   transaction itself depends on goes through them: the engine's own end bookkeeping
 *   ([dispatchTransactionStepMethod]), `PosTerminal`'s per-transaction stream, and the
 *   behavior's step dispatch (a step method publishes the next step from inside itself, and a
 *   kernel callback needs a step's answer on the same call stack — `askStep`). A
 *   `SharedFlow`/`Channel` can't do that: its collectors run later, on their own dispatcher.
 * - **Flows** ([transactionSteps], [emvSteps]) are for observers only (the UI): `tryEmit` into
 *   a buffer, never blocking or suspending the transaction thread.
 *
 * The one way back out is [authorize]: a behavior goes online from inside the kernel, so the
 * engine hands that request to the [Authorizer] `PosTerminal` gave it — `PosTerminal#authorize`,
 * which holds the communication port. The engine never holds the port itself.
 */
class EmvEngine(
    private val behavior: EmvBehavior,
    private val authorizer: Authorizer,
) {

    /** How the engine goes online for its behavior — `PosTerminal#authorize`. Blocking. */
    fun interface Authorizer {
        fun authorize(config: TransactionConfig): AuthResult
    }

    /** A synchronous step listener — see the class comment. */
    fun interface Listener<T> {
        fun onStep(event: T)
    }

    /**
     * Every transaction's steps for observers, replaying the latest one to a new collector (as
     * `BehaviorSubject` did). Not a `StateFlow`: `APPROVED` and `COMPLETED` are published back to
     * back and a `StateFlow` collector could see only the second.
     */
    private val _transactionSteps = MutableSharedFlow<TransactionStepEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    ).apply { tryEmit(TransactionStepEvent.idle()) }

    private val _emvSteps = MutableSharedFlow<EmvStepEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val transactionSteps: SharedFlow<TransactionStepEvent> = _transactionSteps.asSharedFlow()

    /** Every EMV step for observers; not replayed. */
    val emvSteps: SharedFlow<EmvStepEvent> = _emvSteps.asSharedFlow()

    private val transactionStepListeners = CopyOnWriteArrayList<Listener<TransactionStepEvent>>()
    private val emvStepListeners = CopyOnWriteArrayList<Listener<EmvStepEvent>>()

    /** The behavior's step dispatch; runs after every listener — see [setEmvStepHandler]. */
    @Volatile
    private var emvStepHandler: Listener<EmvStepEvent>? = null

    /**
     * Transaction steps waiting to be delivered — see [notifyTransactionStep]. A cancel reports
     * from the UI thread while the transaction thread is reporting too, and listeners must never
     * get two steps at once.
     */
    private val pendingTransactionSteps = ConcurrentLinkedQueue<TransactionStepEvent>()
    private val deliveringTransactionSteps = AtomicInteger()

    @Volatile
    private var currentState: TransactionStepEvent = TransactionStepEvent.idle()

    private val running = AtomicBoolean(false)

    /**
     * Registers a synchronous transaction-step listener: every step from now on, in order, on
     * the thread delivering it. Nothing is replayed. Close the returned handle to stop it.
     */
    fun addTransactionStepListener(listener: Listener<TransactionStepEvent>): AutoCloseable {
        transactionStepListeners.add(listener)
        return AutoCloseable { transactionStepListeners.remove(listener) }
    }

    /**
     * Registers a synchronous EMV-step observer: every step from now on, on the publishing
     * thread, before the behavior's step method runs. Close the returned handle to stop it.
     */
    fun addEmvStepListener(listener: Listener<EmvStepEvent>): AutoCloseable {
        emvStepListeners.add(listener)
        return AutoCloseable { emvStepListeners.remove(listener) }
    }

    /**
     * The behavior's step dispatch (`AbstractEmvBehavior`), called for every published step
     * after the flows and listeners have it — so every observer sees step N before the step N
     * method publishes step N+1. `null` stops it.
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

    fun currentTransactionState(): TransactionStepEvent = currentState

    fun isRunning(): Boolean = running.get()

    /**
     * Claims the engine for a new transaction; `false` if one is already running. That one is
     * left alone — reporting the refusal as an ERROR would end it too, since ERROR clears the
     * running flag ([dispatchTransactionStepMethod]).
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

    fun cancel() {
        behavior.cancel()
        if (running.get()) {
            notifyError("Transaction cancelled")
        }
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
        for (listener in emvStepListeners) {
            listener.onStep(event)
        }
        emvStepHandler?.onStep(event)
    }

    /**
     * Every transaction step is published through here. Steps reported at the same time from
     * two threads (a cancel from the UI thread, the transaction thread still reporting) are
     * delivered one at a time: one that arrives mid-delivery is queued and delivered right after,
     * by the thread already delivering — the caller never waits. A listener publishing a step
     * from inside its own delivery is queued the same way.
     */
    fun notifyTransactionStep(event: TransactionStepEvent) {
        EmvLog.d("TransactionStep: $event")
        traceTransactionStep(event)
        pendingTransactionSteps.add(event)
        if (deliveringTransactionSteps.getAndIncrement() != 0) {
            return
        }
        var missed = 1
        while (true) {
            while (true) {
                val next = pendingTransactionSteps.poll() ?: break
                deliverTransactionStep(next)
            }
            missed = deliveringTransactionSteps.addAndGet(-missed)
            if (missed == 0) {
                return
            }
        }
    }

    private fun deliverTransactionStep(event: TransactionStepEvent) {
        currentState = event
        // First, before any listener or observer sees the same step.
        dispatchTransactionStepMethod(event.step)
        _transactionSteps.tryEmit(event)
        for (listener in transactionStepListeners) {
            try {
                listener.onStep(event)
            } catch (e: Exception) {
                // One broken listener must not stop the others, nor the transaction.
                e.printStackTrace()
            }
        }
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

    /**
     * Single dispatch point for engine-side transaction-step handling — every
     * [notifyTransactionStep] reaches here first, exactly like
     * `AbstractEmvBehavior#dispatchStepMethod` centralizes per-[EmvStep] handling instead of
     * scattering it across call sites.
     */
    private fun dispatchTransactionStepMethod(step: TransactionStep) {
        if (step.isEnd) {
            running.set(false)
        }
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

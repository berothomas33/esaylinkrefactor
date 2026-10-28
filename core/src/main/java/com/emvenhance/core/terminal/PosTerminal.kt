package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.core.host.PrinterBehavior
import com.emvenhance.core.param.EmvParams
import com.emvenhance.core.util.ApduTrace
import com.emvenhance.core.util.EmvLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * The only public hardware API for the app.
 *
 * Owns card search, host communication, and printer. EMV after entry selection is delegated to
 * its [EmvEngine], which owns the vendor's [EmvBehavior].
 *
 * ```
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)               [returns its Transaction at once]
 *          └── engine.prepare + searchCard(config, listener)        ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── engine.start(card)                     ← vendor EMV, from inside the callback
 *                               └── behavior → engine.authorize → PosTerminal.authorize → host
 * ```
 *
 * This class owns the transaction's whole life: [startTransaction] starts it (or refuses it,
 * [TerminalBusyException]) and returns its [Transaction] — its state, its steps, its cancel and
 * its end. The end (COMPLETED, ERROR or CANCELLED) is published once, after the terminal is
 * free. The transaction thread stays inside this class.
 *
 * Threads: one dedicated transaction thread (card search, kernel, kernel callbacks, the host
 * call through `runBlocking`), never the main thread and never `Dispatchers.Default`/`IO` — a
 * blocked kernel call must not starve a shared pool. One print thread. Coroutines decide how
 * work is started, cancelled and observed; never the order or the thread of kernel calls.
 */
abstract class PosTerminal protected constructor(
    behavior: EmvBehavior,
    @JvmField protected val communication: CommunicationBehavior,
    @JvmField protected val printer: PrinterBehavior,
) {

    @JvmField
    protected val engine: EmvEngine = EmvEngine(
        behavior,
        EmvEngine.Authorizer { authorize(it) },
        ::publish,
        { current.get()?.isRunning() == true },
        { current.get()?.isCancelled() == true },
    )

    /**
     * The one thread every transaction runs on, from card search through EMV and the host: one
     * reader, one kernel. Nothing ever queues on it — a [startTransaction] is refused while the
     * previous transaction is still there — and nothing ever interrupts it: a cancel is a state
     * the transaction reads, never an interrupt of a native call. A plain executor, never a
     * coroutine: a coroutine cancelled before it starts never runs, and then nothing would free
     * the terminal; an executor's task always runs.
     */
    private val transactionExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Where receipts print: one thread (a blocking printer driver never touches a shared pool)
     * and one [Mutex] (a suspending one never interleaves two receipts) — one printer, one job at
     * a time, in the order they were asked for.
     */
    private val printScope =
        CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val printMutex = Mutex()

    /**
     * The transaction on [transactionExecutor], from its accepted start until it is done with the
     * reader; empty when the terminal is free (busy = non-empty). Only it may report steps.
     */
    private val current = AtomicReference<Transaction?>(null)

    init {
        initializeVendor()
    }

    fun communication(): CommunicationBehavior = communication

    fun printer(): PrinterBehavior = printer

    /**
     * Host authorize via the terminal-owned [CommunicationBehavior]. This is also where a
     * vendor's [EmvBehavior] goes online — [EmvEngine.authorize] hands its request here — so
     * it's one implementation, whether it's the terminal or a vendor asking.
     *
     * Publishes `ONLINE_REQUIRED` / `ONLINE_PROCESSING` / `ONLINE_COMPLETED` around the call, so
     * a vendor gets the notify sequence for free instead of repeating it.
     *
     * Blocking: called on the transaction thread from inside the kernel, which needs the answer
     * on the same stack, so the suspending host call runs in `runBlocking` here — the only
     * `runBlocking` in the app, and never on the main thread.
     */
    fun authorize(config: TransactionConfig): AuthResult {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED))
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING))
        // Cancel doesn't cut this short: once sent, the sale may be approved at the host, and
        // with no reversal yet the terminal waits for that answer — or the timeout, which
        // cancels the request.
        val result = try {
            runBlocking {
                withTimeout(onlineTimeoutMs()) {
                    communication.authorize(config)
                }
            }
        } catch (e: TimeoutCancellationException) {
            AuthResult.failed("Online failed: the host didn't answer in time")
        } catch (e: Exception) {
            // No usable host answer (network, envelope error): online failed, not a host decline.
            AuthResult.failed(e.message ?: "Online failed")
        }
        engine.notifyTransactionStep(
            TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, result.message)
                .build(),
        )
        return result
    }

    /**
     * The longest one online authorization may take, end to end — token refresh, exchange and
     * sale/refund together. Each HTTP call alone may take 75 s to connect and 75 s to read, which
     * chained came to minutes with the terminal busy throughout. Past this the request is
     * cancelled and the transaction is online failed, like any host that doesn't answer.
     */
    protected open fun onlineTimeoutMs(): Long = 90_000

    /**
     * Print receipt lines via the terminal-owned [PrinterBehavior], on the print thread, and
     * returns at once — a real printer is slow enough that printing on the calling (UI) thread
     * would ANR. A failure (no paper, DAL not ready, printer busy) is logged, not thrown — the
     * caller asked to print and walked away; it has no exception handler waiting.
     */
    fun printReceipt(lines: List<String>) {
        printScope.launch {
            printMutex.withLock {
                try {
                    printer.print(lines)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
    }

    // ─── Public API (vendor-agnostic) ────────────────────────────────────

    fun emvSteps(): SharedFlow<EmvStepEvent> = engine.emvSteps

    /**
     * The current transaction's APDU trace — owned by the engine, alongside [emvSteps]: collect [ApduTrace.changes] and read [ApduTrace.text].
     */
    fun apduTrace(): ApduTrace = engine.apduTrace()

    /**
     * Accept any presented card (chip / tap / swipe / manual as enabled by the vendor), with the
     * original sale's reference number, which a refund needs for the host — see
     * [TransactionConfig.referenceNumber]. Preferred UI entry point — see [startTransaction].
     */
    fun acceptCard(type: TransactionType, amountMinor: Long, referenceNumber: String? = null): Transaction =
        startTransaction(
            TransactionConfig(type, amountMinor, EntryMethod.ANY).withReferenceNumber(referenceNumber),
        )

    /**
     * Start with an explicit reader mode: card search, EMV and the host run on the transaction
     * thread and this returns at once. Every outcome (card found, search cancelled, timed out,
     * or errored) arrives as a [CardSearchListener] callback there, and the transaction's steps
     * on [Transaction.steps], ending with COMPLETED, ERROR or CANCELLED once the terminal is
     * free.
     *
     * Not `suspend`, and never called inside `withContext` / `flowOn`: the busy check runs here,
     * on the calling thread, so two quick taps never queue a second sale.
     *
     * @return this transaction: its [steps][Transaction.steps], and the handle to
     *      [cancel][Transaction.cancel] it — that one only, never another's
     * @throws TerminalBusyException at once, on the calling thread, nothing queued: the previous
     *      transaction hasn't finished — still searching or running EMV, or cancelled but not yet
     *      done with the reader (a PAX poll takes a moment to stop). Nothing is reported on
     *      the steps of the transaction still there.
     */
    open fun startTransaction(config: TransactionConfig): Transaction {
        val transaction = Transaction(config)
        if (!current.compareAndSet(null, transaction)) {
            EmvLog.d("POS terminal is busy: the previous transaction hasn't finished")
            throw TerminalBusyException()
        }
        transactionExecutor.execute { process(transaction) }
        return transaction
    }

    /** Cancels the transaction still on this terminal, if any; the terminal is going away. */
    open fun dispose() {
        current.get()?.cancel()
        engine.dispose()
        printScope.cancel()
    }

    /**
     * Whether a transaction is on this terminal (from its accepted start until it is done with
     * the reader) — what refuses a second start. Nothing else may rewrite what it reads (EMV
     * parameters) meanwhile.
     */
    fun isBusy(): Boolean = current.get() != null

    /** Whether the vendor search polling for the current transaction should stop: it is over. */
    fun isSearchCancelled(): Boolean = current.get()?.isOver() == true

    /**
     * Every transaction step comes through here ([EmvEngine.notifyTransactionStep] hands the
     * behavior's ones over). Published only while the current transaction is running, on that
     * transaction's [Transaction.steps]; the first end ends it ([Transaction.end]) and is
     * published by [process] once the terminal is free; anything after it is dropped.
     */
    private fun publish(event: TransactionStepEvent) {
        val transaction = current.get()
        if (transaction == null) {
            EmvLog.d("TransactionStep dropped, no transaction: $event")
            return
        }
        synchronized(transaction.steps) {
            if (!transaction.isRunning()) {
                EmvLog.d("TransactionStep dropped, the transaction has ended: $event")
                engine.apduTrace().note("EMV", "transaction step dropped, the transaction has ended: $event")
                return
            }
            if (event.step.isEnd) {
                transaction.end(event)
                return
            }
            transaction.publishNow(event)
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
        engine.apduTrace().transactionEvent(sb.toString())
    }

    /**
     * Debug switch for the [apduTrace]: whether to record every APDU command/response and step.
     * Always off in a release build (the trace holds card data).
     */
    fun setApduLoggingEnabled(enabled: Boolean) {
        engine.apduTrace().setEnabled(enabled)
    }

    fun isApduLoggingEnabled(): Boolean = engine.apduTrace().isEnabled()

    // ─── Terminal setup (a vendor overrides what it has) ─────────────────

    /** This terminal's serial number, which the host identifies it by; empty if the vendor has none. */
    open fun serialNumber(): String = ""

    /** Runtime permissions the vendor's hardware needs (a screen asks for them); none by default. */
    open fun requiredPermissions(): Array<String> = emptyArray()

    /** The countries the terminal can be set to (its currency); empty: the vendor has no setting. */
    open fun countries(): List<String> = emptyList()

    /**
     * Makes `country`, one of [countries], the terminal's country.
     *
     * @return its currency code, e.g. "EGP"
     */
    open fun setCountry(country: String): String =
        throw UnsupportedOperationException("This terminal has no country setting")

    /** The vendor's EMV parameters, for the EMV params screen; `null`: none to show. */
    open fun emvParams(): EmvParams? = null

    // ─── Vendor must implement ───────────────────────────────────────────

    /**
     * Blocking card search using the vendor's native SDK, on the transaction thread.
     * Fire [CardSearchListener] events; return the selected card or `null`.
     */
    abstract fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence?

    protected abstract fun cancelCardSearch()

    protected open fun initializeVendor() {
        // default: no-op
    }

    // ─── Orchestration (fixed for all vendors) ───────────────────────────

    /**
     * One accepted [startTransaction]: everything about it, in one place. The terminal outlives
     * every transaction, so what belongs to one lives here — a search still running for an
     * earlier transaction can then tell it is over, instead of acting on the next.
     *
     * - [end] is its whole state: `null` while it runs (and the terminal is busy with it); the
     *   first COMPLETED / ERROR, or the cancel's CANCELLED, once it has ended. One
     *   compare-and-set decides it, so a cancel racing the end, or two ends from two threads, end
     *   it exactly once.
     * - [steps] is what it reports, for a screen: its own flow, so a new transaction never shows
     *   the last one's outcome. It replays all its steps to a collector that starts late or again
     *   (a rotation): APPROVED's data is still there after COMPLETED, and nothing is missed
     *   between the start and the collect. Not a `StateFlow` (it would conflate APPROVED into
     *   COMPLETED). It never completes — the end is a step on it. It is also the lock of its
     *   reporting: every emit holds it, so a cancel's CANCELLING never lands after the end — one
     *   atomic can't order an emit across two threads. `tryEmit` never waits (the replay buffer
     *   has no limit), and a collector that throws fails its own coroutine, never the emitter or
     *   the transaction thread.
     */
    inner class Transaction internal constructor(internal val config: TransactionConfig) {

        internal val end = AtomicReference<TransactionStepEvent?>(null)

        internal val steps = MutableSharedFlow<TransactionStepEvent>(replay = Int.MAX_VALUE)

        /**
         * This transaction's steps, from its first, ending with COMPLETED, ERROR or CANCELLED once
         * the terminal is free.
         */
        fun steps(): SharedFlow<TransactionStepEvent> = steps.asSharedFlow()

        /**
         * Cancels this transaction: CANCELLING at once, then its search stops and its EMV winds
         * down, or it never starts if it hadn't yet. Never interrupts the transaction thread: the
         * terminal stays busy until that is done with the reader — a host call already sent is
         * waited for — then reports CANCELLED. Does nothing once it has ended — a finished sale,
         * or a second cancel.
         */
        fun cancel() {
            synchronized(steps) {
                if (!end.compareAndSet(null, TransactionStepEvent.of(TransactionStep.CANCELLED))) {
                    return
                }
                // Under the lock: before the end, whichever thread is first.
                publishNow(TransactionStepEvent.of(TransactionStep.CANCELLING, "Cancelling — finishing with the card"))
            }
            cancelCardSearch()
        }

        internal fun isRunning(): Boolean = end.get() == null

        internal fun isCancelled(): Boolean = end.get()?.step == TransactionStep.CANCELLED

        /** Ended, or no longer the terminal's transaction: nothing it reports reaches the UI. */
        internal fun isOver(): Boolean = !isRunning() || current.get() !== this

        /** The first end wins; a later one (a stage noticing the cancel, say) is dropped. */
        internal fun end(event: TransactionStepEvent) {
            if (!end.compareAndSet(null, event)) {
                EmvLog.d("TransactionStep dropped, the transaction has ended: $event")
            }
        }

        /** Emits `event` on [steps], holding its lock. */
        internal fun publishNow(event: TransactionStepEvent) {
            synchronized(steps) {
                EmvLog.d("TransactionStep: $event")
                traceTransactionStep(event)
                steps.tryEmit(event)
            }
        }
    }

    /**
     * Runs on [transactionExecutor] — always, even for a transaction cancelled before it got
     * here, so it always frees the terminal. Blocking, never `suspend`. One [EmvEngine.begin] for
     * the whole transaction, including any retries a behavior requests via
     * [EmvEngine.requestRetry] (PAX fallback / try-another-interface / try-again). Never throws:
     * nothing above it would catch it.
     *
     * Its `finally` is the one place a transaction's end is published: frees the terminal first,
     * then publishes the end, so a screen that sees COMPLETED/ERROR/CANCELLED can start the next
     * sale at once.
     */
    private fun process(transaction: Transaction) {
        try {
            engine.begin()
            publish(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED)) // once
            // Each attempt: prepare, search, EMV. A retry the behavior asked for (fallback, try
            // another interface, try again, See Phone) is the next turn of this loop — not a
            // search started from inside the previous search's callback.
            var config: TransactionConfig? = transaction.config
            while (config != null && transaction.isRunning()) {
                config = attempt(transaction, config)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            transaction.end(errorEvent(t.message ?: "Transaction failed"))
        } finally {
            synchronized(transaction.steps) { // a cancel's CANCELLING can't land after the end
                // A search that stopped with no outcome still ends its transaction.
                transaction.end.compareAndSet(null, errorEvent("Transaction ended without an outcome"))
                current.compareAndSet(transaction, null)
                transaction.publishNow(transaction.end.get()!!)
            }
        }
    }

    /**
     * One attempt: terminal initialization, then card search. Every [CardSearchListener]
     * implementation (Pax/Ingenico/Fake) calls exactly one listener method before it stops
     * searching, and that callback drives what happens next:
     * [EngineReportingListener.handleCardFound] runs EMV the moment a card is found.
     *
     * @return the adjusted config of a retry the behavior asked for ([EmvEngine.requestRetry]),
     *      or `null`: the transaction is done
     */
    private fun attempt(transaction: Transaction, config: TransactionConfig): TransactionConfig? {
        if (!engine.prepare(config)) {
            engine.notifyError("Terminal initialization failed") // dropped if it already ended
            return null
        }
        searchCard(config, EngineReportingListener(transaction, config))
        // Consumed either way; process's loop drops it if the transaction is over (cancelled).
        val retry = engine.consumePendingRetry()
        if (retry != null && !transaction.isRunning()) {
            EmvLog.d("Retry dropped: the transaction is over")
            engine.apduTrace().note("EMV", "retry dropped — the transaction is over")
        }
        return retry
    }

    /**
     * Maps reader events → steps for the UI, and starts EMV once a card is found. Belongs
     * to one [Transaction]: once that is over, its callbacks are dropped, so a search that
     * outlived it can't fail, or start EMV for, the next one.
     */
    private inner class EngineReportingListener(
        private val transaction: Transaction,
        private val config: TransactionConfig,
    ) : CardSearchListener {

        private fun isStale(callback: String): Boolean {
            if (transaction.isOver()) {
                EmvLog.d("Ignored $callback from a search whose transaction is over")
                return true
            }
            return false
        }

        override fun onSearchStarted(config: TransactionConfig) {
            if (isStale("onSearchStarted")) {
                return
            }
            val prompt = engine.consumeRetryPrompt()
            val msg = prompt ?: waitingMessage(config)
            val waiting = TransactionStepEvent.builder(TransactionStep.WAITING_FOR_CARD).message(msg)
            if (prompt != null) {
                waiting.put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt)
            }
            engine.notifyTransactionStep(waiting.build())
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, msg)
        }

        override fun onChipDetected(card: CardPresence) {
            handleCardFound(card)
        }

        override fun onContactlessDetected(card: CardPresence) {
            handleCardFound(card)
        }

        override fun onMagstripeDetected(card: CardPresence) {
            handleCardFound(card)
        }

        override fun onManualEntrySelected(card: CardPresence) {
            handleCardFound(card)
        }

        override fun onCardRemoved() {
            if (isStale("onCardRemoved")) {
                return
            }
            engine.notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD, "Card removed"),
            )
        }

        override fun onSearchTimeout() {
            if (isStale("onSearchTimeout")) {
                return
            }
            engine.notifyError("No card presented — card search timed out")
        }

        override fun onSearchCancelled() {
            if (isStale("onSearchCancelled")) {
                return
            }
            engine.notifyError("Card search cancelled")
        }

        override fun onReaderError(message: String?) {
            if (isStale("onReaderError")) {
                return
            }
            engine.notifyError(message ?: "Reader error")
        }

        override fun onSearchRetry(message: String) {
            if (isStale("onSearchRetry")) {
                return
            }
            engine.notifyTransactionStep(
                TransactionStepEvent.builder(TransactionStep.WAITING_FOR_CARD).message(message)
                    .put(TransactionStepEvent.KEY_RETRY_PROMPT, message).build(),
            )
            engine.notifyEmvStep(EmvStep.SEARCH_CARD, message)
        }

        /** Runs EMV right here; a retry it asks for is [attempt]'s to return. */
        private fun handleCardFound(card: CardPresence) {
            if (isStale("card detected")) {
                return
            }
            engine.notifyTransactionStep(
                TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                    .put(TransactionStepEvent.KEY_MODE, card.modeLabel)
                    .put("entryMethod", card.entryMethod.name)
                    .build(),
            )

            engine.start(config, card)
        }
    }

    private companion object {
        fun errorEvent(error: String): TransactionStepEvent =
            TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build()

        fun waitingMessage(config: TransactionConfig): String = when {
            config.mode == EntryMethod.ANY -> "Insert, tap, or swipe"
            config.isContact -> "Insert card"
            config.isMagstripe -> "Swipe card"
            config.isManual -> "Enter card number"
            else -> "Tap card"
        }
    }
}

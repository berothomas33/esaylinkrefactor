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
import com.emvenhance.core.util.ApduTrace
import com.emvenhance.core.util.EmvLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
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
 *   UI → PosTerminal.startTransaction(ANY|CHIP|…)               [suspends until the terminal is free]
 *          └── engine.prepare + searchCard(config, listener)        ← vendor SDK
 *                 └── listener.onXxxDetected(card)                 ← vendor callback
 *                        └── engine.start(card)                     ← vendor EMV, from inside the callback
 *                               └── behavior → engine.authorize → PosTerminal.authorize → host
 * ```
 *
 * A transaction is one call of the suspending [startTransaction]: calling it starts it,
 * cancelling the caller cancels it (that one only, never another's), returning means the
 * terminal is free again, [TerminalBusyException] means it was refused. Its steps are on the
 * engine's one [transactionSteps] flow. The transaction thread stays inside this class.
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
    protected val engine: EmvEngine =
        EmvEngine(behavior, EmvEngine.Authorizer { authorize(it) }) { isSearchCancelled() }

    /**
     * The one thread every transaction runs on, from card search through EMV and the host: one
     * reader, one kernel. Nothing ever queues on it — a [startTransaction] is refused while the
     * previous transaction is still there. A plain executor, never a coroutine dispatcher shared
     * with anything else; its task always runs, and a transaction cancelled before it began
     * returns at once (see [Transaction.cancel]).
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
     * reader; empty when the terminal is free. Only it may report to the engine.
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

    /** Every transaction's steps, from now on — nothing replayed. See [EmvEngine.transactionSteps]. */
    fun transactionSteps(): SharedFlow<TransactionStepEvent> = engine.transactionSteps

    fun emvSteps(): SharedFlow<EmvStepEvent> = engine.emvSteps

    /**
     * The current transaction's APDU trace — owned by the engine, alongside [transactionSteps]
     * and [emvSteps]: collect [ApduTrace.changes] and read [ApduTrace.text].
     */
    fun apduTrace(): ApduTrace = engine.apduTrace()

    /**
     * Accept any presented card (chip / tap / swipe / manual as enabled by the vendor), with the
     * original sale's reference number, which a refund needs for the host — see
     * [TransactionConfig.referenceNumber]. Preferred UI entry point — see [startTransaction].
     */
    suspend fun acceptCard(type: TransactionType, amountMinor: Long, referenceNumber: String? = null) {
        startTransaction(
            TransactionConfig(type, amountMinor, EntryMethod.ANY).withReferenceNumber(referenceNumber),
        )
    }

    /**
     * Start with an explicit reader mode — one transaction attempt per call. Card search, EMV and
     * the host run on the transaction thread, and every outcome (card found, search cancelled,
     * timed out, or errored) arrives as a [CardSearchListener] callback there; the steps are on
     * [transactionSteps].
     *
     * - Returns: the transaction is done with the reader; the terminal is free.
     * - Throws [TerminalBusyException]: refused at once, on the calling thread, before this
     *   suspends — nothing queued — while the previous transaction hasn't finished (still
     *   searching or running EMV, or cancelled but not yet done with the reader: a PAX poll takes
     *   a moment to stop). It is not reported on the steps.
     * - Cancelling the caller: cancels this transaction only — its search stops and its EMV
     *   winds down, or it never runs if it hadn't begun yet. The cancel is the transaction's own
     *   flag ([Transaction.state]); it never interrupts the transaction thread, and the terminal
     *   stays busy until that is done with the reader.
     *
     * Call it straight from the caller's coroutine (`viewModelScope`'s `Main.immediate`); never
     * wrap it in `withContext` / `flowOn` — that would move the busy check onto another thread,
     * and two quick taps would queue a second sale.
     */
    open suspend fun startTransaction(config: TransactionConfig) {
        val transaction = Transaction(config)
        // Here, on the calling thread, before any suspension: a refused start is reported at once
        // and never waits on the transaction thread to run later.
        if (!current.compareAndSet(null, transaction)) {
            EmvLog.d("POS terminal is busy: the previous transaction hasn't finished")
            throw TerminalBusyException()
        }
        suspendCancellableCoroutine { done ->
            done.invokeOnCancellation { transaction.cancel() }
            transactionExecutor.execute {
                try {
                    process(transaction)
                } finally {
                    done.resume(Unit) // ignored once the caller was cancelled
                }
            }
        }
    }

    /** Cancels the transaction still on this terminal, if any; the terminal is going away. */
    open fun dispose() {
        current.get()?.cancel()
        engine.dispose()
        printScope.cancel()
    }

    /** Whether the search calling this should stop: its transaction was cancelled. */
    fun isSearchCancelled(): Boolean = current.get()?.isOver() == true

    /**
     * Debug switch for the [apduTrace]: whether to record every APDU command/response and step.
     * Always off in a release build (the trace holds card data).
     */
    fun setApduLoggingEnabled(enabled: Boolean) {
        engine.apduTrace().setEnabled(enabled)
    }

    fun isApduLoggingEnabled(): Boolean = engine.apduTrace().isEnabled()

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
     * One accepted [startTransaction]: its config and its one cancel flag. The terminal outlives
     * every transaction, so what belongs to one transaction lives here — a search still running
     * for an earlier transaction can then tell it is over, instead of acting on the next.
     */
    private inner class Transaction(val config: TransactionConfig) {

        /** PENDING → RUNNING ([process]) | CANCELLED ([cancel]). Never reset. */
        val state = AtomicReference(State.PENDING)

        /**
         * Cancelled before it began: it never will (its [process] returns at once), so the cancel
         * frees the terminal itself. Running: its search stops and the engine's transaction ends —
         * once (a second cancel does nothing), and only while it is still the terminal's
         * transaction: once done, the reader and the engine may belong to the next one.
         */
        fun cancel() {
            if (state.compareAndSet(State.PENDING, State.CANCELLED)) {
                current.compareAndSet(this, null)
                return
            }
            if (current.get() === this && state.compareAndSet(State.RUNNING, State.CANCELLED)) {
                cancelCardSearch()
                engine.cancel()
            }
        }

        /** Cancelled, or no longer the terminal's transaction: nothing it reports reaches the engine. */
        fun isOver(): Boolean = state.get() == State.CANCELLED || current.get() !== this
    }

    private enum class State { PENDING, RUNNING, CANCELLED }

    /**
     * Runs on [transactionExecutor]; blocking, never `suspend`. One [EmvEngine.begin] guards the
     * whole transaction, including any retries a behavior requests via [EmvEngine.requestRetry]
     * (PAX fallback / try-another-interface / try-again). Never throws: nothing above it would
     * catch it. Ends the transaction it began, at the latest here, and frees the terminal when it
     * returns — done with the reader.
     */
    private fun process(transaction: Transaction) {
        if (!transaction.state.compareAndSet(State.PENDING, State.RUNNING)) {
            return // cancelled before it began; the cancel freed the terminal
        }
        var began = false
        try {
            began = engine.begin()
            if (began && !transaction.isOver()) {
                triggerSearch(transaction, transaction.config)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            if (!transaction.isOver()) {
                engine.notifyError(t.message ?: "Transaction failed")
            }
        } finally {
            if (began) {
                // The transaction this began ends here at the latest — cancelled before its search
                // started, or a search that stopped with no outcome. Dropped if it already ended.
                engine.notifyError(
                    if (transaction.isOver()) "Transaction cancelled" else "Transaction ended without an outcome",
                )
            }
            current.compareAndSet(transaction, null)
        }
    }

    /**
     * Fires card search and returns immediately — search and EMV are not sequenced by a blocking
     * return value here. Every [CardSearchListener] implementation (Pax/Ingenico/Fake) always
     * calls exactly one listener method before it stops searching, so that callback — not this
     * method's return — is what drives what happens next:
     * [EngineReportingListener.handleCardFound] starts EMV the moment a card is found, and a
     * behavior-requested retry re-enters this same method with the adjusted config — still on
     * this one thread, so it never races the attempt that requested it.
     */
    private fun triggerSearch(transaction: Transaction, config: TransactionConfig) {
        if (!engine.prepare(config)) {
            if (!transaction.isOver()) {
                engine.notifyError("Terminal initialization failed")
            }
            return
        }
        searchCard(config, EngineReportingListener(transaction, config))
    }

    /**
     * Maps reader events → engine steps for the UI, and starts EMV once a card is found. Belongs
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

        /** Runs EMV right here, then re-triggers search if the behavior asked for a retry. */
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

            // Consumed either way: a retry requested by a cancelled transaction never searches.
            val retryConfig = engine.consumePendingRetry()
            if (retryConfig != null && !transaction.isOver()) {
                triggerSearch(transaction, retryConfig)
            }
        }
    }

    private companion object {
        fun waitingMessage(config: TransactionConfig): String = when {
            config.mode == EntryMethod.ANY -> "Insert, tap, or swipe"
            config.isContact -> "Insert card"
            config.isMagstripe -> "Swipe card"
            config.isManual -> "Enter card number"
            else -> "Tap card"
        }
    }
}

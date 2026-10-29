package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.Field55
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.EmvStepEvent
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.util.EmvLog

/**
 * Vendor base: each step method does vendor work, then calls [goToStep] to publish the next
 * step.
 *
 * ```
 *   override fun onApplicationSelection(...) {
 *       // vendor work…
 *       goToStep(EmvStep.WAIT_APPLICATION_SELECTION)  // → engine → its onXxx
 *   }
 * ```
 *
 * [dispatchStepMethod] is this behavior's step handler on the engine
 * ([EmvEngine.setEmvStepHandler]): it gets every step published there — by [goToStep], from a
 * kernel callback, or by PosTerminal's card search — and runs that step's `onXxx` method, on
 * the publishing thread, before the publish returns. Nothing calls a step method directly. A
 * phase the kernel runs internally is still published with [goToStep]; its `onXxx` is then the
 * place that says so, with nothing to run.
 *
 * Everything here is plain and blocking, on the transaction thread — no `suspend`: the native
 * kernel calls back into it and needs every answer on the same stack.
 *
 * The attempt's [PosTerminal.Transaction] comes with [prepare] / [start]: the behavior reports
 * its steps there ([publish], `finish*`) and asks it whether a step may still run ([goToStep],
 * [isRunning], [isCancelled]).
 *
 * [prepare] only starts [EmvStep.TERMINAL_INITIALIZATION] (does not chain further —
 * PosTerminal searches next). [start] kicks the first post-search step; the vendor chain
 * continues via [goToStep].
 */
abstract class AbstractEmvBehavior : EmvBehavior {

    /** The terminal's engine, from [attach]; the one place it is kept. */
    private var attached: EmvEngine? = null

    /**
     * The terminal's engine ([attach]); a behavior is always attached before any step runs.
     * Private: a vendor reads [engine], and nothing but [attach] can set it.
     */
    private val engine: EmvEngine
        get() = attached ?: throw IllegalStateException("EmvBehavior used before attach()")

    @JvmField
    protected var activeConfig: TransactionConfig? = null

    @JvmField
    protected var activeCard: CardPresence? = null

    /** The attempt's transaction, from [prepare] / [start]. */
    private var transaction: PosTerminal.Transaction? = null

    /** This behavior's step handler on the engine — see [initEmvObserver]. */
    private val stepHandler = EmvEngine.Listener<EmvStepEvent> { event -> handleEmvStep(event) }

    /** The EMV engine, given once by [PosTerminal]'s constructor; it never changes. */
    fun attach(engine: EmvEngine) {
        attached = engine
    }

    /** The engine [attach] gave: the one place it is kept. */
    fun engine(): EmvEngine = engine

    override fun prepare(config: TransactionConfig, transaction: PosTerminal.Transaction): Boolean {
        this.transaction = transaction
        initEmvObserver(engine)
        this.activeConfig = config
        this.activeCard = null

        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        enterStep(EmvStep.TERMINAL_INITIALIZATION)
        return isRunning() // false once it ended: init failed, or cancelled
    }

    override fun start(config: TransactionConfig, card: CardPresence, transaction: PosTerminal.Transaction) {
        this.transaction = transaction
        // No initEmvObserver here: prepare, which always runs first in the same attempt,
        // registered the handler; nothing in the app collects emvSteps during a card search.
        this.activeConfig = config
        this.activeCard = card

        // First post-search step — vendor methods chain via goToStep from here.
        goToStep(firstStepAfterSearch(card))
    }

    override fun dispose() {
        stopObserving()
    }

    /**
     * Makes this behavior the engine's step handler, replacing any earlier registration. Called
     * at the start of every [prepare]. [start] doesn't repeat it: `PosTerminal` always runs
     * [prepare] first in the same attempt (a retry is a new attempt).
     *
     * The engine always runs the handler after the flow has the step, so every observer sees a
     * step before its method publishes the next one — the UI ends on the last step, not the
     * first. After a transaction's end [goToStep] publishes no EMV step, so a step from code
     * still unwinding never runs a step method; the next [prepare] registers again.
     */
    protected fun initEmvObserver(engine: EmvEngine) {
        engine.setEmvStepHandler(stepHandler)
    }

    private fun stopObserving() {
        attached?.clearEmvStepHandler(stepHandler)
    }

    /**
     * Vendor finished the current step → publish `step`; the engine runs that step's `onXxx`
     * method before this returns.
     *
     * The one cancel check at a step boundary: once the transaction is cancelled (or ended) no
     * step is published, so nothing runs.
     *
     * @return whether the step ran — `false` once the transaction is cancelled or ended, so a
     *      vendor holding a resource across steps (the ICC reader) knows to release it
     */
    @JvmOverloads
    protected fun goToStep(step: EmvStep?, detail: String? = null): Boolean {
        if (step == null) {
            unknownStep(null)
            return false
        }
        if (activeConfig == null) {
            return false
        }
        return enterStep(step, detail)
    }

    /**
     * For a kernel callback that waits on an answer from the cardholder (application selection,
     * PIN entry): publishes `step` like [goToStep], and returns what that step's `onXxx` method
     * gave to [answerStep] — the step method does the UI work, the callback only hands its
     * answer back to the kernel.
     *
     * Works because the step method runs before `goToStep` returns (a plain call, see
     * [EmvEngine.setEmvStepHandler]). If it doesn't answer — the transaction ended or was
     * cancelled so nothing ran, the method threw, or it simply never called [answerStep] — this
     * returns `failClosed` and notes it in the APDU trace: the kernel always gets an answer and
     * never waits forever.
     */
    protected fun askStep(step: EmvStep, detail: String?, failClosed: Int): Int {
        val outer = stepAnswer
        val answer = StepAnswer()
        stepAnswer = answer
        try {
            goToStep(step, detail)
        } finally {
            stepAnswer = outer
        }
        // No answer, or one that came after the transaction ended (a cancel while the cardholder
        // was choosing): the kernel gets the fail-closed value, never a choice nobody waits for.
        val ended = !isRunning()
        if (!answer.answered || ended) {
            val why = if (answer.answered) step.title + " answered after the transaction ended" else "no answer for " + step.title
            engine.apduTrace().note("EMV", "$why — answering $failClosed")
            return failClosed
        }
        return answer.value
    }

    /** The answer to the [askStep] running this step method. */
    protected fun answerStep(value: Int) {
        val answer = stepAnswer ?: return // Not asked: a plain goToStep has no one waiting.
        answer.value = value
        answer.answered = true
    }

    /** The answer the innermost [askStep] is waiting for; null when nobody is asking. */
    private var stepAnswer: StepAnswer? = null

    private class StepAnswer {
        var value = 0
        var answered = false
    }

    protected fun finishApproved(message: String) {
        finishApproved(
            TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, message)
                .build(),
        )
    }

    /** As [finishApproved], with an APPROVED event the vendor built (its audit data). */
    protected fun finishApproved(approved: TransactionStepEvent) {
        publish(approved)
        publish(TransactionStepEvent.of(TransactionStep.COMPLETED))
    }

    protected fun finishDeclined(message: String) {
        finishDeclined(
            TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, message)
                .build(),
        )
    }

    /** As [finishDeclined], with a DECLINED event the vendor built (its audit data). */
    protected fun finishDeclined(declined: TransactionStepEvent) {
        publish(declined)
        publish(TransactionStepEvent.of(TransactionStep.COMPLETED))
    }

    protected fun finishError(message: String) {
        publish(PosTerminal.errorEvent(message))
    }

    /**
     * Reports a step of the transaction (CARD_READ, APPLICATION_SELECTED…) on its steps; dropped
     * once it has ended.
     */
    protected fun publish(event: TransactionStepEvent) {
        val transaction = transaction ?: run {
            EmvLog.d("TransactionStep dropped, no transaction: $event")
            return
        }
        transaction.publish(event)
    }

    /** Whether the transaction is still running: false once it ended (an outcome, or a cancel). */
    protected fun isRunning(): Boolean = transaction?.isRunning() == true

    /** Whether `PosTerminal` cancelled the transaction ([PosTerminal.Transaction.cancel]). */
    protected fun isCancelled(): Boolean = transaction?.isCancelled() == true

    /**
     * The one way a vendor goes online. For chip and contactless it first builds field 55 from
     * the vendor's [readIccTag] ([buildField55]) and attaches it to [activeConfig], so no
     * request reaches the host without it; with no tag at all the request is online failed
     * instead. Then [addOnlineRequestData] lets the vendor attach anything else it read, and
     * [EmvEngine.authorize] hands it to [PosTerminal.authorize], which sends it to the host.
     *
     * Call it right after the 1st GENERATE AC returned the ARQC, when the kernel asks to go
     * online (the PAX kernel's `startOnlineProcess` callback, or `onStartOnlineProcess`).
     * Blocking: it returns once the host answered or the online timeout passed.
     */
    protected fun authorizeOnline(): AuthResult {
        var config = activeConfig ?: return AuthResult.failed("Online failed: no active transaction")
        if (isCancelled()) {
            return AuthResult.declined("17", "Cancelled")
        }
        // Built up here and stored once: an early return leaves activeConfig as it was.
        val card = activeCard
        if (card != null && !isSynchronousEntry(card)) {
            val iccData = buildField55()
            if (iccData.isEmpty()) {
                return AuthResult.failed("Online failed: no chip data (field 55) to send")
            }
            config = config.withIccData(iccData)
        }
        config = addOnlineRequestData(config)
        activeConfig = config
        return engine.authorize(config)
    }

    /**
     * Field 55 as hex BER-TLV, read tag by tag through [readIccTag]. Every tag sent (name and
     * value) and every tag missing goes to the APDU trace and the debug log. Empty when the
     * kernel had none of the tags.
     */
    protected fun buildField55(): String {
        val field55 = Field55.read(::readIccTag)
        val card = activeCard
        val source = when {
            card == null -> null
            card.isContactless -> "contactless"
            else -> "contact"
        }
        val summary = field55.describe(source)
        EmvLog.d(summary)
        engine.apduTrace().note("EMV", summary)
        return field55.hex()
    }

    /**
     * Vendor extras for the online request (PAN, PIN block, EMV result, ...), added to `config`
     * after field 55 is attached. Default: nothing to add.
     */
    protected open fun addOnlineRequestData(config: TransactionConfig): TransactionConfig = config

    /**
     * True for entry methods with no EMV kernel involved at all — magstripe and manual PAN
     * entry. Both [firstStepAfterSearch] and a vendor's own step methods (typically
     * `onReadApplicationData`, `onStartOnlineProcess`, `onTransactionCompletion`) ask this same
     * question to jump straight past the EMV-only phases.
     */
    protected open fun isSynchronousEntry(card: CardPresence): Boolean =
        card.isMagstripe || card.isManual

    /** Which step [start] enters after card search. Override per entry method. */
    protected open fun firstStepAfterSearch(card: CardPresence): EmvStep =
        if (isSynchronousEntry(card)) EmvStep.READ_APPLICATION_DATA else EmvStep.APPLICATION_SELECTION

    // ─── EMV step methods — every vendor writes these ─────────────────────

    // Each EMV phase is a method, and — with the single exception of onSearchCard — every one of
    // them is abstract: a vendor class that doesn't write all of them fails to compile. This is
    // deliberate. A silently inherited no-op is indistinguishable from "the developer forgot
    // this phase"; a compile error is not. For a phase your kernel drives internally, the kernel
    // callback still publishes it with goToStep (so the UI and the APDU trace see it), and the
    // correct body is a one-line comment saying the kernel already did it — a real, considered
    // answer, not a stub.
    //
    // They are protected, not on EmvBehavior: only dispatchStepMethod runs them, for a step
    // published on the engine. Nothing holding the engine's EmvBehavior can run one.
    //
    // `card` is null only for SEARCH_CARD (and TERMINAL_INITIALIZATION, which has none): every
    // step after the search has the card it found.

    protected abstract fun onTerminalInitialization(config: TransactionConfig)

    /**
     * The one step method that stays optional. [PosTerminal.searchCard] is the real, mandatory
     * search for every vendor; this runs when `SEARCH_CARD` is published — by PosTerminal, with
     * its prompt — and has nothing left to do. Requiring a body here would force an empty one
     * in every vendor for no benefit.
     */
    protected open fun onSearchCard(config: TransactionConfig, card: CardPresence?) {
    }

    protected abstract fun onApplicationSelection(config: TransactionConfig, card: CardPresence)

    protected abstract fun onWaitApplicationSelection(config: TransactionConfig, card: CardPresence)

    protected abstract fun onFinalApplicationSelection(config: TransactionConfig, card: CardPresence)

    protected abstract fun onReadApplicationData(config: TransactionConfig, card: CardPresence)

    protected abstract fun onSetTransactionData(config: TransactionConfig, card: CardPresence)

    protected abstract fun onOfflineDataAuthentication(config: TransactionConfig, card: CardPresence)

    protected abstract fun onProcessRestrictions(config: TransactionConfig, card: CardPresence)

    protected abstract fun onCardholderVerification(config: TransactionConfig, card: CardPresence)

    protected abstract fun onOfflinePinVerification(config: TransactionConfig, card: CardPresence)

    protected abstract fun onTerminalRiskManagement(config: TransactionConfig, card: CardPresence)

    protected abstract fun onTerminalActionAnalysis(config: TransactionConfig, card: CardPresence)

    protected abstract fun onStartOnlineProcess(config: TransactionConfig, card: CardPresence)

    protected abstract fun onIssuerAuthentication(config: TransactionConfig, card: CardPresence)

    protected abstract fun onScriptProcessing(config: TransactionConfig, card: CardPresence)

    protected abstract fun onTransactionCompletion(config: TransactionConfig, card: CardPresence)

    // ─── Field 55 — every vendor writes this ──────────────────────────────

    /**
     * One tag's value bytes, read from this vendor's own kernel in whatever way its SDK offers;
     * empty (or `null`) when the kernel has no value for it. That's all a vendor does for field
     * 55: [authorizeOnline] picks the tags ([Field55]), encodes them and logs them.
     *
     * Called right after the 1st GENERATE AC returned the ARQC, so the card's cryptogram data
     * (9F26, 9F27, 9F10, 9F36) is there to read. Never called for magstripe or manual.
     */
    protected abstract fun readIccTag(tag: Int): ByteArray?

    /**
     * Publishes `step` — only while the transaction is running. Once it has ended (an outcome,
     * or a cancel) a step from code still unwinding is dropped: it never reaches the UI or a
     * step method.
     *
     * @return whether it was published (and so ran)
     */
    private fun enterStep(step: EmvStep, detail: String? = null): Boolean {
        if (!isRunning()) {
            val event = EmvStepEvent(step, detail)
            val why = if (isCancelled()) "cancelled" else "the transaction has ended"
            EmvLog.d("EmvStep dropped, $why: $event")
            engine.apduTrace().note("EMV", "step dropped, $why: $event")
            return false
        }
        // Flows → UI, then the step handler → dispatchStepMethod → onXxx.
        engine.notifyEmvStep(step, detail)
        return true
    }

    /**
     * The step handler: hands every published step to [dispatchStepMethod]. A step method that
     * throws ends the transaction with an ERROR.
     */
    private fun handleEmvStep(event: EmvStepEvent) {
        val step = event.step
        try {
            dispatchStepMethod(step)
        } catch (e: Exception) {
            finishError(e.message ?: step.title)
        }
    }

    /** Runs the `onXxx` method of every step published on the engine. */
    private fun dispatchStepMethod(step: EmvStep) {
        val config = activeConfig ?: return
        if (step == EmvStep.TERMINAL_INITIALIZATION) {
            onTerminalInitialization(config)
            return
        }
        if (step == EmvStep.SEARCH_CARD) {
            onSearchCard(config, activeCard)
            return
        }
        // Every later step runs on the card the search found.
        val card = activeCard ?: run {
            unknownStep(step)
            return
        }
        when (step) {
            EmvStep.TERMINAL_INITIALIZATION, EmvStep.SEARCH_CARD -> Unit // handled above
            EmvStep.APPLICATION_SELECTION -> onApplicationSelection(config, card)
            EmvStep.WAIT_APPLICATION_SELECTION -> onWaitApplicationSelection(config, card)
            EmvStep.FINAL_APPLICATION_SELECTION -> onFinalApplicationSelection(config, card)
            EmvStep.READ_APPLICATION_DATA -> onReadApplicationData(config, card)
            EmvStep.SET_TRANSACTION_DATA -> onSetTransactionData(config, card)
            EmvStep.OFFLINE_DATA_AUTHENTICATION -> onOfflineDataAuthentication(config, card)
            EmvStep.PROCESS_RESTRICTIONS -> onProcessRestrictions(config, card)
            EmvStep.CARDHOLDER_VERIFICATION -> onCardholderVerification(config, card)
            EmvStep.OFFLINE_PIN_VERIFICATION -> onOfflinePinVerification(config, card)
            EmvStep.TERMINAL_RISK_MANAGEMENT -> onTerminalRiskManagement(config, card)
            EmvStep.TERMINAL_ACTION_ANALYSIS -> onTerminalActionAnalysis(config, card)
            EmvStep.START_ONLINE_PROCESS -> onStartOnlineProcess(config, card)
            EmvStep.ISSUER_AUTHENTICATION -> onIssuerAuthentication(config, card)
            EmvStep.SCRIPT_PROCESSING -> onScriptProcessing(config, card)
            EmvStep.TRANSACTION_COMPLETION -> onTransactionCompletion(config, card)
        }
    }

    /**
     * A step with no method to run ends the transaction: after its ERROR no more steps are
     * published ([goToStep]).
     */
    private fun unknownStep(step: EmvStep?) {
        if (!isRunning()) {
            return // already ended: no second ERROR
        }
        finishError("Unknown EMV step: $step")
    }
}

package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.Field55
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.EmvStepEvent
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
 * [prepare] only starts [EmvStep.TERMINAL_INITIALIZATION] (does not chain further —
 * PosTerminal searches next). [start] kicks the first post-search step; the vendor chain
 * continues via [goToStep].
 */
abstract class AbstractEmvBehavior : EmvBehavior {

    @JvmField
    protected var engine: EmvEngine? = null

    @JvmField
    protected var activeConfig: TransactionConfig? = null

    @JvmField
    protected var activeCard: CardPresence? = null

    /** This behavior's step handler on the engine — see [initEmvObserver]. */
    private val stepHandler = EmvEngine.Listener<EmvStepEvent> { event -> handleEmvStep(event) }

    override fun prepare(engine: EmvEngine, config: TransactionConfig): Boolean {
        this.engine = engine
        initEmvObserver(engine)
        this.activeConfig = config
        this.activeCard = null

        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        enterStep(EmvStep.TERMINAL_INITIALIZATION)
        return engine.isRunning() // false once it ended: init failed, or cancelled
    }

    override fun start(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        this.engine = engine
        initEmvObserver(engine)
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
     * at the start of every [prepare] and [start].
     *
     * The engine always runs the handler after the flow has the step, so every observer sees a
     * step before its method publishes the next one — the UI ends on the last step, not the
     * first. After a transaction's end the engine publishes no EMV step, so a step from code
     * still unwinding never runs a step method; the next [prepare] registers again.
     */
    protected fun initEmvObserver(engine: EmvEngine) {
        engine.setEmvStepHandler(stepHandler)
    }

    private fun stopObserving() {
        engine?.clearEmvStepHandler(stepHandler)
    }

    /**
     * Vendor finished the current step → publish `step`; the engine runs that step's `onXxx`
     * method before this returns.
     *
     * No cancel check of its own: once the transaction is cancelled (or ended) the engine
     * publishes no step ([EmvEngine.notifyEmvStep]), so nothing runs.
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
        if (engine == null || activeConfig == null) {
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
        if (!answer.answered) {
            engine?.apduTrace()?.note("EMV", "no answer for " + step.title + " — answering " + failClosed)
            return failClosed
        }
        val engine = engine
        if (engine != null && !engine.isRunning()) {
            // Answered, but the transaction ended (cancelled) while the cardholder was choosing:
            // the kernel gets the fail-closed value, not a choice nobody is waiting for.
            engine.apduTrace().note("EMV", step.title + " answered after the transaction ended — answering " + failClosed)
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
        val engine = engine ?: return
        engine.notifyApproved(message)
        engine.notifyCompleted()
    }

    protected fun finishDeclined(message: String) {
        val engine = engine ?: return
        engine.notifyDeclined(message)
        engine.notifyCompleted()
    }

    protected fun finishError(message: String) {
        engine?.notifyError(message)
    }

    /** Whether `PosTerminal` cancelled the current transaction (see [EmvEngine.isCancelled]). */
    protected fun isCancelled(): Boolean = engine?.isCancelled() == true

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
        val engine = engine
        var config = activeConfig
        if (engine == null || config == null) {
            return AuthResult.failed("Online failed: no active transaction")
        }
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
        engine?.apduTrace()?.note("EMV", summary)
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

    protected abstract fun onTerminalInitialization(engine: EmvEngine, config: TransactionConfig)

    /**
     * The one step method that stays optional. [PosTerminal.searchCard] is the real, mandatory
     * search for every vendor; this runs when `SEARCH_CARD` is published — by PosTerminal, with
     * its prompt — and has nothing left to do. Requiring a body here would force an empty one
     * in every vendor for no benefit.
     */
    protected open fun onSearchCard(engine: EmvEngine, config: TransactionConfig, card: CardPresence?) {
    }

    protected abstract fun onApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onWaitApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onFinalApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onReadApplicationData(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onSetTransactionData(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onOfflineDataAuthentication(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onProcessRestrictions(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onCardholderVerification(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onOfflinePinVerification(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onTerminalRiskManagement(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onTerminalActionAnalysis(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onStartOnlineProcess(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onIssuerAuthentication(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onScriptProcessing(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

    protected abstract fun onTransactionCompletion(engine: EmvEngine, config: TransactionConfig, card: CardPresence)

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

    private fun enterStep(step: EmvStep, detail: String? = null): Boolean {
        // Flows/listeners → UI, then the step handler → dispatchStepMethod → onXxx.
        return engine?.notifyEmvStep(step, detail) == true
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
        val engine = engine ?: return
        val config = activeConfig ?: return
        if (step == EmvStep.TERMINAL_INITIALIZATION) {
            onTerminalInitialization(engine, config)
            return
        }
        if (step == EmvStep.SEARCH_CARD) {
            onSearchCard(engine, config, activeCard)
            return
        }
        // Every later step runs on the card the search found.
        val card = activeCard ?: run {
            unknownStep(step)
            return
        }
        when (step) {
            EmvStep.TERMINAL_INITIALIZATION, EmvStep.SEARCH_CARD -> Unit // handled above
            EmvStep.APPLICATION_SELECTION -> onApplicationSelection(engine, config, card)
            EmvStep.WAIT_APPLICATION_SELECTION -> onWaitApplicationSelection(engine, config, card)
            EmvStep.FINAL_APPLICATION_SELECTION -> onFinalApplicationSelection(engine, config, card)
            EmvStep.READ_APPLICATION_DATA -> onReadApplicationData(engine, config, card)
            EmvStep.SET_TRANSACTION_DATA -> onSetTransactionData(engine, config, card)
            EmvStep.OFFLINE_DATA_AUTHENTICATION -> onOfflineDataAuthentication(engine, config, card)
            EmvStep.PROCESS_RESTRICTIONS -> onProcessRestrictions(engine, config, card)
            EmvStep.CARDHOLDER_VERIFICATION -> onCardholderVerification(engine, config, card)
            EmvStep.OFFLINE_PIN_VERIFICATION -> onOfflinePinVerification(engine, config, card)
            EmvStep.TERMINAL_RISK_MANAGEMENT -> onTerminalRiskManagement(engine, config, card)
            EmvStep.TERMINAL_ACTION_ANALYSIS -> onTerminalActionAnalysis(engine, config, card)
            EmvStep.START_ONLINE_PROCESS -> onStartOnlineProcess(engine, config, card)
            EmvStep.ISSUER_AUTHENTICATION -> onIssuerAuthentication(engine, config, card)
            EmvStep.SCRIPT_PROCESSING -> onScriptProcessing(engine, config, card)
            EmvStep.TRANSACTION_COMPLETION -> onTransactionCompletion(engine, config, card)
        }
    }

    /**
     * A step with no method to run ends the transaction: its ERROR stops the engine publishing
     * steps ([EmvEngine.notifyEmvStep]) and removes this behavior's step handler
     * ([initEmvObserver]).
     */
    private fun unknownStep(step: EmvStep?) {
        val engine = engine
        if (engine == null || !engine.isRunning()) {
            return // already ended: no second ERROR
        }
        finishError("Unknown EMV step: $step")
    }
}

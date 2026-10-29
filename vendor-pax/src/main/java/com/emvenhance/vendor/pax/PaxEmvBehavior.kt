package com.emvenhance.vendor.pax

import android.os.SystemClock
import android.util.Log
import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.EmvTransactionResult
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.AbstractAppSelectDialog
import com.emvenhance.core.terminal.AbstractEmvBehavior
import com.emvenhance.core.terminal.PosTerminal
import com.emvenhance.emvflow.device.EmvDeviceImpl
import com.emvenhance.emvflow.runtime.EmvFlowRuntime
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor
import com.emvenhance.network.onboarding.OnboardingState
import com.emvenhance.vendor.pax.PaxTransactionState.Outcome
import com.emvenhance.vendor.pax.PaxTransactionState.PinRequest
import com.pax.configservice.impl.EmvParamService
import com.emvenhance.settings.PinBypassSettings
import com.pax.bizentity.entity.SearchMode
import com.pax.bizlib.card.TrackUtils
import com.pax.bizlib.ped.PedHelper
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.currency.CurrencyConverter
import com.pax.commonlib.utils.ConvertUtils
import com.pax.commonlib.utils.LogUtils
import com.pax.dal.IPed
import com.pax.dal.entity.EAesCheckMode
import com.pax.dal.entity.EPedKeyType
import com.pax.dal.entity.EPiccType
import com.pax.dal.exceptions.EPedDevException
import com.pax.dal.exceptions.PedDevException
import com.pax.emvbase.constant.EmvConstant
import com.pax.emvbase.constant.TagsTable
import com.pax.emvbase.param.EmvProcessParam
import com.pax.emvbase.param.EmvTransParam
import com.pax.emvbase.process.contact.CandidateAID
import com.pax.emvbase.process.contact.IContactCallback
import com.pax.emvbase.process.contactless.IContactlessCallback
import com.pax.emvbase.process.entity.EOnlineResult
import com.pax.emvbase.process.entity.IssuerRspData
import com.pax.emvbase.process.entity.OnlineResultWrapper
import com.pax.emvbase.process.entity.TransResult
import com.pax.emvbase.process.enums.CvmResultEnum
import com.pax.emvbase.process.enums.TransResultEnum
import com.pax.emvlib.dpas.contact.ContactProcess
import com.pax.emvlib.process.contactless.ClssProcess
import com.pax.emvservice.emv.pin.PinService
import com.pax.emvservice.export.exceptions.PinException
import com.pax.jemv.clcommon.RetCode
import com.pax.jemv.device.DeviceManager
import com.pax.poslib.gl.convert.ConvertHelper
import com.pax.poslib.utils.PosDeviceUtils
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PAX vendor EMV behavior on [AbstractEmvBehavior].
 *
 * - Mag / manual: sync [goToStep] chain (same pattern as Fake).
 * - Chip: [onApplicationSelection] drives the PAX kernel one native call at a time, through the
 *   very same [goToStep] engine that drives mag/manual — each stage's run* method
 *   ([runContactAppSelect] → [runContactReadAppData] → [runContactSetTransactionData] →
 *   [runContactCardAuth] → [runContactStartTransaction]) advances by calling
 *   `goToStep(EmvStep.NEXT)` rather than the next run* method directly, so `goToStep` both
 *   publishes that step and dispatches to its onXxx method (e.g. [onReadApplicationData]), which
 *   hands straight back to the matching run* method for chip. Chaining continues only while
 *   [isContactTransactionFinished] stays false; APPLICATION_SELECTION itself is entered the
 *   normal way, via the framework's own goToStep call that invoked [onApplicationSelection] in
 *   the first place. Sub-phases with no kernel callback and no dedicated stage of their own
 *   (terminal risk management, terminal action analysis — both folded into
 *   [runContactStartTransaction]'s EMVStartTrans call) still can't be published individually;
 *   CVM is only published when the kernel actually asks for it, via `onCardHolderPwd` below.
 *   Contactless: a granular kernel (EFT, PayPass, PayWave — see
 *   `ClssKernelProcess#supportsGranularSteps()`) runs the same way, one stage per step, in its
 *   own kernel's order; an atomic kernel runs in one call and its steps are published from kernel
 *   callbacks, their onXxx having nothing left to run.
 *
 * Everything here runs on the terminal's one transaction thread and blocks there: the native
 * kernel calls the [IContactCallback]/[IContactlessCallback] methods below on that thread and
 * needs each answer on the same stack, so none of them (and nothing they call) is `suspend`.
 * The host call behind [authorizeOnline] suspends inside `PosTerminal.authorize`'s `runBlocking`.
 *
 * **Experiment branch:** talks to [ContactProcess] (emvlib:dpas) directly — no
 * `EmvContactService` service layer. Everything that used to live there — per-transaction state
 * ([PaxTransactionState.transResult], [PaxTransactionState.cachedTrack2Data]), the online-processing
 * decision inside [startContactTransProcess], PAN/track2 derivation ([getContactPan]), and the
 * result-to-callback mapping in [checkContactResult] — now lives here instead. The contactless
 * path got the same treatment: talks to [ClssProcess] (emvlib) directly — no
 * `ContactlessService` layer — with its own mirrored state
 * ([PaxTransactionState.clsTransResult]/[PaxTransactionState.clsCachedTrack2Data]/[clsLastNeedSeePhone]),
 * online decision ([startContactlessTransProcess]), PAN derivation ([getContactlessPan]), and
 * result mapping ([checkContactlessResult]). See the structure diagram (panel 10/11) for why this
 * was tried and what it cost.
 */
class PaxEmvBehavior(private val kernel: PaxKernel) :
    AbstractEmvBehavior(), IContactCallback, IContactlessCallback {

    /**
     * See Phone on a tap: the kernel asks for it ([needSeePhone]) on the next tap, which is a
     * retry — a new attempt — so unlike [PaxTransactionState] it carries over.
     */
    private var clsLastNeedSeePhone = false

    /** What this behavior learns during one attempt — see [PaxTransactionState]. */
    @Volatile
    private var tx = PaxTransactionState()

    private val pinBypass = PinBypassSettings(BaseApplication.getAppContext())

    // ─── Lifecycle ───────────────────────────────────────────────────────

    override fun prepare(config: TransactionConfig, transaction: PosTerminal.Transaction): Boolean {
        // A fresh state per attempt: nothing from an earlier sale carries over — its online PIN
        // block (and RSA-wrapped key) sent again with this sale, flagged as online PIN; or its
        // kernel result read as this one's.
        tx = PaxTransactionState()
        super.prepare(config, transaction)
        return tx.initOk && isRunning()
    }

    override fun onTerminalInitialization(config: TransactionConfig) {
        // The kernel calls EmvDeviceImpl directly, so hand it this transaction's APDU trace.
        EmvDeviceImpl.getInstance().setApduTrace(engine().apduTrace())
        if (!prepareKernel(config)) {
            tx.initOk = false
            finishError("Terminal initialization failed")
        }
        // Do not goToStep — PosTerminal.searchCard runs next.
    }

    // firstStepAfterSearch: no override needed — AbstractEmvBehavior's default already does
    // exactly this (isSynchronousEntry → READ_APPLICATION_DATA, else APPLICATION_SELECTION,
    // which starts the PAX kernel for chip/CLSS).

    // ─── Step methods (goToStep / kernel start) ──────────────────────────

    override fun onApplicationSelection(config: TransactionConfig, card: CardPresence) {
        if (card.isChip) {
            runContactAppSelect()
            return
        }
        if (card.isContactless) {
            runContactlessAppSelect()
            return
        }
        // Mag/manual should not land here
        goToStep(EmvStep.READ_APPLICATION_DATA)
    }

    override fun onReadApplicationData(config: TransactionConfig, card: CardPresence) {
        if (card.isManual) {
            val pan = card.manualPan ?: ""
            if (pan.isEmpty()) {
                finishError("Manual entry requires a PAN")
                return
            }
            publish(
                TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MANUAL")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, card.modeLabel)
                    .build(),
            )
            goToStep(EmvStep.START_ONLINE_PROCESS, "manual")
            return
        }
        if (card.isMagstripe) {
            val track2 = card.track2 ?: ""
            val pan = panFromTrack2(track2)
            publish(
                TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MAG")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, card.modeLabel)
                    .build(),
            )
            goToStep(EmvStep.START_ONLINE_PROCESS, "magstripe")
            return
        }
        if (card.isChip) {
            // Reached via runContactAppSelect's goToStep(READ_APPLICATION_DATA) — never via the
            // framework's own cancellation short-circuit in goToStep, which returns before
            // dispatchStepMethod runs at all.
            runContactReadAppData()
        }
        if (card.isContactless && kernel.contactless.supportsGranularSteps()) {
            // Reached via runContactlessAppSelect's goToStep(READ_APPLICATION_DATA) — only a
            // kernel whose SDK exposes this as its own call (see
            // ClssKernelProcess#supportsGranularSteps()). An atomic kernel publishes this step
            // from onReadCardOk() after reading the data itself: nothing to run then.
            runContactlessReadAppData()
        }
    }

    override fun onStartOnlineProcess(config: TransactionConfig, card: CardPresence) {
        // Mag / manual sync path
        if (isSynchronousEntry(card)) {
            tx.lastAuth = authorizeOnline()
            goToStep(EmvStep.TRANSACTION_COMPLETION)
            return
        }
        // Chip/CLSS: asked by startOnlineProcess() after the 1st GENERATE AC — the host round
        // trip runs here, in its own step.
        if (tx.onlineRequested) {
            tx.lastAuth = authorizeOnline()
        }
    }

    override fun onTransactionCompletion(config: TransactionConfig, card: CardPresence) {
        // Mag / manual sync path
        if (isSynchronousEntry(card)) {
            val auth = tx.lastAuth
            if (auth != null && auth.isApproved) {
                finishApproved(if (card.isManual) "MANUAL ONLINE APPROVED" else "MAG ONLINE APPROVED")
            } else {
                finishDeclined(auth?.message ?: "DECLINED")
            }
            return
        }
        // Chip/CLSS: the outcome checkContactResult / checkContactlessResult decided
        // (finishWithOutcome) — reported here, in its own step.
        val outcome = tx.outcome
        tx.outcome = null
        if (outcome != null) {
            reportOutcome(outcome)
        }
    }

    // Chip: reached via goToStep from the previous run* stage — dispatchStepMethod invokes
    // these, and each hands off to its run* method. See the class doc and runContactAppSelect
    // for the full chain.

    override fun onOfflineDataAuthentication(config: TransactionConfig, card: CardPresence) {
        if (card.isChip) {
            // Reached via runContactSetTransactionData's goToStep(OFFLINE_DATA_AUTHENTICATION).
            runContactCardAuth()
        }
        if (card.isContactless) {
            // Reached via runContactlessReadAppData's goToStep(OFFLINE_DATA_AUTHENTICATION), or
            // for PayWave via runContactlessProcessRestrictions' (its kernel's order).
            runContactlessCardAuth()
        }
    }

    override fun onProcessRestrictions(config: TransactionConfig, card: CardPresence) {
        if (card.isChip) {
            // Reached via runContactCardAuth's goToStep(PROCESS_RESTRICTIONS) — the entry point
            // into runContactStartTransaction's bundled EMVStartTrans call (see class doc).
            runContactStartTransaction()
        }
        if (card.isContactless) {
            // Reached via runContactlessCardAuth's goToStep(PROCESS_RESTRICTIONS) for a granular
            // kernel (via runContactlessReadAppData's for PayWave, whose restrictions come
            // before offline data auth), or directly from runContactlessAppSelect for an atomic
            // one, which goes straight to runContactlessStartTransaction's bundled call.
            runContactlessProcessRestrictions()
        }
    }

    // ─── Kernel-driven phases — required overrides, not stubs ──
    //
    // Chip/CLSS: PAX's own kernel (startContactTransProcess/startContactlessTransProcess)
    // runs these phases internally and reports them from the IContactCallback methods further
    // below, which publish each one so the UI and the APDU trace follow it.
    // dispatchStepMethod then runs the onXxx here. Where the kernel waits on the cardholder
    // (application selection, PIN), the callback asks with askStep and the onXxx here does the
    // UI work and answers; elsewhere the kernel has already done the work, so there is nothing
    // left to do.
    //
    // Mag/manual: these EMV data phases don't apply. onApplicationSelection and
    // onReadApplicationData above jump straight past them with explicit goToStep targets.
    //
    // EmvBehavior still requires an override for each — a compile error beats a vendor author
    // forgetting a phase and inheriting a silent no-op.

    override fun onWaitApplicationSelection(config: TransactionConfig, card: CardPresence) {
        // Asked by IContactCallback#onWaitAppSelect below: the kernel waits on this answer.
        answerApplicationSelection()
    }

    override fun onFinalApplicationSelection(config: TransactionConfig, card: CardPresence) {
        // Same as onWaitApplicationSelection: the kernel's retry after a failed final select.
        answerApplicationSelection()
    }

    /**
     * The cardholder's choice among the kernel's candidates ([PaxTransactionState.candidates]),
     * answered to [onWaitAppSelect]'s `askStep`: index 0 with nothing to choose between, else the
     * dialog's choice.
     */
    private fun answerApplicationSelection() {
        val candList = tx.candidates
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED))
        if (candList == null || !showsAppSelectDialog(candList)) {
            // Nothing for the cardholder to choose between — index 0 either selects the card's
            // only application, or (candList empty/null) is the safe default the kernel expects.
            answerStep(EmvConstant.ContactCallbackStatus.CONTACT_OK)
            return
        }
        val index = PaxAppSelectDialog().selectBlocking(PaxAppSelectDialog.labels(candList)) {
            !isRunning()
        }
        if (index == AbstractAppSelectDialog.NO_ANSWER) {
            // Not answered: askStep gives the kernel USER_CANCEL and notes it in the trace.
            LogUtils.i(TAG, "Application not selected (timed out, cancelled or no screen)")
            return
        }
        answerStep(index)
    }

    override fun onSetTransactionData(config: TransactionConfig, card: CardPresence) {
        if (card.isChip) {
            // Reached via runContactReadAppData's goToStep(SET_TRANSACTION_DATA): the contact
            // kernel takes the data after reading the card.
            runContactSetTransactionData()
        }
        // Granular contactless kernels (EFT, PayPass, PayWave): this stage gives the kernel the
        // transaction data, before READ_APPLICATION_DATA's GPO needs it for the PDOL.
        if (card.isContactless && kernel.contactless.supportsGranularSteps()) {
            runContactlessSetTransactionData()
        }
        // Atomic contactless kernels take the data inside their own call; the step is
        // published from IContactlessCallback#confirmCard below.
    }

    override fun onCardholderVerification(config: TransactionConfig, card: CardPresence) {
        // Asked by onCardHolderPwd below for an online PIN; for an offline PIN this step only
        // announces CVM and OFFLINE_PIN_VERIFICATION is the one asked.
        val request = tx.pinRequest
        if (request != null && request.online) {
            answerStep(enterPin(request))
        }
    }

    override fun onOfflinePinVerification(config: TransactionConfig, card: CardPresence) {
        // Asked by onCardHolderPwd below for an offline PIN: show the pad, answer CONTACT_OK —
        // the kernel then verifies the PIN with the card itself.
        val request = tx.pinRequest
        if (request != null && !request.online) {
            answerStep(enterPin(request))
        }
    }

    override fun onTerminalRiskManagement(config: TransactionConfig, card: CardPresence) {
        // Kernel-internal — no callback exists, so it is never published on its own.
    }

    override fun onTerminalActionAnalysis(config: TransactionConfig, card: CardPresence) {
        // Kernel-internal — no callback exists, so it is never published on its own.
    }

    override fun onIssuerAuthentication(config: TransactionConfig, card: CardPresence) {
        // Asked by runIssuerAuthentication with the host's answer: the card's 2nd GENERATE AC
        // (contact) or the 2nd-tap check (contactless). With no ARQC to complete, or a
        // contactless sale the host didn't approve, the card has nothing to do: no step.
        if (tx.issuerResponse != null) {
            completeIssuerAuthentication()
        }
    }

    override fun onScriptProcessing(config: TransactionConfig, card: CardPresence) {
        // Contactless 2nd tap: asked by runIssuerScriptProcessing — the kernel's script call
        // (PayWave's Clss_IssScriptProc_Wave). Contact: EMVCompleteTrans already applied them,
        // and completeApproved() announces the step with nothing to run.
        if (tx.pendingScripts != null) {
            processIssuerScripts()
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Kernel runners
    // ═══════════════════════════════════════════════════════════════════

    // ─── CONTACTLESS ONLY — ClssProcess / ClssKernelProcess runners ──────

    private fun runContactlessAppSelect() {
        var proceed = false
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance())
            LogUtils.d(TAG, "============ Contactless EMV: App Select ============")
            // reset every transaction
            tx.clsCachedTrack2Data = null
            resetTransactionSummary()
            kernel.contactless.registerClssProcessListener(this)
            val result = kernel.contactless.selectApplication()
            tx.clsTransResult = result
            val ret = result.resultCode
            LogUtils.d(TAG, "selectApplication ret=$ret")
            markTryAgainOnCommunicationError(ret)
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished()
            if (!proceed) {
                kernel.contactless.unregisterClssProcessListener()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contactless app select failed", e)
            finishError(e.message ?: "Contactless EMV app select failed")
        } finally {
            if (!proceed) {
                finishContactlessStage()
            }
        }
        if (proceed) {
            // EFT/PayPass/PayWave expose set data/read/offline-auth/restrictions as their own
            // calls; the other seven brands' SDKs bundle the whole transaction into one call
            // instead — see ClssKernelProcess#supportsGranularSteps().
            advanceContactlessStage(firstStageAfterAppSelect())
        }
    }

    /**
     * A granular stage that ends the transaction after the card was read (a decline, See Phone,
     * an error) keeps the same summary — PAN, TVR, TACs/IACs, field 55 — that
     * [runContactlessStartTransaction] records when the kernel's one call ends it.
     */
    private fun captureSummaryAfterCardRead() {
        try {
            captureTransactionSummary()
        } catch (e: Exception) {
            LogUtils.e(TAG, "transaction summary failed", e)
        }
    }

    /** A granular kernel starts with its set-data stage; an atomic one goes to its one call. */
    private fun firstStageAfterAppSelect(): EmvStep =
        if (kernel.contactless.supportsGranularSteps()) EmvStep.SET_TRANSACTION_DATA else EmvStep.PROCESS_RESTRICTIONS

    /**
     * A granular stage got `CLSS_RESELECT_APP`: delete the candidate and select the next one
     * ([ClssProcess.reselectApplication]), as `ClssProcess.startTransProcess`'s loop does for the
     * atomic kernels. Returns the selection's result code; the caller then starts the new
     * candidate from [firstStageAfterAppSelect].
     */
    private fun reselectApplication(): Int {
        LogUtils.d(TAG, "CLSS_RESELECT_APP — selecting the next candidate")
        engine().apduTrace().note("EMV", "kernel asked to reselect — next application")
        val result = kernel.contactless.reselectApplication()
        tx.clsTransResult = result
        val ret = result.resultCode
        LogUtils.d(TAG, "reselectApplication ret=$ret")
        return ret
    }

    /** Granular kernels only: kernel init and the transaction data, then READ_APPLICATION_DATA. */
    private fun runContactlessSetTransactionData() {
        var proceed = false
        var reselected = false
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Set Transaction Data ============")
            val result = kernel.contactless.setTransactionData()
            tx.clsTransResult = result
            var ret = result.resultCode
            LogUtils.d(TAG, "setTransactionData ret=$ret")
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true
                ret = reselectApplication()
            }
            markTryAgainOnCommunicationError(ret)
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished()
            if (!proceed) {
                kernel.contactless.unregisterClssProcessListener()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contactless set transaction data failed", e)
            finishError(e.message ?: "Contactless EMV set transaction data failed")
        } finally {
            if (!proceed) {
                finishContactlessStage()
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect())
            } else {
                advanceContactlessStage(EmvStep.READ_APPLICATION_DATA)
            }
        }
    }

    /** Read App Data stage — reached via [onReadApplicationData]'s contactless branch. */
    private fun runContactlessReadAppData() {
        var proceed = false
        var reselected = false
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Read App Data ============")
            val result = kernel.contactless.readApplicationData()
            tx.clsTransResult = result
            var ret = result.resultCode
            LogUtils.d(TAG, "readApplicationData ret=$ret")
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true
                ret = reselectApplication()
            }
            markTryAgainOnCommunicationError(ret)
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished()
            if (!proceed) {
                kernel.contactless.unregisterClssProcessListener()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contactless read app data failed", e)
            finishError(e.message ?: "Contactless EMV read app data failed")
        } finally {
            if (!proceed) {
                captureSummaryAfterCardRead()
                finishContactlessStage()
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect())
            } else {
                advanceContactlessStage(
                    if (kernel.contactless.restrictionsBeforeOfflineDataAuth()) {
                        EmvStep.PROCESS_RESTRICTIONS
                    } else {
                        EmvStep.OFFLINE_DATA_AUTHENTICATION
                    },
                )
            }
        }
    }

    /**
     * Offline Data Authentication stage — reached via [onOfflineDataAuthentication]'s contactless
     * branch.
     */
    private fun runContactlessCardAuth() {
        var proceed = false
        var reselected = false
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Offline Data Authentication ============")
            val result = kernel.contactless.offlineDataAuthentication()
            tx.clsTransResult = result
            var ret = result.resultCode
            LogUtils.d(TAG, "offlineDataAuthentication ret=$ret")
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true
                ret = reselectApplication()
            }
            markTryAgainOnCommunicationError(ret)
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished()
            if (!proceed) {
                kernel.contactless.unregisterClssProcessListener()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contactless offline data authentication failed", e)
            finishError(e.message ?: "Contactless EMV offline data authentication failed")
        } finally {
            if (!proceed) {
                captureSummaryAfterCardRead()
                finishContactlessStage()
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect())
            } else if (kernel.contactless.restrictionsBeforeOfflineDataAuth()) {
                // PayWave: restrictions already ran — on to the kernel's remaining call.
                runContactlessStartTransaction()
            } else {
                advanceContactlessStage(EmvStep.PROCESS_RESTRICTIONS)
            }
        }
    }

    /**
     * Processing Restrictions stage — reached via [onProcessRestrictions]'s contactless branch. A
     * granular kernel (EFT, PayPass, PayWave) runs `process.processRestrictions()` here; an atomic
     * kernel arrives here directly from [runContactlessAppSelect] — its SDK has no call for this
     * phase — and skips straight to [runContactlessStartTransaction], the same way mag/manual
     * skip straight to [EmvStep.START_ONLINE_PROCESS] on the contact side.
     */
    private fun runContactlessProcessRestrictions() {
        if (!kernel.contactless.supportsGranularSteps()) {
            runContactlessStartTransaction()
            return
        }
        var proceed = false
        var reselected = false
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Processing Restrictions ============")
            val result = kernel.contactless.processRestrictions()
            tx.clsTransResult = result
            var ret = result.resultCode
            LogUtils.d(TAG, "processRestrictions ret=$ret")
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true
                ret = reselectApplication()
            }
            markTryAgainOnCommunicationError(ret)
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished()
            if (!proceed) {
                kernel.contactless.unregisterClssProcessListener()
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contactless processing restrictions failed", e)
            finishError(e.message ?: "Contactless EMV processing restrictions failed")
        } finally {
            if (!proceed) {
                captureSummaryAfterCardRead()
                finishContactlessStage()
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect())
            } else if (kernel.contactless.restrictionsBeforeOfflineDataAuth()) {
                // PayWave: offline data authentication comes after restrictions.
                advanceContactlessStage(EmvStep.OFFLINE_DATA_AUTHENTICATION)
            } else {
                runContactlessStartTransaction()
            }
        }
    }

    /**
     * Final stage — reached via [runContactlessProcessRestrictions]. Cardholder verification
     * through the first GENERATE AC (bundled — see `ClssKernelProcess#startTransProcess()`'s doc),
     * then online processing if the kernel requested it. Always terminal — always reports via
     * [finishContactlessStage].
     */
    private fun runContactlessStartTransaction() {
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Start Transaction ============")
            val ret = startContactlessTransProcess()
            LogUtils.d(TAG, "startTransProcess ret=$ret")
            captureTransactionSummary()
        } catch (e: Exception) {
            LogUtils.e(TAG, "contactless execution failed", e)
            finishError(e.message ?: "Contactless EMV failed")
        } finally {
            kernel.contactless.unregisterClssProcessListener()
            finishContactlessStage()
        }
    }

    /**
     * Runs the remaining contactless bundle — cardholder verification through 1st GAC — then, if
     * the kernel requests it, the online-authorization round trip including the 2nd-tap check.
     * Ported from `ContactlessService#startTransProcess`; application selection (and, for a
     * granular kernel, read data / offline data auth / processing restrictions) has already run by
     * the time this is reached — see [runContactlessAppSelect] through
     * [runContactlessProcessRestrictions].
     *
     * `CONTACTLESS_FLOW_TYPE` mirrors the one hardcoded value [runPreTransProcess] ever builds
     * (`EmvTransParam.FLOWTYPE_COMPLETE`) — verified repo-wide, nothing ever requests
     * `FLOWTYPE_SIMPLE` for contactless — so the branch below is unreachable today, kept only for
     * parity with the ported logic in case that ever changes. [ClssProcess] itself has no getter
     * for the flow type it was built with, and per its own header comment it shouldn't be touched
     * to add one.
     */
    private fun startContactlessTransProcess(): Int {
        val first = kernel.contactless.startTransProcess()
        tx.clsTransResult = first
        val cvmResult = first.cvmResult
        val resultCode = first.resultCode
        val transResultEnum = first.transResult
        if (resultCode != RetCode.EMV_OK) {
            // PayPass and the bundled kernels exchange with the card inside this call: a card
            // pulled away mid-exchange lands here as ICC_CMD_ERR. Nothing has gone online yet,
            // so it's a tap-again, not a decline.
            markTryAgainOnCommunicationError(resultCode)
            return resultCode
        }
        if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE ||
            transResultEnum == TransResultEnum.RESULT_TRY_AGAIN
        ) {
            // PayPass reports both with EMV_OK: a restart, not a card to take online. The stage
            // end (checkContactlessResult) asks for the re-tap, or declines at the cap.
            return resultCode
        }
        var ret = confirmCard()
        LogUtils.d(TAG, "confirm card: $ret")
        if (ret != RetCode.EMV_OK) {
            // for example, timeout/data_error
            tx.clsTransResult = TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM)
            return ret
        }
        @Suppress("KotlinConstantConditions")
        if (CONTACTLESS_FLOW_TYPE == EmvTransParam.FLOWTYPE_SIMPLE) {
            tx.clsTransResult = TransResult(0, TransResultEnum.RESULT_SIMPLE_FLOW_END, CvmResultEnum.CVM_NO_CVM)
            return 0
        }
        if (cvmResult == CvmResultEnum.CVM_ONLINE_PIN || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
            ret = onCardHolderPwd(true, pinBypass.isOnlinePinBypassEnabled, 0, null)
            // NO_PASSWORD is a PIN bypass (only possible when it's enabled): go online without a
            // PIN block and let the issuer decide.
            if (ret != RetCode.EMV_OK && ret != EmvConstant.ContactCallbackStatus.NO_PASSWORD) {
                // No PIN (cancelled, timed out, PIN pad error): an error, not a card decline. The
                // end is this ERROR; the decline recorded below (so nothing goes online) is
                // dropped by the end guard.
                engine().apduTrace().note("PICC", "online PIN not entered ($ret)")
                finishError("Online PIN not entered — cancelled or timed out")
                tx.clsTransResult = TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, cvmResult)
                return ret
            }
        }
        // Online-only terminal: nothing completes without the host. A refund goes online whatever
        // the card answered (cards don't authorize refunds: they return an AAC, which the Visa
        // kernel reports as TC), and so does any kernel "approved offline". There's no ARQC to
        // complete in those cases, so the host's answer is the outcome.
        if (needsHostWithoutArqc(transResultEnum, resultCode)) {
            noteOnlineWithoutArqc(transResultEnum)
            val online = startOnlineProcess()
            val outcome = hostOutcome(online, cvmResult)
            tx.clsTransResult = outcome
            return outcome.resultCode
        }
        // check whether need goes online
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            val onlineResultWrapper = startOnlineProcess()
            val onlineTransResultEnum = onlineResultWrapper.transResultEnum
            // handle online result after second GAC
            if (TransResultEnum.RESULT_ONLINE_APPROVED != onlineTransResultEnum) {
                val result = tx.clsTransResult!!
                result.transResult = onlineTransResultEnum
                result.resultCode = onlineTransResultEnum.ordinal
                return onlineTransResultEnum.ordinal
            }
            runIssuerAuthentication(onlineResultWrapper)
            // restore signature
            if (cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
                tx.clsTransResult!!.cvmResult = CvmResultEnum.CVM_SIG
            }
        }
        return tx.clsTransResult!!.resultCode
    }

    /**
     * Contactless ISSUER_AUTHENTICATION, host approved: when the issuer sent data for the card
     * (Visa issuer update), the 2nd tap and the kernel's completion; otherwise approved as is.
     */
    private fun completeContactlessIssuerAuthentication(onlineResultWrapper: OnlineResultWrapper) {
        val issuerRspData = onlineResultWrapper.issuerRspData
        val needSecondTap = kernel.contactless.isNeedSecondTap(issuerRspData)
        if (needSecondTap) {
            onDetect2ndTap()
            val result = kernel.contactless.completeTransProcess(issuerRspData)
            tx.clsTransResult = result
            if (result.resultCode == RetCode.EMV_OK) {
                runIssuerScriptProcessing(issuerRspData)
            }
        } else {
            tx.clsTransResult = TransResult(
                RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM,
            )
        }
    }

    /**
     * SCRIPT_PROCESSING after a contactless 2nd tap's issuer authentication: [onScriptProcessing]
     * runs the kernel's script call. If the step doesn't run (the transaction was cancelled
     * meanwhile), the scripts still run here, as they did when they were part of the completion
     * call.
     */
    private fun runIssuerScriptProcessing(issuerRspData: IssuerRspData) {
        tx.pendingScripts = issuerRspData
        tx.scriptsProcessed = true
        goToStep(EmvStep.SCRIPT_PROCESSING, null)
        if (tx.pendingScripts != null) {
            engine().apduTrace().note("EMV", "script processing step did not run — running the scripts anyway")
            processIssuerScripts()
        }
    }

    private fun processIssuerScripts() {
        val issuerRspData = tx.pendingScripts
        tx.pendingScripts = null
        tx.clsTransResult = kernel.contactless.issuerScriptProcessing(issuerRspData)
    }

    /**
     * True when the card finished without an ARQC but the transaction still has to go to the host:
     * any refund the card answered (TC or AAC — cards don't authorize refunds), or a kernel
     * "approved offline" for any type (this terminal approves nothing offline). A card decline
     * (AAC) on anything but a refund stays a decline.
     */
    private fun needsHostWithoutArqc(result: TransResultEnum?, resultCode: Int): Boolean {
        if (resultCode != RetCode.EMV_OK) {
            return false
        }
        if (result == TransResultEnum.RESULT_OFFLINE_APPROVED) {
            return true
        }
        return result == TransResultEnum.RESULT_OFFLINE_DENIED && isRefund()
    }

    private fun isRefund(): Boolean = activeConfig?.type == TransactionType.REFUND

    private fun noteOnlineWithoutArqc(cardResult: TransResultEnum?) {
        val why = if (isRefund()) "refund" else "kernel approved offline"
        LogUtils.i(TAG, "Going online without an ARQC ($why, card result $cardResult)")
        engine().apduTrace().note(
            "EMV", "$why — card result $cardResult, sending to the host (online-only terminal)",
        )
    }

    /**
     * A card-communication error before any outcome — typically the card left the RF field
     * mid-exchange (a tap too short) — is recorded as "try again" rather than the offline decline
     * `ClssProcess` reports for it, as EMV contactless (Book A) requires: the cardholder is asked
     * to tap again: every contactless stage up to and including the kernel's own transaction call
     * (`startTransProcess` — where PayPass and the bundled kernels talk to the card), all before
     * anything is sent to the host. At most [MAX_TAP_RETRIES] times per transaction, so a card that
     * always fails still ends in a decline.
     */
    private fun markTryAgainOnCommunicationError(ret: Int) {
        val communicationError = ret == RetCode.ICC_CMD_ERR || ret == RetCode.ICC_RESET_ERR ||
            ret == RetCode.CLSS_TRY_AGAIN
        if (!communicationError) {
            return
        }
        // The cap is tryAgain()'s: it declines once MAX_TAP_RETRIES taps were tried, whichever
        // way the try-again was reported (here, or by the kernel itself: JCB, Mir, RuPay).
        LogUtils.w(TAG, "Contactless communication error ret=$ret — asking to tap again")
        engine().apduTrace().note("PICC", "card communication error $ret — tap again")
        tx.clsTransResult = TransResult(ret, TransResultEnum.RESULT_TRY_AGAIN, CvmResultEnum.CVM_NO_CVM)
    }

    /** True once a stage has recorded a terminal outcome — no further stage should run. */
    private fun isContactlessTransactionFinished(): Boolean = tx.clsTransResult?.transResult != null

    /** Common tail once a contactless stage chain has reached a terminal outcome. */
    private fun finishContactlessStage() {
        closeReaders(true)
        if (isCancelled()) {
            noteResultNotReported()
            return
        }
        try {
            checkContactlessResult()
        } catch (e: Exception) {
            LogUtils.e(TAG, "checkClsResult error", e)
            finishError(e.message ?: "checkClsResult failed")
        }
    }

    private fun noteResultNotReported() {
        LogUtils.i(TAG, "Cancelled — the card's result is not reported")
        engine().apduTrace().note("EMV", "cancelled — the card's result is not reported")
    }

    /** Advances to `next`; see [advanceContactStage] for why a step that didn't run still cleans up. */
    private fun advanceContactlessStage(next: EmvStep) {
        if (!goToStep(next)) {
            finishContactlessStage()
        }
    }

    /**
     * Maps the recorded [PaxTransactionState.clsTransResult] to the matching result method on
     * `this` — ported from `ContactlessService#checkClsResult`.
     */
    private fun checkContactlessResult() {
        val result = tx.clsTransResult
        if (result == null) {
            LogUtils.e(TAG, "check result: no clsTransResult recorded — treating as offline denied")
            offlineDenied(-1)
            return
        }
        val resultCode = result.resultCode
        val transResultEnum = result.transResult
        val cvmResult = result.cvmResult
        LogUtils.d(
            TAG,
            "result code: $resultCode, trans result: $transResultEnum, cvm result: $cvmResult",
        )
        // A restart outcome, whatever the result code: PayPass and Mir report See Phone and
        // Try Again with EMV_OK, which fell through to "unknown status" and declined.
        if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE) {
            clsLastNeedSeePhone = true
            seePhone()
            return
        }
        if (transResultEnum == TransResultEnum.RESULT_TRY_AGAIN) {
            tryAgain()
            return
        }
        if (resultCode == RetCode.EMV_OK) {
            // CVM_CONSUMER_DEVICE here is CDCVM already *done*: the phone verified the cardholder
            // (Face ID / fingerprint / passcode), a successful CVM like PIN — the outcome below
            // decides approve/decline as for any other CVM. Only RESULT_CLSS_SEE_PHONE (above)
            // means the cardholder still has to verify on the phone.
            if (cvmResult == CvmResultEnum.CVM_CONSUMER_DEVICE) {
                LogUtils.d(TAG, "CDCVM performed on the consumer device")
            }
            if (transResultEnum == TransResultEnum.RESULT_OFFLINE_APPROVED) {
                offlineApproved(cvmResult == CvmResultEnum.CVM_SIG)
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_APPROVED) {
                onlineApproved(cvmResult == CvmResultEnum.CVM_SIG)
            } else if (transResultEnum == TransResultEnum.RESULT_SIMPLE_FLOW_END) {
                simpleFlowEnd()
            } else {
                // Unknown status
                offlineDenied(RetCode.CLSS_DECLINE)
            }
        } else {
            if (transResultEnum == TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE ||
                resultCode == RetCode.CLSS_USE_CONTACT
            ) {
                // restart detect icc card and transaction
                tryAnotherInterface()
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_DENIED) {
                // host reject, prompt has been showed during online
                onlineDenied()
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_FAILED) {
                // such as connect error, receive error, pack error, prompt has been showed during online
                onlineFailed()
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_CARD_DENIED) {
                onlineCardDenied(resultCode)
            } else if (transResultEnum == TransResultEnum.RESULT_OFFLINE_DENIED) {
                // here we need prompt details by resultCode
                offlineDenied(resultCode)
            } else {
                // Unknown status
                offlineDenied(resultCode)
            }
        }
    }

    /** Ported from `ContactlessService#getPan`. */
    private fun getContactlessPan(): String {
        val cached = tx.clsCachedTrack2Data
        if (!cached.isNullOrEmpty()) {
            return TrackUtils.getPan(cached)
        }
        val track2Data = getContactlessTrack2Data()
        if (!track2Data.isNullOrEmpty()) {
            return TrackUtils.getPan(track2Data)
        }
        // some cards don't have track2 data
        val panBytes = kernel.contactless.getTlv(TagsTable.PAN)
        val pan = ConvertUtils.bcd2Str(panBytes, panBytes.size)
        val indexF = pan.indexOf('F')
        return pan.substring(0, if (indexF != -1) indexF else pan.length)
    }

    /** Ported from `ContactlessService#getTrack2Data`. */
    private fun getContactlessTrack2Data(): String? {
        tx.clsCachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contactless.getTlv(TagsTable.TRACK2))
        return tx.clsCachedTrack2Data
    }

    /** Cardholder name (5F20) as text, e.g. "MTIP06 MCD 25A", as the old app sent it. */
    private fun getContactlessCardholderName(): String =
        asciiText(kernel.contactless.getTlv(TagsTable.CARDHOLDER_NAME))

    // ─── CONTACT ONLY — ContactProcess runners ────────────────────────────
    //
    // Contact chip flow, driven one kernel stage at a time from here instead of one monolithic
    // kernel call: runContactAppSelect → runContactReadAppData → runContactSetTransactionData →
    // runContactCardAuth → runContactStartTransaction. Each stage calls the matching
    // ContactProcess method (which itself calls a single top-level EMVCallback.EMV___ native
    // call), then either advances via advanceContactStage — which normally calls goToStep,
    // publishing the next EmvStep and dispatching to that step's onXxx method, which hands
    // straight back to the matching run* method — or, on error or a terminal business result such
    // as simple-flow-end, per isContactTransactionFinished(), stops and reports via
    // finishContactStage.
    //
    // After a cancel no step runs (the engine publishes none) — which for every other goToStep
    // transition in this class (mag/manual, APPLICATION_SELECTION) is fine, since there's no
    // vendor-owned resource left open to clean up. For chip the ICC reader stays activated across
    // the whole app-select → start-transaction span, so advanceContactStage falls back to
    // finishContactStage when goToStep reports the step didn't run: the readers are always closed.
    //
    // Processing restrictions, cardholder verification, terminal risk management and terminal
    // action analysis stay bundled inside runContactStartTransaction's EMVStartTrans call — the
    // PAX kernel has no separate native entry point for each, so they can't be split out any
    // further.

    private fun runContactAppSelect() {
        var proceed = false
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance())
            LogUtils.d(TAG, "============ Contact EMV: App Select ============")
            // reset every transaction — mirrors EmvContactService#selectApplication
            tx.cachedTrack2Data = null
            resetTransactionSummary()
            kernel.contact.registerEmvProcessListener(this)
            val result = selectApplicationWithRetry()
            tx.transResult = result
            val ret = result.resultCode
            LogUtils.d(TAG, "selectApplication ret=$ret")
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished()
            if (!proceed) {
                // Selection failed — the transaction ends here; release the listener now since
                // startTransProcess() (and its own unregister) will never run.
                kernel.contact.registerEmvProcessListener(null)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contact app select failed", e)
            finishError(e.message ?: "Contact EMV app select failed")
        } finally {
            // Readers stay open (and nothing is reported yet) only while we're about to chain
            // into the next stage — every other exit (cancel, exception, terminal result) must
            // close them here, since no later stage will run to do it.
            if (!proceed) {
                finishContactStage()
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.READ_APPLICATION_DATA)
        }
    }

    /**
     * Retries `selectApplication()` up to [ICC_APP_SELECT_MAX_ATTEMPTS] times, power-cycling the
     * ICC slot between attempts, when the first attempt comes back non-OK. Native
     * `IccException: 51` (no ATR) surfaces here as a plain `ret == -1` — the kernel catches the
     * exception internally and returns a result code, so there's nothing to catch on our side,
     * only the code to react to. Restarting app selection from scratch on retry is safe: nothing
     * has been selected yet at this point in the flow.
     */
    private fun selectApplicationWithRetry(): TransResult {
        var result = kernel.contact.selectApplication()
        var attempt = 1
        while (result.resultCode != RetCode.EMV_OK && attempt < ICC_APP_SELECT_MAX_ATTEMPTS && !isCancelled()) {
            LogUtils.w(
                TAG,
                "selectApplication ret=" + result.resultCode + " (attempt " + attempt + "/" +
                    ICC_APP_SELECT_MAX_ATTEMPTS + ") — power-cycling ICC and retrying",
            )
            try {
                EmvFlowRuntime.getDal()?.icc?.close(0.toByte())
            } catch (ignored: Exception) {
                // Expected if the slot is already in a bad state — closing here is only to force
                // a fresh power-on for the next attempt, not to succeed cleanly.
            }
            try {
                Thread.sleep(ICC_POWER_SETTLE_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
            result = kernel.contact.selectApplication()
            attempt++
        }
        return result
    }

    /** Read App Data stage — reached via [onReadApplicationData]'s chip branch. */
    private fun runContactReadAppData() {
        var proceed = false
        try {
            LogUtils.d(TAG, "============ Contact EMV: Read App Data ============")
            val result = kernel.contact.readApplicationData()
            tx.transResult = result
            val ret = result.resultCode
            LogUtils.d(TAG, "readApplicationData ret=$ret")
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished()
            if (!proceed) {
                kernel.contact.registerEmvProcessListener(null)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contact read app data failed", e)
            finishError(e.message ?: "Contact EMV read app data failed")
        } finally {
            if (!proceed) {
                finishContactStage()
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.SET_TRANSACTION_DATA)
        }
    }

    /**
     * Set Transaction Data stage — reached via [onSetTransactionData]'s chip branch, after
     * READ_APPLICATION_DATA: the contact kernel takes the transaction data after reading the card
     * (confirm card, 9C).
     */
    private fun runContactSetTransactionData() {
        var proceed = false
        try {
            LogUtils.d(TAG, "============ Contact EMV: Set Transaction Data ============")
            val result = kernel.contact.setTransactionData()
            tx.transResult = result
            val ret = result.resultCode
            LogUtils.d(TAG, "setTransactionData ret=$ret")
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished()
            if (!proceed) {
                kernel.contact.registerEmvProcessListener(null)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contact set transaction data failed", e)
            finishError(e.message ?: "Contact EMV set transaction data failed")
        } finally {
            if (!proceed) {
                finishContactStage()
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.OFFLINE_DATA_AUTHENTICATION)
        }
    }

    /** Card Auth (CAPK + EMVCardAuth) stage — reached via [onOfflineDataAuthentication]'s chip branch. */
    private fun runContactCardAuth() {
        var proceed = false
        try {
            LogUtils.d(TAG, "============ Contact EMV: Card Auth ============")
            val result = kernel.contact.cardAuthentication()
            tx.transResult = result
            val ret = result.resultCode
            LogUtils.d(TAG, "cardAuthentication ret=$ret")
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished()
            if (!proceed) {
                kernel.contact.registerEmvProcessListener(null)
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "contact card auth failed", e)
            finishError(e.message ?: "Contact EMV card auth failed")
        } finally {
            if (!proceed) {
                finishContactStage()
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.PROCESS_RESTRICTIONS)
        }
    }

    /**
     * Advances to `next` via [goToStep]. If it didn't run — cancelled or ended in between — the
     * next run* method (and its [finishContactStage] cleanup) never fires, so the cleanup runs
     * here instead: the readers are never left open.
     */
    private fun advanceContactStage(next: EmvStep) {
        if (!goToStep(next)) {
            finishContactStage()
        }
    }

    /**
     * Final stage — reached via [onProcessRestrictions]'s chip branch. EMVStartTrans (processing
     * restrictions → CVM → terminal risk management → terminal action analysis → 1st GAC,
     * bundled — see class doc), then online processing if the kernel requested it. Always
     * terminal — always reports via [finishContactStage].
     */
    private fun runContactStartTransaction() {
        try {
            LogUtils.d(TAG, "============ Contact EMV: Start Transaction ============")
            // CVM is published separately from onCardHolderPwd below when the kernel actually
            // asks for it; terminal risk management and terminal action analysis have no callback
            // and are never published (see class doc).
            val ret = startContactTransProcess()
            LogUtils.d(TAG, "startTransProcess ret=$ret")
            captureTransactionSummary()
        } catch (e: Exception) {
            LogUtils.e(TAG, "contact execution failed", e)
            finishError(e.message ?: "Contact EMV failed")
        } finally {
            kernel.contact.registerEmvProcessListener(null)
            finishContactStage()
        }
    }

    /**
     * Runs EMVStartTrans and, if the kernel requests it, the online-authorization round trip (host
     * authorize via [startOnlineProcess] → EMVCompleteTrans). Ported from
     * `EmvContactService#startTransProcess` — the AET-146 comment below is carried over verbatim.
     */
    private fun startContactTransProcess(): Int {
        kernel.contact.registerEmvProcessListener(this)
        val first = kernel.contact.startTransProcess()
        tx.transResult = first
        val resultCode = first.resultCode
        val transResultEnum = first.transResult
        if (resultCode != RetCode.EMV_OK) {
            return resultCode
        }
        // Same rule as contactless (see startContactlessTransProcess): a refund goes online
        // whatever the card answered at 1st GAC — no 2nd GAC, the host's answer is the outcome.
        if (needsHostWithoutArqc(transResultEnum, resultCode)) {
            noteOnlineWithoutArqc(transResultEnum)
            val online = startOnlineProcess()
            val outcome = hostOutcome(online, tx.transResult!!.cvmResult)
            tx.transResult = outcome
            return outcome.resultCode
        }
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            val onlineResultWrapper = startOnlineProcess()
            val result = tx.transResult!!
            result.resultCode = onlineResultWrapper.resultCode
            result.transResult = onlineResultWrapper.transResultEnum
            runIssuerAuthentication(onlineResultWrapper)
            return tx.transResult!!.resultCode
        }
        return 0
    }

    /**
     * Contact ISSUER_AUTHENTICATION: the card's 2nd GENERATE AC (EMVCompleteTrans) with the
     * host's answer, and the outcome it gives.
     */
    private fun completeContactIssuerAuthentication(onlineResultWrapper: OnlineResultWrapper) {
        val issuerRspData = onlineResultWrapper.issuerRspData
        val onlineResultCode = onlineResultWrapper.resultCode
        /*
         * //AET-146
         * Whatever value it returns from startOnlineProcess(), 2nd GAC should be performed as per EMV Book 3
         * The right way to fix AET-146 is to map ABORT_TERMINATED to ONLINE_FAILED so that
         * EMVApi#EMVCompleteTrans will not return -30: emv param error
         *
         * should ensure script.length will not throw Null Pointer Exception
         * If Field 39 responsed from host is not 00, EMVCallback.EMVCompleteTrans will return -11(EMV DENIAL)
         *
         * 1st param of EMVApi#EMVCompleteTrans only accept 3 values:
         * ONLINE_APPROVE, ONLINE_DENIAL, and ONLINE_FAILED,
         * reference to the API doc of JNI_EMV_LIB_v102
         */
        Log.d(TAG, "startContactTransProcess: $issuerRspData")
        val secondTransResult = kernel.contact.completeTransProcess(issuerRspData)

        val result = tx.transResult!!
        result.resultCode = secondTransResult.resultCode
        if (onlineResultCode == EOnlineResult.APPROVE.resultCode) {
            result.transResult = if (secondTransResult.transResult != TransResultEnum.RESULT_ONLINE_APPROVED) {
                TransResultEnum.RESULT_ONLINE_CARD_DENIED
            } else {
                TransResultEnum.RESULT_ONLINE_APPROVED
            }
        } else if (onlineResultCode == EOnlineResult.FAILED.resultCode) {
            result.transResult = if (secondTransResult.transResult != TransResultEnum.RESULT_ONLINE_APPROVED) {
                TransResultEnum.RESULT_ONLINE_FAILED
            } else {
                TransResultEnum.RESULT_ONLINE_FAILED_CARD_APPROVED
            }
        } else {
            result.transResult = TransResultEnum.RESULT_ONLINE_DENIED
        }
    }

    /** True once a stage has recorded a terminal outcome — no further stage should run. */
    private fun isContactTransactionFinished(): Boolean = tx.transResult?.transResult != null

    /** Common tail once a contact stage chain has reached a terminal outcome. */
    private fun finishContactStage() {
        closeReaders(false)
        tx.activePinPad?.let {
            it.dismiss()
            tx.activePinPad = null
        }
        if (isCancelled()) {
            noteResultNotReported()
            return
        }
        try {
            checkContactResult()
        } catch (e: Exception) {
            LogUtils.e(TAG, "checkContactResult error", e)
            finishError(e.message ?: "checkContactResult failed")
        }
    }

    /**
     * Maps the recorded [PaxTransactionState.transResult] to the matching result method on `this` —
     * ported from `EmvContactService#checkContactResult`.
     */
    private fun checkContactResult() {
        val result = tx.transResult
        if (result == null) {
            LogUtils.e(TAG, "check result: no transResult recorded — treating as offline denied")
            offlineDenied(-1)
            return
        }
        val resultCode = result.resultCode
        val transResultEnum = result.transResult
        if (transResultEnum == null) {
            // No stage ever reached a terminal result — e.g. an exception aborted the chain
            // before that stage's own transResult assignment ran, leaving the "continue"
            // sentinel (RetCode.EMV_OK, no enum) from the prior successful stage in place.
            // Fail closed instead of NPE-ing on the when below.
            LogUtils.e(TAG, "check result: no terminal result recorded, code = $resultCode — treating as offline denied")
            offlineDenied(resultCode)
            return
        }
        val cvmResult = result.cvmResult ?: CvmResultEnum.CVM_NO_CVM
        LogUtils.d(TAG, "check result: code = " + resultCode + ", enum = " + transResultEnum.name + ", cvm = " + cvmResult.name)
        val isNeedSignature = cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG
        when (transResultEnum) {
            TransResultEnum.RESULT_OFFLINE_APPROVED -> offlineApproved(isNeedSignature, true)
            TransResultEnum.RESULT_ONLINE_APPROVED -> onlineApproved(isNeedSignature)
            TransResultEnum.RESULT_ONLINE_CARD_DENIED -> onlineCardDenied(resultCode)
            // No host answer: the card approving at 2nd GAC doesn't make it approved here.
            TransResultEnum.RESULT_ONLINE_FAILED_CARD_APPROVED -> onlineFailed()
            TransResultEnum.RESULT_ONLINE_FAILED -> onlineFailed()
            TransResultEnum.RESULT_ONLINE_DENIED -> onlineDenied()
            TransResultEnum.RESULT_OFFLINE_DENIED -> offlineDenied(resultCode)
            TransResultEnum.RESULT_SIMPLE_FLOW_END -> simpleFlowEnd()
            TransResultEnum.RESULT_FALLBACK -> fallback()
            else -> {
                LogUtils.e(TAG, "Cannot handle this statement")
                offlineDenied(resultCode)
            }
        }
    }

    /** Ported from `EmvContactService#getPan`. */
    private fun getContactPan(): String {
        val cached = tx.cachedTrack2Data
        if (!cached.isNullOrEmpty()) {
            return TrackUtils.getPan(cached)
        }
        val track2Data = getContactTrack2Data()
        if (!track2Data.isNullOrEmpty()) {
            return TrackUtils.getPan(track2Data)
        }
        // some cards don't have track2 data
        val panBytes = kernel.contact.getTlv(TagsTable.PAN)
        val pan = ConvertUtils.bcd2Str(panBytes, panBytes.size)
        val indexF = pan.indexOf('F')
        return pan.substring(0, if (indexF != -1) indexF else pan.length)
    }

    /** Ported from `EmvContactService#getTrack2Data`. */
    private fun getContactTrack2Data(): String? {
        tx.cachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contact.getTlv(TagsTable.TRACK2))
        return tx.cachedTrack2Data
    }

    /** Cardholder name (5F20) as text, e.g. "MTIP06 MCD 25A", as the old app sent it. */
    private fun getContactCardholderName(): String = asciiText(kernel.contact.getTlv(TagsTable.CARDHOLDER_NAME))

    // ─── SHARED — kernel bring-up used by both contact and contactless ───

    private fun prepareKernel(config: TransactionConfig): Boolean {
        if (!config.allowsChip() && !config.allowsContactless()) {
            return true
        }
        try {
            EmvFlowRuntime.init(EmvFlowRuntime.getApp())
        } catch (t: Throwable) {
            LogUtils.e(TAG, "EmvFlowRuntime.init failed", t)
        }
        if (!EmvFlowRuntime.isReady()) {
            LogUtils.e(TAG, "Neptune DAL is not ready — cannot start EMV")
            return false
        }

        initializeTerminalIfNeeded()

        // Defensive reset: close every reader before this attempt touches any of them, in case
        // the previous transaction (crash, process death, a partial closeReaders() failure) left
        // one open. Closing an already-closed reader is a no-op, so this is safe to run
        // unconditionally on every attempt, not just after a known-bad prior transaction.
        closeReaders(true)

        var requested = 0
        if (config.allowsChip()) {
            requested = requested or SearchMode.INSERT.toInt()
        }
        if (config.allowsContactless()) {
            requested = requested or SearchMode.INTERNAL_WAVE.toInt()
        }
        if (requested == 0) {
            return false
        }
        return try {
            // Fresh kernel object per transaction attempt, not a process-lifetime singleton —
            // this reset used to live inside EmvContactService#preTransProcess.
            kernel.contact = ContactProcess()
            val setUpStart = SystemClock.elapsedRealtime()
            val adjusted = runPreTransProcess(config, requested.toByte())
            engine().apduTrace().note(
                "EMV", "kernel set-up for this transaction: " + (SystemClock.elapsedRealtime() - setUpStart) + " ms",
            )
            kernel.contactReady = SearchMode.isSupportIcc(adjusted)
            kernel.contactlessReady = SearchMode.isWave(adjusted)
            if (config.allowsContactless() && !kernel.contactlessReady) {
                engine().apduTrace().note("PICC", "contactless disabled for this transaction: pre-processing failed")
            }
            if (adjusted.toInt() == 0) {
                LogUtils.e(TAG, "preTransProcess disabled every search mode")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "preTransProcess failed", e)
            false
        }
    }

    /**
     * Terminal initialization: loads every EMV/CLSS parameter (AIDs, CAPKs, terminal config,
     * contactless kernels) from the database into memory, in step 0 before the card search — the
     * first transaction after an app start, a download or a seed ([EmvParamService.isLoaded], the
     * one owner). Every transaction after that reuses them and only runs its own kernel set-up
     * ([runPreTransProcess]: amount, date/time, 9F41, contactless pre-processing), which PAX and
     * EMV need per transaction.
     */
    private fun initializeTerminalIfNeeded() {
        if (EmvParamService.isLoaded()) {
            return
        }
        val start = SystemClock.elapsedRealtime()
        kernel.params.cachedEmvParam
        engine().apduTrace().note(
            "EMV",
            "terminal initialization (EMV parameters loaded from the database): " +
                (SystemClock.elapsedRealtime() - start) + " ms",
        )
    }

    /**
     * Builds [EmvTransParam]/[EmvProcessParam] from `config` and the cached EMV parameters, then
     * runs contact / contactless `preTransProcess`, clearing the corresponding [SearchMode] bit for
     * whichever one fails. Ported from `EmvPreProcessFacade#start` — single caller, no
     * service-locator indirection, and `emvflow` (the module it used to live in) is PAX-only
     * already, so nothing else needed it as a separate object.
     */
    private fun runPreTransProcess(config: TransactionConfig, mode: Byte): Byte {
        var searchCardMode = mode
        if (!SearchMode.isSupportIcc(searchCardMode) && !SearchMode.isWave(searchCardMode)) {
            return 0
        }
        DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance())
        val proc = ConvertHelper.getConvert().strToBcdPaddingRight(config.procCode)
        val cachedEmvParam = kernel.params.cachedEmvParam
        val dateTime = timestamp()
        // One trans param for both kernels. PCI mode, PIN lengths and PIN timeout are read by the
        // contact kernel only (EMVSetPCIModeParam); contactless ignores them.
        val transParam = EmvTransParam.Builder()
            .setTransType(proc[0])
            .setAmount(config.amountMinor)
            .setAmountOther(AMOUNT_OTHER_MINOR)
            .setTerminalID(cachedEmvParam.termConfig.terminalId)
            .setTransCurrencyCode(CurrencyConverter.getCurrencyCode())
            .setTransCurrencyExponent(CurrencyConverter.getDigitsNum().toByte())
            .setTransDate(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(2, 8)))
            .setTransTime(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(8)))
            .setTransTraceNo(TRANSACTION_SEQUENCE_COUNTER)
            .setFlowType(EmvTransParam.FLOWTYPE_COMPLETE)
            .setMaskPattern("")
            .setPciMode(1.toByte())
            .setPinLenSet(offlinePinLengths().toByteArray(Charsets.US_ASCII))
            .setPciTimeout(60 * 1000)
            .create()
        val processParamBuilder = EmvProcessParam.Builder()
            .setTermConfig(cachedEmvParam.termConfig)
            .setCapkParam(cachedEmvParam.capkParam)
            .setEmvTransParam(transParam)

        if (SearchMode.isSupportIcc(searchCardMode)) {
            processParamBuilder.setEmvAidList(cachedEmvParam.emvAidList)
            val contactRet = kernel.contact.preTransProcess(processParamBuilder.create())
            if (contactRet != RetCode.EMV_OK) {
                LogUtils.e(TAG, "contact pre process failed, ret=$contactRet")
                searchCardMode = (searchCardMode.toInt() and SearchMode.INSERT.toInt().inv()).toByte()
            }
        }
        if (SearchMode.isSupportInternalPicc(searchCardMode) || SearchMode.isSupportExternalPicc(searchCardMode)) {
            processParamBuilder.setAmexParam(cachedEmvParam.amexParam)
                .setPassParam(cachedEmvParam.payPassParam)
                .setPayWaveParam(cachedEmvParam.payWaveParam)
                .setDpasParam(cachedEmvParam.dpasParam)
                .setEFTParam(cachedEmvParam.eftParam)
                .setJcbParam(cachedEmvParam.jcbParam)
                .setMirParam(cachedEmvParam.mirParam)
                .setPbocParam(cachedEmvParam.pbocParam)
                .setPureParam(cachedEmvParam.pureParam)
                .setRuPayParam(cachedEmvParam.ruPayParam)
            val contactlessRet = kernel.contactless.preTransProcess(processParamBuilder.create())
            if (contactlessRet != RetCode.EMV_OK) {
                LogUtils.e(
                    TAG,
                    "contactless pre process failed, ret=$contactlessRet — contactless won't be offered for this transaction",
                )
                searchCardMode = (searchCardMode.toInt() and SearchMode.WAVE.toInt().inv()).toByte()
            }
        }
        return searchCardMode
    }

    // ═══════════════════════════════════════════════════════════════════
    // IContactCallback + IContactlessCallback overrides
    // Each is tagged below — CONTACT / CONTACTLESS / SHARED (both interfaces
    // route through the same method; see class doc + §09/§14/§15 of the
    // EMV Transaction Backbone artifact for why). Plain functions on the transaction
    // thread: the native kernel calls them and waits for the return value.
    // ═══════════════════════════════════════════════════════════════════

    // [CONTACTLESS]
    override fun onReadCardOk() {
        if (!kernel.contactless.supportsGranularSteps()) {
            // An atomic kernel read the application data itself, inside its one bundled call:
            // publish the step for the UI and the trace — onReadApplicationData knows not to read
            // again. A granular kernel (EFT, PayPass, PayWave) already went through it via
            // runContactlessReadAppData(), so publishing it here would be a duplicate.
            goToStep(EmvStep.READ_APPLICATION_DATA, null)
        }
        publish(
            TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"),
        )
    }

    // [CONTACTLESS]
    override fun confirmCard(): Int {
        if (!kernel.contactless.supportsGranularSteps()) {
            // An atomic kernel took the data inside its one call; a granular one already went
            // through SET_TRANSACTION_DATA (runContactlessSetTransactionData).
            goToStep(EmvStep.SET_TRANSACTION_DATA, null)
        }
        // The contactless kernels don't take the TID from the trans param; set it for field 55.
        kernel.contactless.setTlv(TagsTable.TERMINAL_ID, kernel.params.cachedEmvParam.termConfig.terminalId)
        publish(
            TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, safe(getContactlessPan()))
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "PAX Issuer")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, safe(getContactlessCardholderName()))
                .put(TransactionStepEvent.KEY_MODE, "Contactless")
                .build(),
        )
        return EmvConstant.ContactCallbackStatus.CONTACT_OK
    }

    // [SHARED — same method satisfies both IContactCallback and IContactlessCallback]
    override fun onWaitAppSelect(isFirstSelect: Boolean, candList: List<CandidateAID>?): Int {
        // The kernel waits on the choice: the step method makes it (answerApplicationSelection).
        // Fail closed: a cancelled or ended transaction answers USER_CANCEL, never a hang.
        tx.candidates = candList
        val candidates = candList?.size ?: 0
        return askStep(
            if (isFirstSelect) EmvStep.WAIT_APPLICATION_SELECTION else EmvStep.FINAL_APPLICATION_SELECTION,
            "$candidates candidate AID(s)" + if (showsAppSelectDialog(candList)) "" else ", selecting first",
            EmvConstant.ContactCallbackStatus.USER_CANCEL,
        )
    }

    // [CONTACT]
    override fun showConfirmCard(): Int {
        // Called by ContactProcess.setTransactionData(), inside SET_TRANSACTION_DATA's own step.
        publish(
            TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, safe(getContactPan()))
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "PAX Issuer")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, safe(getContactCardholderName()))
                .put(TransactionStepEvent.KEY_MODE, "Contact")
                .build(),
        )
        return EmvConstant.ContactCallbackStatus.CONTACT_OK
    }

    // [SHARED — same method satisfies both interfaces; branches internally on isOnlinePin]
    override fun onCardHolderPwd(isOnlinePin: Boolean, supportPINByPass: Boolean, leftTimes: Int, pinData: ByteArray?): Int {
        // The kernel (contact) or startContactlessTransProcess waits on the PIN: the step method
        // collects it (enterPin) and answers. Fail closed: a cancelled or ended transaction
        // answers USER_CANCEL, never a hang.
        tx.pinRequest = PinRequest(isOnlinePin, supportPINByPass, leftTimes)
        try {
            if (isOnlinePin) {
                return askStep(EmvStep.CARDHOLDER_VERIFICATION, "online PIN", EmvConstant.ContactCallbackStatus.USER_CANCEL)
            }
            goToStep(EmvStep.CARDHOLDER_VERIFICATION, "PIN")
            return askStep(EmvStep.OFFLINE_PIN_VERIFICATION, null, EmvConstant.ContactCallbackStatus.USER_CANCEL)
        } finally {
            tx.pinRequest = null
        }
    }

    /**
     * The PIN step's work, answered to [onCardHolderPwd]'s `askStep`: online PIN from
     * [onCardholderVerification], offline PIN from [onOfflinePinVerification].
     */
    private fun enterPin(request: PinRequest): Int {
        val isOnlinePin = request.online
        val supportPINByPass = request.supportPinBypass
        val leftTimes = request.leftTimes
        publish(
            TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, isOnlinePin)
                .put(
                    TransactionStepEvent.KEY_PIN_BYPASS,
                    supportPINByPass &&
                        if (isOnlinePin) pinBypass.isOnlinePinBypassEnabled else pinBypass.isOfflinePinBypassEnabled,
                )
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, leftTimes)
                .build(),
        )

        val pinService = PinService()

        if (!isOnlinePin) {
            // Offline PIN never produces a PIN block for the host — nothing goes in encPinBlock.
            clearOnlinePin()
            // Offline PIN is the kernel's job, not ours: once we say CONTACT_OK, it calls
            // EmvDeviceImpl#pedVerifyPlainPin / #pedVerifyCipherPin (the native IDevice callback)
            // directly, which is already fully wired to IPed#verifyPlainPin / #verifyCipherPin —
            // the real secure PIN pad, real verification against the ICC. What's missing without
            // the two lines below is only the on-screen feedback: PAX's own demo (OfflinePinTask)
            // always wires setInputPinListener + EmvDeviceImpl's pinCallback and shows a dialog
            // before returning CONTACT_OK, fire-and-forget — the dialog is dismissed by whichever
            // of PinCallback's terminal callbacks EmvDeviceImpl invokes (onCancel/onTimeout/
            // onError), or defensively in finishContactStage() if none of those fire.
            tx.activePinPad?.dismiss() // the kernel asks again (wrong PIN): one pad on screen
            val pad = PaxPinPad("Enter Offline PIN ($leftTimes left)")
            tx.activePinPad = pad
            pad.showForOfflinePin()
            pinService.setInputPinListener(pad)
            EmvDeviceImpl.getInstance().setPinCallback(pad)
            return EmvConstant.ContactCallbackStatus.CONTACT_OK
        }

        // Online PIN never touches the card, so nothing in the native kernel collects it for us —
        // show the secure pad ourselves, matching PAX's own demo (OnlinePinTask): a dialog shown
        // and waited on via ConditionVariable before the blocking collect call, so the key-event
        // listener always has a live dialog to update. Uses PinService directly (same object for
        // both the listener and the actual collection) instead of duplicating its already-correct
        // getPinBlock(keyIndex, pinLenCsv, panBytes, mode, timeoutMs) call.
        //
        // Bypass needs the terminal setting, and for contact the selected AID too
        // (supportPINByPass); contactless passes the setting itself.
        val bypassAllowed = supportPINByPass && pinBypass.isOnlinePinBypassEnabled
        tx.lastOnlinePinBlock = null
        provisionOnlinePinKey()

        val pad = PaxPinPad(if (bypassAllowed) "Enter Online PIN (Enter to skip)" else "Enter Online PIN")
        tx.activePinPad = pad
        pad.showForOnlinePin()
        pinService.setInputPinListener(pad)
        try {
            val ped = PedHelper.getPed()
            ped.setIntervalTime(1, 1)
            val card = activeCard
            val pan = if (card != null && card.isContactless) getContactlessPan() else getContactPan()
            val pinBlock = pinService.getEncryptedPinData(safe(pan), bypassAllowed, false)
            if (pinBlock == null || pinBlock.isEmpty()) {
                // Enter with no digits: the PIN pad only allows it when bypass is allowed.
                clearOnlinePin()
                return if (bypassAllowed) {
                    EmvConstant.ContactCallbackStatus.NO_PASSWORD
                } else {
                    EmvConstant.ContactCallbackStatus.USER_CANCEL
                }
            }
            tx.lastOnlinePinBlock = pinBlock
            return EmvConstant.ContactCallbackStatus.CONTACT_OK
        } catch (e: PinException) {
            // No PIN block, so no PIN key either — the host must not expect one.
            clearOnlinePin()
            if (bypassAllowed && NO_PIN_INPUT == e.errCode) {
                return EmvConstant.ContactCallbackStatus.NO_PASSWORD
            }
            LogUtils.e(TAG, "online PIN entry failed", e)
            return EmvConstant.ContactCallbackStatus.USER_CANCEL
        } catch (e: PedDevException) {
            LogUtils.e(TAG, "online PIN entry failed", e)
            clearOnlinePin()
            return EmvConstant.ContactCallbackStatus.USER_CANCEL
        } finally {
            pinService.setInputPinListener(null)
            pad.dismiss()
        }
    }

    /**
     * PIN lengths the PIN pad accepts for offline PIN (the contact kernel's PCI mode). A `0` lets
     * Enter with no digits through, which the kernel treats as a PIN bypass — only when offline
     * PIN bypass is enabled.
     */
    private fun offlinePinLengths(): String {
        val lengths = "4,5,6,7,8,9,10,11,12"
        return (if (pinBypass.isOfflinePinBypassEnabled) "0,$lengths" else lengths) + "\u0000"
    }

    /** Forgets this transaction's online PIN block and PIN key (none collected / not online). */
    private fun clearOnlinePin() {
        tx.lastOnlinePinBlock = null
        tx.lastOnlinePinKeyEncrypted = null
    }

    /**
     * Generates a fresh random online PIN key (PEK) for this transaction, writes it to
     * [PosDeviceUtils.INDEX_AES_PEK] as an **AES** key so `getPinBlock` produces an AES-encrypted
     * PIN block (confirmed against the server team — it decrypts the PIN block with AES, not
     * 3DES), and RSA-wraps the same key value with the host's public key (from onboarding —
     * [OnboardingState.getPublicKey]) so the host can independently recover it. Runs every
     * online-PIN transaction, not just once — a fresh key per transaction, not a reused one,
     * matching the old project's `preparePekKey()` being called at the start of every sale.
     *
     * Ported from the old project's `PaxUtils#writePekAES` — `EPedKeyType.AES_TPK` (a distinct key
     * type from the plain, 3DES-family `EPedKeyType.TPK`) written via `IPed#writeAesKey`, erasing
     * whatever's at that index first, wrapped under `EPedKeyType.TLK` index 0 (not `TMK` — the AES
     * key hierarchy uses a different wrapping key type than the 3DES one). The key type written
     * here must match the PIN-block mode `PinService#getEncryptedPinData` reads with: the 3DES
     * `EPinBlockMode.ISO9564_0` call reads the 3DES `TPK` slot, not this `AES_TPK`, so it uses the
     * AES ISO 9564 format 4 mode (see `PinService`'s `PIN_BLOCK_MODE_ISO9564_4_AES`).
     *
     * That assumes a TLK is already present at index 0, true on PAX SDK demo/dev units out of the
     * box; a real deployment provisions its own TLK (see the onboarding online/TMK-provisioning
     * cycle, currently dormant — `OnboardingClient#runOnlineCycle` — which would need an AES
     * counterpart if it's ever turned on for this key hierarchy too). If the write fails (no TLK)
     * or no public key has been onboarded yet, this logs and leaves
     * [PaxTransactionState.lastOnlinePinKeyEncrypted] unset — online PIN entry itself still proceeds
     * (matching this method's predecessor's behavior), it just won't have a key the host can
     * decrypt with.
     */
    private fun provisionOnlinePinKey() {
        tx.lastOnlinePinKeyEncrypted = null

        val pinKey = ByteArray(ONLINE_PIN_KEY_LENGTH_BYTES)
        SecureRandom().nextBytes(pinKey)

        val ped = PedHelper.getPed()
        // Clear the slot before every PIN entry so nothing stale can be used. Both PIN key types
        // at this index are erased; an empty slot throws, which isn't a reason to skip the write.
        eraseKeyQuietly(ped, EPedKeyType.AES_TPK, PosDeviceUtils.INDEX_AES_PEK)
        eraseKeyQuietly(ped, EPedKeyType.TPK, PosDeviceUtils.INDEX_AES_PEK)
        try {
            ped.writeAesKey(
                EPedKeyType.TLK.pedkeyType, 0.toByte(),
                EPedKeyType.AES_TPK.pedkeyType, PosDeviceUtils.INDEX_AES_PEK, pinKey,
                EAesCheckMode.KCV_NONE, null,
            )
        } catch (e: PedDevException) {
            LogUtils.e(
                TAG,
                "Online PIN key write failed — no TLK at index 0? Online PIN will " +
                    "keep failing 'Key does not exist' until a real TLK is injected.",
                e,
            )
            return
        }

        val hostPublicKey = OnboardingState(BaseApplication.getAppContext()).getPublicKey()
        if (hostPublicKey == null) {
            LogUtils.w(
                TAG,
                "No host public key on file (onboarding not completed?) — online PIN " +
                    "key written to the PED, but the host has no way to decrypt with it.",
            )
            return
        }
        try {
            tx.lastOnlinePinKeyEncrypted = RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, pinKey)
        } catch (e: GeneralSecurityException) {
            LogUtils.e(TAG, "Failed to RSA-encrypt the online PIN key for the host", e)
        }
    }

    // [SHARED — same method satisfies both interfaces; §08 of the backbone artifact covers how
    // the return-code handoff into this method works for the contact path]
    override fun startOnlineProcess(): OnlineResultWrapper {
        // Right after the 1st GENERATE AC returned the ARQC: onStartOnlineProcess sends it —
        // field 55 (read through readIccTag) and the card data (addOnlineRequestData) attached.
        // Fail closed: if that step doesn't run (the transaction was cancelled), nothing goes to
        // the host and the answer is online failed. The host call blocks right here, on the
        // kernel's stack (PosTerminal.authorize → runBlocking).
        tx.lastAuth = null
        tx.onlineRequested = true
        try {
            goToStep(EmvStep.START_ONLINE_PROCESS, null)
        } finally {
            tx.onlineRequested = false
        }
        if (tx.lastAuth == null) {
            engine().apduTrace().note("EMV", "online step did not run — online failed")
        }
        return toOnlineResultWrapper(tx.lastAuth)
    }

    /** How the host answered, for the ISSUER_AUTHENTICATION banner. */
    private fun issuerAuthenticationDetail(): String {
        val auth = tx.lastAuth ?: return "online failed: no host response"
        return when {
            auth.isApproved -> "host approved"
            auth.isFailed -> "online failed: " + auth.message
            else -> "host declined"
        }
    }

    /**
     * ISSUER_AUTHENTICATION with the host's answer for the card: [onIssuerAuthentication] runs the
     * 2nd GENERATE AC (contact) or the 2nd-tap check (contactless). If the step doesn't run (the
     * transaction was cancelled meanwhile), the card still gets its 2nd GENERATE AC here, as EMV
     * Book 3 requires whatever the host said (see AET-146 below).
     */
    private fun runIssuerAuthentication(online: OnlineResultWrapper) {
        tx.issuerResponse = online
        goToStep(EmvStep.ISSUER_AUTHENTICATION, issuerAuthenticationDetail())
        if (tx.issuerResponse != null) {
            engine().apduTrace().note("EMV", "issuer authentication step did not run — completing with the card anyway")
            completeIssuerAuthentication()
        }
    }

    private fun completeIssuerAuthentication() {
        val online = tx.issuerResponse!!
        tx.issuerResponse = null
        val card = activeCard
        if (card != null && card.isContactless) {
            completeContactlessIssuerAuthentication(online)
        } else {
            completeContactIssuerAuthentication(online)
        }
    }

    /** Field 55 tags come from whichever PAX kernel ran this card — see [readKernelTlv]. */
    override fun readIccTag(tag: Int): ByteArray? {
        val value = readKernelTlv(tag)
        if (tag == TagsTable.AMOUNT_OTHER && value.isEmpty()) {
            // The kernel filled 9F03 with zeros in GENERATE AC (CDOL1) but doesn't return it.
            return amountOtherBcd()
        }
        return value
    }

    /**
     * Chip/contactless only: the PAN, online PIN block and [EmvTransactionResult] read from the
     * kernel, next to the field 55 [authorizeOnline] already attached. Magstripe and manual send
     * the config as it is.
     */
    override fun addOnlineRequestData(config: TransactionConfig): TransactionConfig {
        val card = activeCard
        if (card == null || isSynchronousEntry(card)) {
            return config
        }
        val contactless = card.isContactless
        val pan = if (contactless) getContactlessPan() else getContactPan()
        val track2 = if (contactless) getContactlessTrack2Data() else getContactTrack2Data()
        val cardHolderName = if (contactless) getContactlessCardholderName() else getContactCardholderName()
        val pinBlock = tx.lastOnlinePinBlock
        val onlinePinBlockHex = if (pinBlock != null) ConvertUtils.bcd2Str(pinBlock, pinBlock.size) else null
        return config.withPan(pan)
            .withOnlinePinBlock(onlinePinBlockHex)
            .withOnlinePinKeyEncrypted(tx.lastOnlinePinKeyEncrypted)
            .withEmvResult(
                buildEmvTransactionResult(pan, track2, cardHolderName, config.iccData, onlinePinBlockHex, contactless),
            )
    }

    /**
     * Assembles the full EMV-kernel read right where [startOnlineProcess] is about to go online —
     * vendor-agnostic mirror of the old project's `PosTransactionResult` (there, built from a
     * vendor SDK's EMV callback; here, built from direct kernel TLV reads plus the [TransResult]/
     * [CvmResultEnum] this class already captured earlier in the same stage — see
     * [startContactTransProcess]/[startContactlessTransProcess], both of which run before
     * [startOnlineProcess] is ever reached). See [EmvTransactionResult].
     *
     * [EmvTransactionResult.issuerName] has no real source: this codebase has no
     * BIN-range/issuer-identification table (the old project's own source for one was never
     * shared either), so the EMV Application Preferred Name — falling back to the Application
     * Label — is reused as a best-available proxy; it's usually brand text like "VISA CREDIT" or
     * "MASTERCARD", which is what the old project's `SaleExtraData`/DCC lookups actually keyed off
     * of. Confirm against the server team before relying on this for real issuer routing.
     */
    private fun buildEmvTransactionResult(
        pan: String?,
        track2: String?,
        cardHolderName: String?,
        chipData: String?,
        onlinePinBlockHex: String?,
        contactless: Boolean,
    ): EmvTransactionResult {
        val cvmResult: CvmResultEnum? = if (contactless) {
            tx.clsTransResult?.let { it.cvmResult } ?: CvmResultEnum.CVM_NO_CVM
        } else {
            tx.transResult?.let { it.cvmResult } ?: CvmResultEnum.CVM_NO_CVM
        }
        // An online PIN CVM with no PIN block was bypassed: no PIN.
        val hasPin = onlinePinBlockHex != null || cvmResult == CvmResultEnum.CVM_OFFLINE_PIN

        val cid = readKernelTlvHex(TagsTable.CRYPTO)
        val cidValue = if (!cid.isNullOrEmpty()) cid.toInt(16) else -1
        val isArqc = (cidValue and 0xC0) == 0x80
        val isTc = (cidValue and 0xC0) == 0x40
        val cryptogram = readKernelTlvHex(TagsTable.APP_CRYPTO)

        val expDateRaw = readKernelTlvHex(TagsTable.EXPIRE_DATE)
        val expDate = if (expDateRaw != null && expDateRaw.length >= 4) expDateRaw.substring(0, 4) else null

        val emvAppLabel = readKernelAsciiTlv(TagsTable.APP_LABEL)
        val emvAppName = readKernelAsciiTlv(TagsTable.APP_NAME)
        // The old app's issuerName was the Application Preferred Name ("MasterCard"), not the
        // label ("MASTERCARD").
        val issuerName = emvAppName ?: emvAppLabel

        return EmvTransactionResult.Builder()
            .pan(pan)
            .maskedPan(maskPan(pan))
            .track2(track2)
            .expDate(expDate)
            .cardHolderName(cardHolderName)
            .chipData(chipData)
            .pinBlock(onlinePinBlockHex)
            .emvAppLabel(emvAppLabel)
            .emvAppName(emvAppName)
            .aid(readKernelTlvHex(TAG_SELECTED_AID))
            .tvr(readKernelTlvHex(TagsTable.TVR))
            .tsi(readKernelTlvHex(TagsTable.TSI))
            .atc(readKernelTlvHex(TagsTable.ATC))
            .arqc(if (isArqc) cryptogram else null)
            .tc(if (isTc) cryptogram else null)
            .issuerName(issuerName)
            .hasPin(hasPin)
            .entryMethod(if (contactless) EntryMethod.CONTACTLESS else EntryMethod.CHIP)
            .build()
    }

    /**
     * Unlike [readKernelTlvHex] (numeric/binary tags rendered as hex), `APP_LABEL`/`APP_NAME` are
     * ASCII text per the EMV spec — decoded as such rather than hex-dumped so
     * [EmvTransactionResult.emvAppLabel]/[EmvTransactionResult.emvAppName] carry human-readable
     * text, matching how the old project's `PosTransactionResult` used them (display strings, not
     * hex blobs).
     */
    private fun readKernelAsciiTlv(tag: Int): String? {
        val value = readKernelTlv(tag)
        return if (value.isEmpty()) null else asciiText(value)
    }

    /** [ContactProcess.getTlv]/[ClssProcess.getTlv] both return only value bytes (empty when absent). */
    private fun readKernelTlv(tag: Int): ByteArray {
        val card = activeCard
        if (card != null && card.isContactless) {
            return kernel.contactless.getTlv(tag)
        }
        return kernel.contact.getTlv(tag)
    }

    // [CONTACTLESS]
    override fun onRemoveCard() {
        LogUtils.d(TAG, "remove card")
    }

    // [CONTACTLESS]
    override fun onDetect2ndTap() {
        // Visa issuer update needs the same card tapped a second time — show the card UI again
        // with the reason, like every other re-presentment (KEY_RETRY_PROMPT).
        val prompt = "Tap the same card again to finish"
        engine().apduTrace().note("PICC", "2nd tap requested — $prompt")
        publish(
            TransactionStepEvent.builder(TransactionStep.WAITING_FOR_CARD).message(prompt)
                .put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt).build(),
        )
        goToStep(EmvStep.SEARCH_CARD, prompt)
    }

    // [CONTACTLESS]
    override fun needSeePhone(): Boolean {
        val seePhone = clsLastNeedSeePhone
        clsLastNeedSeePhone = false
        return seePhone
    }

    // ─── Result reporting — no longer an interface contract (IContactResultListener /
    // IContactlessResultListener were never registered with anything; PAX's kernel only
    // recognizes IContactCallback/IContactlessCallback), just plain methods this class calls
    // on itself from checkContactResult()/checkContactlessResult() below. Tags below are
    // verified against those two methods directly, not assumed from the name. ────────────

    // [SHARED — both checkContactResult() and checkContactlessResult() call this]
    // Online-only terminal: nothing is approved without the host. Every kernel "approved offline"
    // is sent to the host first (needsHostWithoutArqc), so reaching this means it wasn't —
    // report online failed, never approved.
    @Suppress("UNUSED_PARAMETER")
    fun offlineApproved(needSignature: Boolean) {
        LogUtils.e(TAG, "Offline approval reached without the host — reporting online failed")
        engine().apduTrace().note("EMV", "offline approval without the host — online failed")
        finishWithOutcome(TransactionStep.ERROR, "Online Failed", "Online Failed: not sent to the host")
    }

    // [SHARED — 2-arg overload; contact's checkContactResult() calls this one specifically]
    @Suppress("UNUSED_PARAMETER")
    fun offlineApproved(needSignature: Boolean, needSetARC: Boolean) {
        offlineApproved(needSignature)
    }

    // [SHARED]
    @Suppress("UNUSED_PARAMETER")
    fun onlineApproved(needSignature: Boolean) {
        completeApproved("RESULT_ONLINE_APPROVED", true)
    }

    // [SHARED]
    fun onlineDenied() {
        goToStep(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer")
        completeDeclined("Online Denied")
    }

    // [SHARED]
    fun onlineCardDenied(resultCode: Int) {
        goToStep(EmvStep.ISSUER_AUTHENTICATION, "declined by card")
        completeDeclined("Online Card Denied code=$resultCode")
    }

    // [SHARED]
    fun onlineFailed() {
        val auth = tx.lastAuth
        val reason = if (auth != null && auth.isFailed && auth.message != null) {
            "Online Failed: " + auth.message
        } else {
            "Online Failed: no host response"
        }
        finishWithOutcome(TransactionStep.ERROR, "Online Failed", reason)
    }

    // [SHARED]
    fun offlineDenied(resultCode: Int) {
        completeDeclined("Offline Denied code=$resultCode")
    }

    /**
     * "See Phone" outcome (Mastercard message 0x20, Visa CLSS_REFER_CONSUMER_DEVICE): the phone
     * wants the cardholder to verify on it (unlock / Face ID / fingerprint) and tap again. Not a
     * decline — EMV contactless Book A's "end application with restart": show the prompt and
     * restart the same transaction, contactless only, waiting for the re-tap. Shares
     * [MAX_TAP_RETRIES] with the other tap-again retries, so a phone that keeps asking still ends
     * in a decline.
     */
    // [CONTACTLESS — only checkContactlessResult() calls this; no CDCVM concept in contact]
    fun seePhone() {
        val retries = engine().getRetryCount()
        if (retries >= MAX_TAP_RETRIES) {
            engine().apduTrace().note("PICC", "SEE PHONE again after $retries retries — declining")
            completeDeclined("See Phone: verification on the phone not completed")
            return
        }
        engine().apduTrace().note("PICC", "SEE PHONE — verify on the phone, then tap again")
        retryWithMode(
            EntryMethod.CONTACTLESS, "See Phone: waiting for the re-tap",
            "See phone — verify on your phone, then tap again",
        )
    }

    /** Scheme declined the contactless attempt outright (e.g. low-value rules) — retry contact. */
    // [CONTACTLESS — only checkContactlessResult() calls this]
    fun tryAnotherInterface() {
        retryWithMode(EntryMethod.CHIP, "Try Another Interface: retrying with contact", "Insert the card")
    }

    /** Incomplete/glitchy tap (card pulled early, read error) — re-present the same interface. */
    // [CONTACTLESS — only checkContactlessResult() calls this]
    fun tryAgain() {
        val retries = engine().getRetryCount()
        if (retries >= MAX_TAP_RETRIES) {
            // A card that always fails ends in a decline, not an endless "tap again".
            LogUtils.w(TAG, "Try Again after $retries retries — declining")
            engine().apduTrace().note("PICC", "card not read after $retries retries — declining")
            completeDeclined("Card not read — tried ${retries + 1} times")
            return
        }
        val mode = activeConfig?.mode ?: EntryMethod.ANY
        retryWithMode(mode, "Try Again: re-presenting card", "Card not read — tap again and hold the card still")
    }

    /** Chip read failed in a way EMV fallback rules require — retry magstripe. */
    // [CONTACT — only checkContactResult() calls this]
    fun fallback() {
        retryWithMode(EntryMethod.MAGSTRIPE, "Fallback: retrying with magstripe", "Chip not readable — swipe the card")
    }

    /**
     * Requests a same-transaction retry through [EmvEngine.requestRetry] — never a new
     * `startTransaction()` call, so it can't race the attempt that's still unwinding.
     */
    private fun retryWithMode(mode: EntryMethod, reason: String, prompt: String) {
        val config = activeConfig
        if (config == null) {
            finishError("$reason — no active transaction")
            return
        }
        LogUtils.i(TAG, reason)
        engine().requestRetry(config.withMode(mode), prompt)
    }

    // [SHARED — both checkContactResult() and checkContactlessResult() call this]
    fun simpleFlowEnd() {
        completeApproved("RESULT_SIMPLE_FLOW_END", false)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun completeApproved(result: String, scripts: Boolean) {
        if (scripts && !tx.scriptsProcessed) {
            goToStep(EmvStep.SCRIPT_PROCESSING, null)
        }
        finishWithOutcome(TransactionStep.APPROVED, result, result)
    }

    private fun completeDeclined(reason: String) {
        finishWithOutcome(TransactionStep.DECLINED, reason, reason)
    }

    /**
     * Ends a chip/contactless transaction through its TRANSACTION_COMPLETION step:
     * [onTransactionCompletion] reports the outcome. A transaction that has already ended (an
     * error earlier in the chain) drops the step, so it never reports a second outcome.
     */
    private fun finishWithOutcome(step: TransactionStep, stepDetail: String, message: String) {
        tx.outcome = Outcome(step, message)
        goToStep(EmvStep.TRANSACTION_COMPLETION, stepDetail)
        tx.outcome = null
    }

    private fun reportOutcome(outcome: Outcome) {
        when (outcome.step) {
            TransactionStep.APPROVED -> finishApproved(
                buildOutcomeEvent(TransactionStep.APPROVED, TransactionStepEvent.KEY_RESULT, outcome.message),
            )
            TransactionStep.DECLINED -> finishDeclined(
                buildOutcomeEvent(TransactionStep.DECLINED, TransactionStepEvent.KEY_ERROR, outcome.message),
            )
            else -> finishError(outcome.message)
        }
    }

    /**
     * Builds the APPROVED/DECLINED event the String-only [finishApproved] / [finishDeclined]
     * would build, plus the audit snapshot [captureTransactionSummary] stashed earlier; it goes
     * through their event overloads. Mag/manual use the String ones (see
     * [onTransactionCompletion]) — they never call [captureTransactionSummary], so their event
     * simply has no PAN/TVR/TAC/IAC/Field 55 to attach.
     */
    private fun buildOutcomeEvent(step: TransactionStep, messageKey: String, message: String): TransactionStepEvent =
        TransactionStepEvent.builder(step)
            .put(messageKey, message)
            .put(TransactionStepEvent.KEY_PAN, safe(tx.lastPan))
            .put(TransactionStepEvent.KEY_TVR, safe(tx.lastTvr))
            .put(TransactionStepEvent.KEY_TAC_DENIAL, safe(tx.lastTacDenial))
            .put(TransactionStepEvent.KEY_IAC_DENIAL, safe(tx.lastIacDenial))
            .put(TransactionStepEvent.KEY_ICC_DATA, safe(tx.lastIccData))
            .build()

    private fun resetTransactionSummary() {
        tx.lastPan = null
        tx.lastTvr = null
        tx.lastTacDenial = null
        tx.lastIacDenial = null
        tx.lastIccData = null
    }

    /**
     * Captures PAN/TVR/TAC Denial/IAC Denial/Field 55 while the kernel session is still open —
     * right after the bundled EMVStartTrans/startTransProcess call returns, before
     * [closeReaders]. Runs for every chip/contactless outcome, online or offline, approved or
     * declined; [buildOutcomeEvent] reads these fields once `checkContactResult()`/
     * `checkContactlessResult()` maps the result to [completeApproved]/[completeDeclined].
     */
    private fun captureTransactionSummary() {
        val card = activeCard
        val contactless = card != null && card.isContactless
        tx.lastPan = safe(if (contactless) getContactlessPan() else getContactPan())
        tx.lastTvr = readKernelTlvHex(TagsTable.TVR)
        tx.lastTacDenial = readKernelTlvHex(TagsTable.TAC_DENIAL)
        tx.lastIacDenial = readKernelTlvHex(TagsTable.IAC_DENIAL)
        // startOnlineProcess() (called from inside startXxxTransProcess for an online outcome)
        // already built and attached this to activeConfig — reuse it instead of re-reading the
        // same kernel tags a second time. Offline outcomes never call it, so build fresh here.
        tx.lastIccData = activeConfig?.iccData ?: buildField55()
    }

    private fun readKernelTlvHex(tag: Int): String? {
        val value = readKernelTlv(tag)
        return if (value.isEmpty()) null else ConvertUtils.bcd2Str(value, value.size)
    }

    /**
     * Closes mag/icc/picc independently — each in its own try/catch — so one reader's close
     * failing (e.g. a reader already in a bad state) can't skip the rest. The previous single-try
     * version stopped at the first exception, silently leaving whatever came after it open;
     * that's the likely root cause of a "reader already open" failure on the next attempt.
     */
    private fun closeReaders(contactless: Boolean) {
        val dal = EmvFlowRuntime.getDal() ?: return
        try {
            dal.mag.close()
        } catch (e: Exception) {
            LogUtils.e(TAG, "close mag reader failed", e)
        }
        try {
            dal.icc.close(0.toByte())
        } catch (e: Exception) {
            LogUtils.e(TAG, "close icc reader failed", e)
        }
        try {
            dal.getPicc(EPiccType.INTERNAL).close()
        } catch (e: Exception) {
            LogUtils.e(TAG, "close internal picc reader failed", e)
        }
        if (contactless) {
            try {
                dal.getPicc(EPiccType.EXTERNAL).close()
            } catch (e: Exception) {
                LogUtils.e(TAG, "close external picc reader failed", e)
            }
        }
    }

    private companion object {
        const val TAG = "PaxEmvBehavior"

        /** See [startContactlessTransProcess]'s doc for why this is a constant. */
        const val CONTACTLESS_FLOW_TYPE = EmvTransParam.FLOWTYPE_COMPLETE

        /** Tap-again retries after a contactless communication error before the sale is declined. */
        const val MAX_TAP_RETRIES = 3

        /**
         * Retry budget for [selectApplicationWithRetry] — restores tuning a previous session
         * (commit `aa200e0`) arrived at on real hardware for native `IccException: 51` / DAL
         * `ICC#97` ("no ATR" — chip not seated, upside-down, or a mag-only card in the hybrid
         * slot): power-cycle the slot, wait for VCC to settle, retry a bounded number of times.
         */
        const val ICC_APP_SELECT_MAX_ATTEMPTS = 3
        const val ICC_POWER_SETTLE_MS = 200L

        /**
         * AES-128 key length — see [provisionOnlinePinKey] (`EPedKeyType.AES_TPK`, not the
         * 3DES-family plain `TPK`).
         */
        const val ONLINE_PIN_KEY_LENGTH_BYTES = 16

        /**
         * DF Name (selected AID) — not in [TagsTable], confirmed by grep; kept local rather than
         * added there since nothing else in this codebase reads it yet. See
         * [buildEmvTransactionResult].
         */
        const val TAG_SELECTED_AID = 0x84

        /**
         * Amount, Other (9F03): no cashback, so always zero. The kernels get it in the trans
         * param only. It is never written into a kernel as a tag: the contact kernel then took
         * the sale for cashback in its Application Usage Control check (TVR byte 2 b5, "requested
         * service not allowed") and declined offline any card whose IAC-Denial has that bit (a
         * 650.00 EGP Visa sale, AUC FF00). The kernel still puts zeros in GENERATE AC when CDOL1
         * asks for 9F03 but doesn't return it when read back, so [readIccTag] answers zeros for
         * field 55, which the host needs to check the ARQC.
         */
        const val AMOUNT_OTHER_MINOR = 0L

        /**
         * Transaction Sequence Counter (9F41): always 0, as the old app sent it. Its field 55 with
         * 9F41 = 00000000 is the one the host approved.
         */
        const val TRANSACTION_SEQUENCE_COUNTER = 0L

        /** `PinException#getErrCode` when the PIN pad's Enter was pressed with no digits. */
        val NO_PIN_INPUT: String = EPedDevException.PED_ERR_NO_PIN_INPUT.errCodeFromBasement.toString()

        fun amountOtherBcd(): ByteArray =
            ConvertUtils.strToBcdPaddingLeft(String.format(Locale.US, "%012d", AMOUNT_OTHER_MINOR))

        /**
         * Debug builds always show the dialog, even for a single candidate, so it can be exercised
         * on any test card instead of needing a real dual-AID one — never in release, where a lone
         * application should keep auto-selecting with no extra tap.
         */
        fun showsAppSelectDialog(candList: List<CandidateAID>?): Boolean {
            val candidates = candList?.size ?: 0
            return candidates >= 2 || (BuildConfig.DEBUG && candidates >= 1)
        }

        /** The host's answer as the transaction result, for a card that gave no ARQC to complete. */
        fun hostOutcome(online: OnlineResultWrapper, cvmResult: CvmResultEnum?): TransResult {
            val outcome = online.transResultEnum
            val code = if (outcome == TransResultEnum.RESULT_ONLINE_APPROVED) RetCode.EMV_OK else outcome.ordinal
            return TransResult(code, outcome, cvmResult ?: CvmResultEnum.CVM_NO_CVM)
        }

        fun asciiText(value: ByteArray): String = String(value, Charsets.US_ASCII).trim()

        fun eraseKeyQuietly(ped: IPed, keyType: EPedKeyType, index: Byte) {
            try {
                ped.eraseKey(keyType.pedkeyType, index)
            } catch (e: PedDevException) {
                LogUtils.i(TAG, "No " + keyType + " to erase at index " + index + ": " + e.errMsg)
            }
        }

        /** `null` pan (kernel hasn't read one) masks to `null` rather than throwing. */
        fun maskPan(pan: String?): String? {
            if (pan == null || pan.length <= 10) {
                return pan
            }
            val visibleTail = 4
            val visibleHead = 6
            val masked = StringBuilder(pan.length)
            masked.append(pan, 0, visibleHead)
            for (i in visibleHead until pan.length - visibleTail) {
                masked.append('*')
            }
            masked.append(pan.substring(pan.length - visibleTail))
            return masked.toString()
        }

        fun toOnlineResultWrapper(auth: AuthResult?): OnlineResultWrapper {
            val wrapper = OnlineResultWrapper()
            val rsp = IssuerRspData()
            if (auth == null || auth.isFailed) {
                wrapper.resultCode = EOnlineResult.FAILED.resultCode
                wrapper.transResultEnum = TransResultEnum.RESULT_ONLINE_FAILED
                rsp.onlineResult = EOnlineResult.FAILED.emvOnlineResult
                wrapper.issuerRspData = rsp
                return wrapper
            }
            if (auth.isApproved) {
                wrapper.resultCode = EOnlineResult.APPROVE.resultCode
                wrapper.transResultEnum = TransResultEnum.RESULT_ONLINE_APPROVED
                rsp.onlineResult = EOnlineResult.APPROVE.emvOnlineResult
                auth.authCode?.let { rsp.authCode = it.toByteArray(Charsets.US_ASCII) }
                auth.responseCode?.let { rsp.respCode = it.toByteArray(Charsets.US_ASCII) }
                auth.arpc?.let { rsp.authData = it }
                auth.issuerScript?.let { rsp.script = it }
            } else {
                wrapper.resultCode = EOnlineResult.DENIAL.resultCode
                wrapper.transResultEnum = TransResultEnum.RESULT_ONLINE_DENIED
                rsp.onlineResult = EOnlineResult.DENIAL.emvOnlineResult
                auth.responseCode?.let { rsp.respCode = it.toByteArray(Charsets.US_ASCII) }
            }
            wrapper.issuerRspData = rsp
            return wrapper
        }

        fun timestamp(): String = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())

        fun safe(s: String?): String = s ?: ""

        fun panFromTrack2(track2: String): String {
            var sep = track2.indexOf('=')
            if (sep <= 0) {
                sep = track2.indexOf('D')
            }
            return if (sep > 0) track2.substring(0, sep) else track2
        }
    }
}

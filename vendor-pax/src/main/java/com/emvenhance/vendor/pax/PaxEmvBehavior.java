package com.emvenhance.vendor.pax;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.emv.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.EmvEngine;
import com.emvenhance.core.emv.EmvStep;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.cardholder.AbstractAppSelectDialog;
import com.emvenhance.core.terminal.AbstractEmvBehavior;
import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.emvenhance.network.crypto.RsaPublicKeyEncryptor;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.settings.PinBypassSettings;
import com.pax.configservice.impl.EmvParamService;
import com.pax.bizentity.entity.SearchMode;
import com.pax.bizlib.card.TrackUtils;
import com.pax.bizlib.ped.PedHelper;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IPed;
import com.pax.dal.entity.EAesCheckMode;
import com.pax.dal.entity.EPedKeyType;
import com.pax.dal.entity.EPiccType;
import com.pax.dal.exceptions.EPedDevException;
import com.pax.dal.exceptions.PedDevException;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.EmvTransParam;
import com.emvenhance.vendor.pax.PaxTransactionState.Outcome;
import com.emvenhance.vendor.pax.PaxTransactionState.PinRequest;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.entity.TransResult;
import com.pax.emvbase.process.enums.CvmResultEnum;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvlib.dpas.contact.ContactProcess;
import com.pax.emvlib.process.contactless.ClssProcess;
import com.pax.emvservice.emv.pin.PinService;
import com.pax.emvservice.export.exceptions.PinException;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.device.DeviceManager;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.utils.PosDeviceUtils;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;


/**
 * PAX vendor EMV behavior on {@link AbstractEmvBehavior}.
 *
 * <ul>
 *   <li>Mag / manual: sync {@link #goToStep} chain (same pattern as Fake).</li>
 *   <li>Chip: {@link #onApplicationSelection} drives the PAX kernel one native call at a time,
 *       through the very same {@link #goToStep} engine that drives mag/manual — each stage's
 *       run* method ({@link #runContactAppSelect} → {@link #runContactReadAppData} →
 *       {@link #runContactSetTransactionData} → {@link #runContactCardAuth} →
 *       {@link #runContactStartTransaction}) advances by calling
 *       {@code goToStep(EmvStep.NEXT)} rather than the next run* method directly, so
 *       {@code goToStep} both publishes that step on the observable and dispatches to its onXxx
 *       method (e.g. {@link #onReadApplicationData}), which hands straight back to the matching
 *       run* method for chip. Chaining continues only while
 *       {@link #isContactTransactionFinished()} stays false; APPLICATION_SELECTION itself is
 *       entered the normal way, via the framework's own goToStep call that invoked
 *       {@link #onApplicationSelection} in the first place. Sub-phases with no kernel callback
 *       and no dedicated stage of their own (terminal risk management, terminal action analysis
 *       — both folded into {@link #runContactStartTransaction}'s EMVStartTrans call) still can't
 *       be published individually; CVM is only published when the kernel actually asks for it,
 *       via {@code onCardHolderPwd} below. Contactless: a granular kernel (EFT, PayPass,
 *       PayWave — see {@code ClssKernelProcess#supportsGranularSteps()}) runs the same way, one
 *       stage per step, in its own kernel's order; an atomic kernel runs in one call and its
 *       steps are published from kernel callbacks, their onXxx having nothing left to run.</li>
 * </ul>
 *
 * <p><b>Experiment branch:</b> talks to {@link ContactProcess} (emvlib:dpas) directly — no
 * {@code EmvContactService} service layer. Everything that used to live there — per-transaction
 * state ({@link PaxTransactionState#transResult}, {@link PaxTransactionState#cachedTrack2Data}), the online-processing decision inside
 * {@link #startContactTransProcess}, PAN/track2 derivation ({@link #getContactPan}), and the
 * result-to-callback mapping in {@link #checkContactResult()} — now lives here instead. The
 * contactless path got the same treatment: talks to {@link ClssProcess} (emvlib) directly — no
 * {@code ContactlessService} layer — with its own mirrored state
 * ({@link PaxTransactionState#clsTransResult}/{@link PaxTransactionState#clsCachedTrack2Data}/{@link #clsLastNeedSeePhone}), online
 * decision ({@link #startContactlessTransProcess}), PAN derivation
 * ({@link #getContactlessPan}), and result mapping ({@link #checkContactlessResult()}). See the
 * structure diagram (panel 10/11) for why this was tried and what it cost.
 */
public class PaxEmvBehavior extends AbstractEmvBehavior
        implements IContactCallback, IContactlessCallback {

    private static final String TAG = "PaxEmvBehavior";

    /** See {@link #startContactlessTransProcess} javadoc for why this is a constant. */
    private static final int CONTACTLESS_FLOW_TYPE = EmvTransParam.FLOWTYPE_COMPLETE;

    /** Tap-again retries after a contactless communication error before the sale is declined. */
    private static final int MAX_TAP_RETRIES = 3;

    /**
     * Retry budget for {@link #selectApplicationWithRetry} — restores tuning a previous session
     * (commit {@code aa200e0}) arrived at on real hardware for native {@code IccException: 51} /
     * DAL {@code ICC#97} ("no ATR" — chip not seated, upside-down, or a mag-only card in the
     * hybrid slot): power-cycle the slot, wait for VCC to settle, retry a bounded number of times.
     */
    private static final int ICC_APP_SELECT_MAX_ATTEMPTS = 3;
    private static final long ICC_POWER_SETTLE_MS = 200L;

    /**
     * AES-128 key length — see {@link #provisionOnlinePinKey} ({@code EPedKeyType.AES_TPK}, not
     * the 3DES-family plain {@code TPK}).
     */
    private static final int ONLINE_PIN_KEY_LENGTH_BYTES = 16;

    /**
     * DF Name (selected AID) — not in {@link TagsTable}, confirmed by grep; kept local rather than
     * added there since nothing else in this codebase reads it yet. See
     * {@link #buildEmvTransactionResult}.
     */
    private static final int TAG_SELECTED_AID = 0x84;

    private final PaxKernel kernel;

    /** The SEARCH_CARD step's polling; stops once the transaction is over. */
    private final PaxCardSearch cardSearch;

    /**
     * See Phone on a tap: the kernel asks for it ({@link #needSeePhone}) on the next tap, which is
     * a retry — a new attempt — so unlike {@link PaxTransactionState} it carries over.
     */
    private boolean clsLastNeedSeePhone;

    /** What this behavior learns during one attempt — see {@link PaxTransactionState}. */
    private volatile PaxTransactionState tx = new PaxTransactionState();

    public PaxEmvBehavior(PaxKernel kernel) {
        this.kernel = kernel;
        this.cardSearch = new PaxCardSearch(kernel, () -> !isRunning());
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public void onTerminalInitialization(TransactionConfig config) {
        // The first step of every attempt: a fresh state, so nothing from an earlier sale or
        // attempt carries over — its online PIN block (and RSA-wrapped key) sent again with this
        // sale, flagged as online PIN; or its kernel result read as this one's.
        tx = new PaxTransactionState();
        // The kernel calls EmvDeviceImpl directly, so hand it this transaction's APDU trace.
        EmvDeviceImpl.getInstance().setApduTrace(engine().apduTrace());
        if (!prepareKernel(config)) {
            finishError("Terminal initialization failed");
        }
        // Do not goToStep — the card search (SEARCH_CARD) is the base class's next step.
    }

    /** Polls the PAX readers ({@link PaxCardSearch}); the card found runs the EMV from its callback. */
    @Override
    public void onSearchCard(TransactionConfig config, @Nullable CardPresence card) {
        cardSearch.search(config, cardSearchListener(), engine().apduTrace());
    }

    @Override
    public void cancelCardSearch() {
        cardSearch.stop();
    }

    // firstStepAfterSearch: no override needed — AbstractEmvBehavior's default already does
    // exactly this (isSynchronousEntry → READ_APPLICATION_DATA, else APPLICATION_SELECTION,
    // which starts the PAX kernel for chip/CLSS).

    // ─── Step methods (goToStep / kernel start) ──────────────────────────

    @Override
    public void onApplicationSelection(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            runContactAppSelect();
            return;
        }
        if (card.isContactless()) {
            runContactlessAppSelect();
            return;
        }
        // Mag/manual should not land here
        goToStep(EmvStep.READ_APPLICATION_DATA);
    }

    @Override
    public void onReadApplicationData(TransactionConfig config, CardPresence card) {
        if (card.isManual()) {
            String pan = card.getManualPan() != null ? card.getManualPan() : "";
            if (pan.isEmpty()) {
                finishError("Manual entry requires a PAN");
                return;
            }
            publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MANUAL")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .build());
            goToStep(EmvStep.START_ONLINE_PROCESS, "manual");
            return;
        }
        if (card.isMagstripe()) {
            String track2 = card.getTrack2() != null ? card.getTrack2() : "";
            String pan = panFromTrack2(track2);
            publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MAG")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .build());
            goToStep(EmvStep.START_ONLINE_PROCESS, "magstripe");
            return;
        }
        if (card.isChip()) {
            // Reached via runContactAppSelect's goToStep(READ_APPLICATION_DATA) — never via the
            // framework's own cancellation short-circuit in goToStep, which returns before
            // dispatchStepMethod runs at all.
            runContactReadAppData();
        }
        if (card.isContactless() && kernel.contactless.supportsGranularSteps()) {
            // Reached via runContactlessAppSelect's goToStep(READ_APPLICATION_DATA) — only a
            // kernel whose SDK exposes this as its own call (see
            // ClssKernelProcess#supportsGranularSteps()). An atomic kernel publishes this step
            // from onReadCardOk() after reading the data itself: nothing to run then.
            runContactlessReadAppData();
        }
    }

    @Override
    public void onStartOnlineProcess(TransactionConfig config, CardPresence card) {
        // Mag / manual sync path
        if (isSynchronousEntry(card)) {
            tx.lastAuth = authorizeOnline();
            goToStep(EmvStep.TRANSACTION_COMPLETION);
            return;
        }
        // Chip/CLSS: asked by startOnlineProcess() after the 1st GENERATE AC — the host round
        // trip runs here, in its own step.
        if (tx.onlineRequested) {
            tx.lastAuth = authorizeOnline();
        }
    }

    @Override
    public void onTransactionCompletion(TransactionConfig config, CardPresence card) {
        // Mag / manual sync path
        if (isSynchronousEntry(card)) {
            if (tx.lastAuth != null && tx.lastAuth.isApproved()) {
                String msg = card.isManual() ? "MANUAL ONLINE APPROVED" : "MAG ONLINE APPROVED";
                finishApproved(msg);
            } else {
                finishDeclined(tx.lastAuth != null && tx.lastAuth.getMessage() != null
                        ? tx.lastAuth.getMessage() : "DECLINED");
            }
            return;
        }
        // Chip/CLSS: the outcome checkContactResult / checkContactlessResult decided
        // (finishWithOutcome) — reported here, in its own step.
        Outcome outcome = tx.outcome;
        tx.outcome = null;
        if (outcome != null) {
            reportOutcome(outcome);
        }
    }

    // Chip: reached via goToStep from the previous run* stage — dispatchStepMethod invokes
    // these, and each hands off to its run* method. See the class doc and runContactAppSelect
    // for the full chain.

    @Override
    public void onOfflineDataAuthentication(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            // Reached via runContactSetTransactionData's goToStep(OFFLINE_DATA_AUTHENTICATION).
            runContactCardAuth();
        }
        if (card.isContactless()) {
            // Reached via runContactlessReadAppData's goToStep(OFFLINE_DATA_AUTHENTICATION), or
            // for PayWave via runContactlessProcessRestrictions' (its kernel's order).
            runContactlessCardAuth();
        }
    }

    @Override
    public void onProcessRestrictions(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            // Reached via runContactCardAuth's goToStep(PROCESS_RESTRICTIONS) — the entry point
            // into runContactStartTransaction's bundled EMVStartTrans call (see class doc).
            runContactStartTransaction();
        }
        if (card.isContactless()) {
            // Reached via runContactlessCardAuth's goToStep(PROCESS_RESTRICTIONS) for a granular
            // kernel (via runContactlessReadAppData's for PayWave, whose restrictions come
            // before offline data auth), or directly from runContactlessAppSelect for an atomic
            // one, which goes straight to runContactlessStartTransaction's bundled call.
            runContactlessProcessRestrictions();
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

    @Override
    public void onWaitApplicationSelection(TransactionConfig config, CardPresence card) {
        // Asked by IContactCallback#onWaitAppSelect below: the kernel waits on this answer.
        answerApplicationSelection();
    }

    @Override
    public void onFinalApplicationSelection(TransactionConfig config, CardPresence card) {
        // Same as onWaitApplicationSelection: the kernel's retry after a failed final select.
        answerApplicationSelection();
    }

    /**
     * The cardholder's choice among the kernel's candidates ({@link PaxTransactionState#candidates}),
     * answered to {@link #onWaitAppSelect}'s {@code askStep}: index 0 with nothing to choose
     * between, else the dialog's choice.
     */
    private void answerApplicationSelection() {
        List<CandidateAID> candList = tx.candidates;
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        if (!showsAppSelectDialog(candList)) {
            // Nothing for the cardholder to choose between — index 0 either selects the card's
            // only application, or (candList empty/null) is the safe default the kernel expects.
            answerStep(EmvConstant.ContactCallbackStatus.CONTACT_OK);
            return;
        }
        int index = new PaxAppSelectDialog().selectBlocking(
                PaxAppSelectDialog.labels(candList), () -> !isRunning());
        if (index == AbstractAppSelectDialog.NO_ANSWER) {
            // Not answered: askStep gives the kernel USER_CANCEL and notes it in the trace.
            LogUtils.i(TAG, "Application not selected (timed out, cancelled or no screen)");
            return;
        }
        answerStep(index);
    }

    /**
     * Debug builds always show the dialog, even for a single candidate, so it can be exercised on
     * any test card instead of needing a real dual-AID one — never in release, where a lone
     * application should keep auto-selecting with no extra tap.
     */
    private static boolean showsAppSelectDialog(@Nullable List<CandidateAID> candList) {
        int candidates = candList == null ? 0 : candList.size();
        return candidates >= 2 || (BuildConfig.DEBUG && candidates >= 1);
    }

    @Override
    public void onSetTransactionData(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            // Reached via runContactReadAppData's goToStep(SET_TRANSACTION_DATA): the contact
            // kernel takes the data after reading the card.
            runContactSetTransactionData();
        }
        // Granular contactless kernels (EFT, PayPass, PayWave): this stage gives the kernel the
        // transaction data, before READ_APPLICATION_DATA's GPO needs it for the PDOL.
        if (card.isContactless() && kernel.contactless.supportsGranularSteps()) {
            runContactlessSetTransactionData();
        }
        // Atomic contactless kernels take the data inside their own call; the step is
        // published from IContactlessCallback#confirmCard below.
    }

    @Override
    public void onCardholderVerification(TransactionConfig config, CardPresence card) {
        // Asked by onCardHolderPwd below for an online PIN; for an offline PIN this step only
        // announces CVM and OFFLINE_PIN_VERIFICATION is the one asked.
        PinRequest request = tx.pinRequest;
        if (request != null && request.online) {
            answerStep(enterPin(request));
        }
    }

    @Override
    public void onOfflinePinVerification(TransactionConfig config, CardPresence card) {
        // Asked by onCardHolderPwd below for an offline PIN: show the pad, answer CONTACT_OK —
        // the kernel then verifies the PIN with the card itself.
        PinRequest request = tx.pinRequest;
        if (request != null && !request.online) {
            answerStep(enterPin(request));
        }
    }

    @Override
    public void onTerminalRiskManagement(TransactionConfig config, CardPresence card) {
        // Kernel-internal — no callback exists, so it is never published on its own.
    }

    @Override
    public void onTerminalActionAnalysis(TransactionConfig config, CardPresence card) {
        // Kernel-internal — no callback exists, so it is never published on its own.
    }

    @Override
    public void onIssuerAuthentication(TransactionConfig config, CardPresence card) {
        // Asked by runIssuerAuthentication with the host's answer: the card's 2nd GENERATE AC
        // (contact) or the 2nd-tap check (contactless). With no ARQC to complete, or a
        // contactless sale the host didn't approve, the card has nothing to do: no step.
        if (tx.issuerResponse != null) {
            completeIssuerAuthentication();
        }
    }

    @Override
    public void onScriptProcessing(TransactionConfig config, CardPresence card) {
        // Contactless 2nd tap: asked by runIssuerScriptProcessing — the kernel's script call
        // (PayWave's Clss_IssScriptProc_Wave). Contact: EMVCompleteTrans already applied them,
        // and completeApproved() announces the step with nothing to run.
        if (tx.pendingScripts != null) {
            processIssuerScripts();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Kernel runners
    // ═══════════════════════════════════════════════════════════════════

    // ─── CONTACTLESS ONLY — ClssProcess / ClssKernelProcess runners ──────

    private void runContactlessAppSelect() {
        boolean proceed = false;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            LogUtils.d(TAG, "============ Contactless EMV: App Select ============");
            // reset every transaction
            tx.clsCachedTrack2Data = null;
            resetTransactionSummary();
            kernel.contactless.registerClssProcessListener(this);
            tx.clsTransResult = kernel.contactless.selectApplication();
            int ret = tx.clsTransResult.getResultCode();
            LogUtils.d(TAG, "selectApplication ret=" + ret);
            markTryAgainOnCommunicationError(ret);
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless app select failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contactless EMV app select failed");
        } finally {
            if (!proceed) {
                finishContactlessStage();
            }
        }
        if (proceed) {
            // EFT/PayPass/PayWave expose set data/read/offline-auth/restrictions as their own
            // calls; the other seven brands' SDKs bundle the whole transaction into one call
            // instead — see ClssKernelProcess#supportsGranularSteps().
            advanceContactlessStage(firstStageAfterAppSelect());
        }
    }

    /**
     * A granular stage that ends the transaction after the card was read (a decline, See Phone,
     * an error) keeps the same summary — PAN, TVR, TACs/IACs, field 55 — that
     * {@link #runContactlessStartTransaction} records when the kernel's one call ends it.
     */
    private void captureSummaryAfterCardRead() {
        try {
            captureTransactionSummary();
        } catch (Exception e) {
            LogUtils.e(TAG, "transaction summary failed", e);
        }
    }

    /** A granular kernel starts with its set-data stage; an atomic one goes to its one call. */
    private EmvStep firstStageAfterAppSelect() {
        return kernel.contactless.supportsGranularSteps()
                ? EmvStep.SET_TRANSACTION_DATA
                : EmvStep.PROCESS_RESTRICTIONS;
    }

    /**
     * A granular stage got {@code CLSS_RESELECT_APP}: delete the candidate and select the next
     * one ({@link ClssProcess#reselectApplication()}), as {@code ClssProcess.startTransProcess}'s
     * loop does for the atomic kernels. Returns the selection's result code; the caller then
     * starts the new candidate from {@link #firstStageAfterAppSelect()}.
     */
    private int reselectApplication() {
        LogUtils.d(TAG, "CLSS_RESELECT_APP — selecting the next candidate");
        engine().apduTrace().note("EMV", "kernel asked to reselect — next application");
        tx.clsTransResult = kernel.contactless.reselectApplication();
        int ret = tx.clsTransResult.getResultCode();
        LogUtils.d(TAG, "reselectApplication ret=" + ret);
        return ret;
    }

    /** Granular kernels only: kernel init and the transaction data, then READ_APPLICATION_DATA. */
    private void runContactlessSetTransactionData() {
        boolean proceed = false;
        boolean reselected = false;
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Set Transaction Data ============");
            tx.clsTransResult = kernel.contactless.setTransactionData();
            int ret = tx.clsTransResult.getResultCode();
            LogUtils.d(TAG, "setTransactionData ret=" + ret);
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true;
                ret = reselectApplication();
            }
            markTryAgainOnCommunicationError(ret);
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless set transaction data failed", e);
            finishError(e.getMessage() != null ? e.getMessage()
                    : "Contactless EMV set transaction data failed");
        } finally {
            if (!proceed) {
                finishContactlessStage();
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect());
            } else {
                advanceContactlessStage(EmvStep.READ_APPLICATION_DATA);
            }
        }
    }

    /** Read App Data stage — reached via {@link #onReadApplicationData}'s contactless branch. */
    private void runContactlessReadAppData() {
        boolean proceed = false;
        boolean reselected = false;
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Read App Data ============");
            tx.clsTransResult = kernel.contactless.readApplicationData();
            int ret = tx.clsTransResult.getResultCode();
            LogUtils.d(TAG, "readApplicationData ret=" + ret);
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true;
                ret = reselectApplication();
            }
            markTryAgainOnCommunicationError(ret);
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless read app data failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contactless EMV read app data failed");
        } finally {
            if (!proceed) {
                captureSummaryAfterCardRead();
                finishContactlessStage();
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect());
            } else {
                advanceContactlessStage(kernel.contactless.restrictionsBeforeOfflineDataAuth()
                        ? EmvStep.PROCESS_RESTRICTIONS
                        : EmvStep.OFFLINE_DATA_AUTHENTICATION);
            }
        }
    }

    /**
     * Offline Data Authentication stage — reached via {@link #onOfflineDataAuthentication}'s
     * contactless branch.
     */
    private void runContactlessCardAuth() {
        boolean proceed = false;
        boolean reselected = false;
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Offline Data Authentication ============");
            tx.clsTransResult = kernel.contactless.offlineDataAuthentication();
            int ret = tx.clsTransResult.getResultCode();
            LogUtils.d(TAG, "offlineDataAuthentication ret=" + ret);
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true;
                ret = reselectApplication();
            }
            markTryAgainOnCommunicationError(ret);
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless offline data authentication failed", e);
            finishError(e.getMessage() != null
                    ? e.getMessage() : "Contactless EMV offline data authentication failed");
        } finally {
            if (!proceed) {
                captureSummaryAfterCardRead();
                finishContactlessStage();
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect());
            } else if (kernel.contactless.restrictionsBeforeOfflineDataAuth()) {
                // PayWave: restrictions already ran — on to the kernel's remaining call.
                runContactlessStartTransaction();
            } else {
                advanceContactlessStage(EmvStep.PROCESS_RESTRICTIONS);
            }
        }
    }

    /**
     * Processing Restrictions stage — reached via {@link #onProcessRestrictions}'s contactless
     * branch. A granular kernel (EFT, PayPass, PayWave) runs
     * {@code process.processRestrictions()} here; an atomic kernel arrives here directly from {@link #runContactlessAppSelect} — its SDK has
     * no call for this phase — and skips straight to {@link #runContactlessStartTransaction},
     * the same way mag/manual skip straight to {@link EmvStep#START_ONLINE_PROCESS} on the
     * contact side.
     */
    private void runContactlessProcessRestrictions() {
        if (!kernel.contactless.supportsGranularSteps()) {
            runContactlessStartTransaction();
            return;
        }
        boolean proceed = false;
        boolean reselected = false;
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Processing Restrictions ============");
            tx.clsTransResult = kernel.contactless.processRestrictions();
            int ret = tx.clsTransResult.getResultCode();
            LogUtils.d(TAG, "processRestrictions ret=" + ret);
            if (ret == RetCode.CLSS_RESELECT_APP) {
                reselected = true;
                ret = reselectApplication();
            }
            markTryAgainOnCommunicationError(ret);
            proceed = ret == RetCode.EMV_OK && !isContactlessTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless processing restrictions failed", e);
            finishError(e.getMessage() != null
                    ? e.getMessage() : "Contactless EMV processing restrictions failed");
        } finally {
            if (!proceed) {
                captureSummaryAfterCardRead();
                finishContactlessStage();
            }
        }
        if (proceed) {
            if (reselected) {
                advanceContactlessStage(firstStageAfterAppSelect());
            } else if (kernel.contactless.restrictionsBeforeOfflineDataAuth()) {
                // PayWave: offline data authentication comes after restrictions.
                advanceContactlessStage(EmvStep.OFFLINE_DATA_AUTHENTICATION);
            } else {
                runContactlessStartTransaction();
            }
        }
    }

    /**
     * Final stage — reached via {@link #runContactlessProcessRestrictions}. Cardholder
     * verification through the first GENERATE AC (bundled — see
     * {@code ClssKernelProcess#startTransProcess()}'s doc), then online processing if the kernel
     * requested it. Always terminal — always reports via {@link #finishContactlessStage}.
     */
    private void runContactlessStartTransaction() {
        try {
            LogUtils.d(TAG, "============ Contactless EMV: Start Transaction ============");
            int ret = startContactlessTransProcess();
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
            captureTransactionSummary();
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless execution failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contactless EMV failed");
        } finally {
            finishContactlessStage();
        }
    }

    /**
     * Runs the remaining contactless bundle — cardholder verification through 1st GAC — then, if
     * the kernel requests it, the online-authorization round trip including the 2nd-tap check.
     * Ported from {@code ContactlessService#startTransProcess}; application selection (and, for a
     * granular kernel, read data / offline data auth / processing restrictions) has already run
     * by the time this is reached — see {@link #runContactlessAppSelect} through
     * {@link #runContactlessProcessRestrictions}.
     *
     * <p>{@code CONTACTLESS_FLOW_TYPE} mirrors the one hardcoded value {@link #runPreTransProcess}
     * ever builds ({@code EmvTransParam.FLOWTYPE_COMPLETE}) — verified repo-wide, nothing ever
     * requests {@code FLOWTYPE_SIMPLE} for contactless — so the branch below is unreachable today,
     * kept only for parity with the ported logic in case that ever changes. {@link ClssProcess}
     * itself has no getter for the flow type it was built with, and per its own header comment it
     * shouldn't be touched to add one.
     */
    private int startContactlessTransProcess() {
        tx.clsTransResult = kernel.contactless.startTransProcess();
        CvmResultEnum cvmResult = tx.clsTransResult.getCvmResult();
        int resultCode = tx.clsTransResult.getResultCode();
        TransResultEnum transResultEnum = tx.clsTransResult.getTransResult();
        if (resultCode != RetCode.EMV_OK) {
            // PayPass and the bundled kernels exchange with the card inside this call: a
            // card pulled away mid-exchange lands here as ICC_CMD_ERR. Nothing has gone online
            // yet, so it's a tap-again, not a decline.
            markTryAgainOnCommunicationError(resultCode);
            return resultCode;
        }
        if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE
                || transResultEnum == TransResultEnum.RESULT_TRY_AGAIN) {
            // PayPass reports both with EMV_OK: a restart, not a card to take online. The stage
            // end (checkContactlessResult) asks for the re-tap, or declines at the cap.
            return resultCode;
        }
        int ret = confirmCard();
        LogUtils.d(TAG, "confirm card: " + ret);
        if (ret != RetCode.EMV_OK) {
            // for example, timeout/data_error
            tx.clsTransResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            return ret;
        }
        if (CONTACTLESS_FLOW_TYPE == EmvTransParam.FLOWTYPE_SIMPLE) {
            tx.clsTransResult = new TransResult(0, TransResultEnum.RESULT_SIMPLE_FLOW_END, CvmResultEnum.CVM_NO_CVM);
            return 0;
        }
        if (cvmResult == CvmResultEnum.CVM_ONLINE_PIN || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
            ret = onCardHolderPwd(true, pinBypass.isOnlinePinBypassEnabled(), 0, null);
            // NO_PASSWORD is a PIN bypass (only possible when it's enabled): go online without
            // a PIN block and let the issuer decide.
            if (ret != RetCode.EMV_OK && ret != EmvConstant.ContactCallbackStatus.NO_PASSWORD) {
                // No PIN (cancelled, timed out, PIN pad error): an error, not a card decline. The
                // end is this ERROR; the decline recorded below (so nothing goes online) is
                // dropped by the end guard.
                engine().apduTrace().note("PICC", "online PIN not entered (" + ret + ")");
                finishError("Online PIN not entered — cancelled or timed out");
                tx.clsTransResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, cvmResult);
                return ret;
            }
        }
        // Online-only terminal: nothing completes without the host. A refund goes online whatever
        // the card answered (cards don't authorize refunds: they return an AAC, which the Visa
        // kernel reports as TC), and so does any kernel "approved offline". There's no ARQC to
        // complete in those cases, so the host's answer is the outcome.
        if (needsHostWithoutArqc(transResultEnum, resultCode)) {
            noteOnlineWithoutArqc(transResultEnum);
            OnlineResultWrapper online = startOnlineProcess();
            tx.clsTransResult = hostOutcome(online, cvmResult);
            return tx.clsTransResult.getResultCode();
        }
        // check whether need goes online
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            OnlineResultWrapper onlineResultWrapper = startOnlineProcess();
            TransResultEnum onlineTransResultEnum = onlineResultWrapper.getTransResultEnum();
            // handle online result after second GAC
            if (TransResultEnum.RESULT_ONLINE_APPROVED != onlineTransResultEnum) {
                tx.clsTransResult.setTransResult(onlineTransResultEnum);
                tx.clsTransResult.setResultCode(onlineTransResultEnum.ordinal());
                return onlineTransResultEnum.ordinal();
            }
            runIssuerAuthentication(onlineResultWrapper);
            // restore signature
            if (cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
                tx.clsTransResult.setCvmResult(CvmResultEnum.CVM_SIG);
            }
        }
        return tx.clsTransResult.getResultCode();
    }

    /**
     * Contactless ISSUER_AUTHENTICATION, host approved: when the issuer sent data for the card
     * (Visa issuer update), the 2nd tap and the kernel's completion; otherwise approved as is.
     */
    private void completeContactlessIssuerAuthentication(OnlineResultWrapper onlineResultWrapper) {
        IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
        boolean needSecondTap = kernel.contactless.isNeedSecondTap(issuerRspData);
        if (needSecondTap) {
            onDetect2ndTap();
            tx.clsTransResult = kernel.contactless.completeTransProcess(issuerRspData);
            if (tx.clsTransResult.getResultCode() == RetCode.EMV_OK) {
                runIssuerScriptProcessing(issuerRspData);
            }
        } else {
            tx.clsTransResult = new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
        }
    }

    /**
     * SCRIPT_PROCESSING after a contactless 2nd tap's issuer authentication:
     * {@link #onScriptProcessing} runs the kernel's script call. If the step doesn't run (the
     * transaction was cancelled meanwhile), the scripts still run here, as they did when they
     * were part of the completion call.
     */
    private void runIssuerScriptProcessing(IssuerRspData issuerRspData) {
        tx.pendingScripts = issuerRspData;
        tx.scriptsProcessed = true;
        goToStep(EmvStep.SCRIPT_PROCESSING, null);
        if (tx.pendingScripts != null) {
            engine().apduTrace().note("EMV",
                    "script processing step did not run — running the scripts anyway");
            processIssuerScripts();
        }
    }

    private void processIssuerScripts() {
        IssuerRspData issuerRspData = tx.pendingScripts;
        tx.pendingScripts = null;
        tx.clsTransResult = kernel.contactless.issuerScriptProcessing(issuerRspData);
    }

    /**
     * True when the card finished without an ARQC but the transaction still has to go to the host:
     * any refund the card answered (TC or AAC — cards don't authorize refunds), or a kernel
     * "approved offline" for any type (this terminal approves nothing offline). A card decline
     * (AAC) on anything but a refund stays a decline.
     */
    private boolean needsHostWithoutArqc(TransResultEnum result, int resultCode) {
        if (resultCode != RetCode.EMV_OK) {
            return false;
        }
        if (result == TransResultEnum.RESULT_OFFLINE_APPROVED) {
            return true;
        }
        return result == TransResultEnum.RESULT_OFFLINE_DENIED && isRefund();
    }

    private boolean isRefund() {
        return activeConfig != null && activeConfig.getType() == TransactionType.REFUND;
    }

    private void noteOnlineWithoutArqc(TransResultEnum cardResult) {
        String why = isRefund() ? "refund" : "kernel approved offline";
        LogUtils.i(TAG, "Going online without an ARQC (" + why + ", card result " + cardResult + ")");
        engine().apduTrace().note("EMV", why + " — card result " + cardResult
                + ", sending to the host (online-only terminal)");
    }

    /** The host's answer as the transaction result, for a card that gave no ARQC to complete. */
    private static TransResult hostOutcome(OnlineResultWrapper online, CvmResultEnum cvmResult) {
        TransResultEnum outcome = online.getTransResultEnum();
        int code = outcome == TransResultEnum.RESULT_ONLINE_APPROVED ? RetCode.EMV_OK
                : outcome.ordinal();
        return new TransResult(code, outcome, cvmResult != null ? cvmResult : CvmResultEnum.CVM_NO_CVM);
    }

    /**
     * A card-communication error before any outcome — typically the card left the RF field
     * mid-exchange (a tap too short) — is recorded as "try again" rather than the offline decline
     * {@code ClssProcess} reports for it, as EMV contactless (Book A) requires: the cardholder is
     * asked to tap again: every contactless stage up to and including the kernel's own
     * transaction call ({@code startTransProcess} — where PayPass and the bundled kernels talk
     * to the card), all before anything is sent to the host. At most {@link #MAX_TAP_RETRIES} times per
     * transaction, so a card that always fails still ends in a decline.
     */
    private void markTryAgainOnCommunicationError(int ret) {
        boolean communicationError = ret == RetCode.ICC_CMD_ERR || ret == RetCode.ICC_RESET_ERR
                || ret == RetCode.CLSS_TRY_AGAIN;
        if (!communicationError) {
            return;
        }
        // The cap is tryAgain()'s: it declines once MAX_TAP_RETRIES taps were tried, whichever
        // way the try-again was reported (here, or by the kernel itself: JCB, Mir, RuPay).
        LogUtils.w(TAG, "Contactless communication error ret=" + ret + " — asking to tap again");
        engine().apduTrace().note("PICC",
                "card communication error " + ret + " — tap again");
        tx.clsTransResult = new TransResult(ret, TransResultEnum.RESULT_TRY_AGAIN,
                CvmResultEnum.CVM_NO_CVM);
    }

    /** True once a stage has recorded a terminal outcome — no further stage should run. */
    private boolean isContactlessTransactionFinished() {
        return tx.clsTransResult != null && tx.clsTransResult.getTransResult() != null;
    }

    /**
     * Common tail once a contactless stage chain has reached a terminal outcome. The one place
     * the kernel's listener is released ({@link #runContactlessAppSelect} registers it once):
     * every contactless path ends here, so none is left holding it.
     */
    private void finishContactlessStage() {
        kernel.contactless.unregisterClssProcessListener();
        closeReaders(true);
        if (isCancelled()) {
            noteResultNotReported();
            return;
        }
        try {
            checkContactlessResult();
        } catch (Exception e) {
            LogUtils.e(TAG, "checkClsResult error", e);
            finishError(e.getMessage() != null ? e.getMessage() : "checkClsResult failed");
        }
    }

    /** Cancelled: the readers are closed, and the card's result is not turned into an outcome. */
    private void noteResultNotReported() {
        LogUtils.i(TAG, "Cancelled — the card's result is not reported");
        engine().apduTrace().note("EMV", "cancelled — the card's result is not reported");
    }

    /** Advances to {@code next}; see {@link #advanceContactStage} for why a step that didn't run still cleans up. */
    private void advanceContactlessStage(EmvStep next) {
        if (!goToStep(next)) {
            finishContactlessStage();
        }
    }

    /**
     * Maps the recorded {@link PaxTransactionState#clsTransResult} to the matching result method on {@code this} —
     * ported from {@code ContactlessService#checkClsResult}.
     */
    private void checkContactlessResult() {
        if (tx.clsTransResult == null) {
            LogUtils.e(TAG, "check result: no clsTransResult recorded — treating as offline denied");
            offlineDenied(-1);
            return;
        }
        int resultCode = tx.clsTransResult.getResultCode();
        TransResultEnum transResultEnum = tx.clsTransResult.getTransResult();
        CvmResultEnum cvmResult = tx.clsTransResult.getCvmResult();
        LogUtils.d(TAG, "result code: " + resultCode
                + ", trans result: " + transResultEnum
                + ", cvm result: " + cvmResult);
        // A restart outcome, whatever the result code: PayPass and Mir report See Phone and
        // Try Again with EMV_OK, which fell through to "unknown status" and declined.
        if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE) {
            clsLastNeedSeePhone = true;
            seePhone();
            return;
        }
        if (transResultEnum == TransResultEnum.RESULT_TRY_AGAIN) {
            tryAgain();
            return;
        }
        if (resultCode == RetCode.EMV_OK) {
            // CVM_CONSUMER_DEVICE here is CDCVM already *done*: the phone verified the cardholder
            // (Face ID / fingerprint / passcode), a successful CVM like PIN — the outcome below
            // decides approve/decline as for any other CVM. Only RESULT_CLSS_SEE_PHONE (above)
            // means the cardholder still has to verify on the phone.
            if (cvmResult == CvmResultEnum.CVM_CONSUMER_DEVICE) {
                LogUtils.d(TAG, "CDCVM performed on the consumer device");
            }
            if (transResultEnum == TransResultEnum.RESULT_OFFLINE_APPROVED) {
                offlineApproved(cvmResult == CvmResultEnum.CVM_SIG);
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_APPROVED) {
                onlineApproved(cvmResult == CvmResultEnum.CVM_SIG);
            } else if (transResultEnum == TransResultEnum.RESULT_SIMPLE_FLOW_END) {
                simpleFlowEnd();
            } else {
                // Unknown status
                offlineDenied(RetCode.CLSS_DECLINE);
            }
        } else {
            if (transResultEnum == TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE
                    || resultCode == RetCode.CLSS_USE_CONTACT) {
                // restart detect icc card and transaction
                tryAnotherInterface();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_DENIED) {
                // host reject, prompt has been showed during online
                onlineDenied();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_FAILED) {
                // such as connect error, receive error, pack error, prompt has been showed during online
                onlineFailed();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_CARD_DENIED) {
                onlineCardDenied(resultCode);
            } else if (transResultEnum == TransResultEnum.RESULT_OFFLINE_DENIED) {
                // here we need prompt details by resultCode
                offlineDenied(resultCode);
            } else {
                // Unknown status
                offlineDenied(resultCode);
            }
        }
    }

    /** Ported from {@code ContactlessService#getPan}. */
    private String getContactlessPan() {
        if (tx.clsCachedTrack2Data != null && !tx.clsCachedTrack2Data.isEmpty()) {
            return TrackUtils.getPan(tx.clsCachedTrack2Data);
        }
        String track2Data = getContactlessTrack2Data();
        if (track2Data != null && !track2Data.isEmpty()) {
            return TrackUtils.getPan(track2Data);
        }
        // some cards don't have track2 data
        byte[] panBytes = kernel.contactless.getTlv(TagsTable.PAN);
        String pan = ConvertUtils.bcd2Str(panBytes, panBytes.length);
        int indexF = pan.indexOf('F');
        return pan.substring(0, indexF != -1 ? indexF : pan.length());
    }

    /** Ported from {@code ContactlessService#getTrack2Data}. */
    private String getContactlessTrack2Data() {
        tx.clsCachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contactless.getTlv(TagsTable.TRACK2));
        return tx.clsCachedTrack2Data;
    }

    /** Cardholder name (5F20) as text, e.g. "MTIP06 MCD 25A", as the old app sent it. */
    private String getContactlessCardholderName() {
        return asciiText(kernel.contactless.getTlv(TagsTable.CARDHOLDER_NAME));
    }

    /**
     * Contact chip flow, driven one kernel stage at a time from here instead of one monolithic
     * kernel call: {@link #runContactAppSelect} → {@link #runContactReadAppData} →
     * {@link #runContactCardAuth} → {@link #runContactStartTransaction}. Each stage calls the
     * matching {@link ContactProcess} method (which itself calls a single top-level
     * EMVCallback.EMV___ native call), then either advances via {@link #advanceContactStage} —
     * which normally calls {@link #goToStep}, publishing the next {@link EmvStep} on the
     * observable and dispatching to that step's onXxx method, which hands straight back to the
     * matching run* method — or, on error or a terminal business result such as
     * simple-flow-end, per {@link #isContactTransactionFinished()}, stops and reports
     * via {@link #finishContactStage}.
     *
     * <p>Once the transaction is cancelled (or ended) no step runs: the engine publishes none,
     * and {@link #goToStep} says so by returning {@code false}. For chip the ICC reader stays
     * activated across the whole app-select → start-transaction span, so
     * {@link #advanceContactStage} falls back to {@link #finishContactStage} when the next step
     * didn't run — the reader is released whatever ended the chain.
     *
     * <p>Processing restrictions, cardholder verification, terminal risk management and
     * terminal action analysis stay bundled inside {@link #runContactStartTransaction}'s
     * EMVStartTrans call — the PAX kernel has no separate native entry point for each, so they
     * can't be split out any further.
     */
    // ─── CONTACT ONLY — ContactProcess runners ────────────────────────────

    private void runContactAppSelect() {
        boolean proceed = false;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            LogUtils.d(TAG, "============ Contact EMV: App Select ============");
            // reset every transaction — mirrors EmvContactService#selectApplication
            tx.cachedTrack2Data = null;
            resetTransactionSummary();
            kernel.contact.registerEmvProcessListener(this);
            tx.transResult = selectApplicationWithRetry();
            int ret = tx.transResult.getResultCode();
            LogUtils.d(TAG, "selectApplication ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contact app select failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV app select failed");
        } finally {
            // Readers stay open (and nothing is reported yet) only while we're about to chain
            // into the next stage — every other exit (cancel, exception, terminal result) must
            // close them here, since no later stage will run to do it.
            if (!proceed) {
                finishContactStage();
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.READ_APPLICATION_DATA);
        }
    }

    /**
     * Retries {@code selectApplication()} up to {@link #ICC_APP_SELECT_MAX_ATTEMPTS} times,
     * power-cycling the ICC slot between attempts, when the first attempt comes back non-OK.
     * Native {@code IccException: 51} (no ATR) surfaces here as a plain {@code ret == -1} — the
     * kernel catches the exception internally and returns a result code, so there's nothing to
     * catch on our side, only the code to react to. Restarting app selection from scratch on
     * retry is safe: nothing has been selected yet at this point in the flow.
     */
    private TransResult selectApplicationWithRetry() {
        TransResult result = kernel.contact.selectApplication();
        for (int attempt = 1; result.getResultCode() != RetCode.EMV_OK
                && attempt < ICC_APP_SELECT_MAX_ATTEMPTS && !isCancelled(); attempt++) {
            LogUtils.w(TAG, "selectApplication ret=" + result.getResultCode() + " (attempt "
                    + attempt + "/" + ICC_APP_SELECT_MAX_ATTEMPTS
                    + ") — power-cycling ICC and retrying");
            try {
                EmvFlowRuntime.getDal().getIcc().close((byte) 0);
            } catch (Exception ignored) {
                // Expected if the slot is already in a bad state — closing here is only to
                // force a fresh power-on for the next attempt, not to succeed cleanly.
            }
            try {
                Thread.sleep(ICC_POWER_SETTLE_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            result = kernel.contact.selectApplication();
        }
        return result;
    }

    /** Read App Data stage — reached via {@link #onReadApplicationData}'s chip branch. */
    private void runContactReadAppData() {
        boolean proceed = false;
        try {
            LogUtils.d(TAG, "============ Contact EMV: Read App Data ============");
            tx.transResult = kernel.contact.readApplicationData();
            int ret = tx.transResult.getResultCode();
            LogUtils.d(TAG, "readApplicationData ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contact read app data failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV read app data failed");
        } finally {
            if (!proceed) {
                finishContactStage();
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.SET_TRANSACTION_DATA);
        }
    }

    /**
     * Set Transaction Data stage — reached via {@link #onSetTransactionData}'s chip branch,
     * after READ_APPLICATION_DATA: the contact kernel takes the transaction data after reading
     * the card (confirm card, 9C).
     */
    private void runContactSetTransactionData() {
        boolean proceed = false;
        try {
            LogUtils.d(TAG, "============ Contact EMV: Set Transaction Data ============");
            tx.transResult = kernel.contact.setTransactionData();
            int ret = tx.transResult.getResultCode();
            LogUtils.d(TAG, "setTransactionData ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contact set transaction data failed", e);
            finishError(e.getMessage() != null ? e.getMessage()
                    : "Contact EMV set transaction data failed");
        } finally {
            if (!proceed) {
                finishContactStage();
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.OFFLINE_DATA_AUTHENTICATION);
        }
    }

    /**
     * Card Auth (CAPK + EMVCardAuth) stage — reached via
     * {@link #onOfflineDataAuthentication}'s chip branch.
     */
    private void runContactCardAuth() {
        boolean proceed = false;
        try {
            LogUtils.d(TAG, "============ Contact EMV: Card Auth ============");
            tx.transResult = kernel.contact.cardAuthentication();
            int ret = tx.transResult.getResultCode();
            LogUtils.d(TAG, "cardAuthentication ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
        } catch (Exception e) {
            LogUtils.e(TAG, "contact card auth failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV card auth failed");
        } finally {
            if (!proceed) {
                finishContactStage();
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.PROCESS_RESTRICTIONS);
        }
    }

    /**
     * Advances to {@code next} via {@link #goToStep}. If it didn't run — cancelled or ended in
     * between — the next run* method (and its {@link #finishContactStage} cleanup) never fires,
     * so the cleanup runs here instead: the readers are never left open.
     */
    private void advanceContactStage(EmvStep next) {
        if (!goToStep(next)) {
            finishContactStage();
        }
    }

    /**
     * Final stage — reached via {@link #onProcessRestrictions}'s chip branch. EMVStartTrans
     * (processing restrictions → CVM → terminal risk management → terminal action analysis →
     * 1st GAC, bundled — see class doc), then online processing if the kernel requested it.
     * Always terminal — always reports via {@link #finishContactStage}.
     */
    private void runContactStartTransaction() {
        try {
            LogUtils.d(TAG, "============ Contact EMV: Start Transaction ============");
            // CVM is published separately from onCardHolderPwd below when the kernel actually
            // asks for it; terminal risk management and terminal action analysis have no
            // callback and are never published (see class doc).
            int ret = startContactTransProcess();
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
            captureTransactionSummary();
        } catch (Exception e) {
            LogUtils.e(TAG, "contact execution failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV failed");
        } finally {
            finishContactStage();
        }
    }

    /**
     * Runs EMVStartTrans and, if the kernel requests it, the online-authorization round trip
     * (host authorize via {@link #startOnlineProcess()} → EMVCompleteTrans). Ported from
     * {@code EmvContactService#startTransProcess} — the AET-146 comment below is carried over
     * verbatim.
     */
    private int startContactTransProcess() {
        tx.transResult = kernel.contact.startTransProcess();
        int resultCode = tx.transResult.getResultCode();
        TransResultEnum transResultEnum = tx.transResult.getTransResult();
        if (resultCode != RetCode.EMV_OK) {
            return resultCode;
        }
        // Same rule as contactless (see startContactlessTransProcess): a refund goes online
        // whatever the card answered at 1st GAC — no 2nd GAC, the host's answer is the outcome.
        if (needsHostWithoutArqc(transResultEnum, resultCode)) {
            noteOnlineWithoutArqc(transResultEnum);
            OnlineResultWrapper online = startOnlineProcess();
            tx.transResult = hostOutcome(online, tx.transResult.getCvmResult());
            return tx.transResult.getResultCode();
        }
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            OnlineResultWrapper onlineResultWrapper = startOnlineProcess();
            tx.transResult.setResultCode(onlineResultWrapper.getResultCode());
            tx.transResult.setTransResult(onlineResultWrapper.getTransResultEnum());
            runIssuerAuthentication(onlineResultWrapper);
            return tx.transResult.getResultCode();
        }
        return 0;
    }

    /**
     * Contact ISSUER_AUTHENTICATION: the card's 2nd GENERATE AC (EMVCompleteTrans) with the
     * host's answer, and the outcome it gives.
     */
    private void completeContactIssuerAuthentication(OnlineResultWrapper onlineResultWrapper) {
        IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
        int onlineResultCode = onlineResultWrapper.getResultCode();
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
        Log.d(TAG, "startContactTransProcess: "+issuerRspData);
        TransResult secondTransResult = kernel.contact.completeTransProcess(issuerRspData);

        tx.transResult.setResultCode(secondTransResult.getResultCode());
        if (onlineResultCode == EOnlineResult.APPROVE.getResultCode()) {
            tx.transResult.setTransResult(secondTransResult.getTransResult() != TransResultEnum.RESULT_ONLINE_APPROVED
                    ? TransResultEnum.RESULT_ONLINE_CARD_DENIED : TransResultEnum.RESULT_ONLINE_APPROVED);
        } else if (onlineResultCode == EOnlineResult.FAILED.getResultCode()) {
            tx.transResult.setTransResult(secondTransResult.getTransResult() != TransResultEnum.RESULT_ONLINE_APPROVED
                    ? TransResultEnum.RESULT_ONLINE_FAILED : TransResultEnum.RESULT_ONLINE_FAILED_CARD_APPROVED);
        } else {
            tx.transResult.setTransResult(TransResultEnum.RESULT_ONLINE_DENIED);
        }
    }

    /** True once a stage has recorded a terminal outcome — no further stage should run. */
    private boolean isContactTransactionFinished() {
        return tx.transResult != null && tx.transResult.getTransResult() != null;
    }

    /**
     * Common tail once a contact stage chain has reached a terminal outcome. The one place the
     * kernel's listener is released ({@link #runContactAppSelect} registers it once): every
     * contact path ends here, so none is left holding it.
     */
    private void finishContactStage() {
        kernel.contact.registerEmvProcessListener(null);
        closeReaders(false);
        if (tx.activePinPad != null) {
            tx.activePinPad.dismiss();
            tx.activePinPad = null;
        }
        if (isCancelled()) {
            noteResultNotReported();
            return;
        }
        try {
            checkContactResult();
        } catch (Exception e) {
            LogUtils.e(TAG, "checkContactResult error", e);
            finishError(e.getMessage() != null ? e.getMessage() : "checkContactResult failed");
        }
    }

    /**
     * Maps the recorded {@link PaxTransactionState#transResult} to the matching result method on {@code this} —
     * ported from {@code EmvContactService#checkContactResult}.
     */
    private void checkContactResult() {
        if (tx.transResult == null) {
            LogUtils.e(TAG, "check result: no transResult recorded — treating as offline denied");
            offlineDenied(-1);
            return;
        }
        int resultCode = tx.transResult.getResultCode();
        TransResultEnum transResultEnum = tx.transResult.getTransResult();
        if (transResultEnum == null) {
            // No stage ever reached a terminal result — e.g. an exception aborted the chain
            // before that stage's own transResult assignment ran, leaving the "continue"
            // sentinel (RetCode.EMV_OK, no enum) from the prior successful stage in place.
            // Fail closed instead of NPE-ing on the switch below.
            LogUtils.e(TAG, "check result: no terminal result recorded, code = " + resultCode
                    + " — treating as offline denied");
            offlineDenied(resultCode);
            return;
        }
        CvmResultEnum cvmResult = tx.transResult.getCvmResult();
        if (cvmResult == null) {
            cvmResult = CvmResultEnum.CVM_NO_CVM;
        }
        LogUtils.d(TAG, "check result: code = " + resultCode
                + ", enum = " + transResultEnum.name()
                + ", cvm = " + cvmResult.name());
        boolean isNeedSignature = cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG;
        switch (transResultEnum) {
            case RESULT_OFFLINE_APPROVED:
                offlineApproved(isNeedSignature, true);
                break;
            case RESULT_ONLINE_APPROVED:
                onlineApproved(isNeedSignature);
                break;
            case RESULT_ONLINE_CARD_DENIED:
                onlineCardDenied(resultCode);
                break;
            case RESULT_ONLINE_FAILED_CARD_APPROVED:
                // No host answer: the card approving at 2nd GAC doesn't make it approved here.
                onlineFailed();
                break;
            case RESULT_ONLINE_FAILED:
                onlineFailed();
                break;
            case RESULT_ONLINE_DENIED:
                onlineDenied();
                break;
            case RESULT_OFFLINE_DENIED:
                offlineDenied(resultCode);
                break;
            case RESULT_SIMPLE_FLOW_END:
                simpleFlowEnd();
                break;
            case RESULT_FALLBACK:
                fallback();
                break;
            default:
                LogUtils.e(TAG, "Cannot handle this statement");
                offlineDenied(resultCode);
                break;
        }
    }

    /** Ported from {@code EmvContactService#getPan}. */
    private String getContactPan() {
        if (tx.cachedTrack2Data != null && !tx.cachedTrack2Data.isEmpty()) {
            return TrackUtils.getPan(tx.cachedTrack2Data);
        }
        String track2Data = getContactTrack2Data();
        if (track2Data != null && !track2Data.isEmpty()) {
            return TrackUtils.getPan(track2Data);
        }
        // some cards don't have track2 data
        byte[] panBytes = kernel.contact.getTlv(TagsTable.PAN);
        String pan = ConvertUtils.bcd2Str(panBytes, panBytes.length);
        int indexF = pan.indexOf('F');
        return pan.substring(0, indexF != -1 ? indexF : pan.length());
    }

    /** Ported from {@code EmvContactService#getTrack2Data}. */
    private String getContactTrack2Data() {
        tx.cachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contact.getTlv(TagsTable.TRACK2));
        return tx.cachedTrack2Data;
    }

    /** Cardholder name (5F20) as text, e.g. "MTIP06 MCD 25A", as the old app sent it. */
    private String getContactCardholderName() {
        return asciiText(kernel.contact.getTlv(TagsTable.CARDHOLDER_NAME));
    }

    private static String asciiText(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII).trim();
    }

    // ─── SHARED — kernel bring-up used by both contact and contactless ───

    private boolean prepareKernel(@NonNull TransactionConfig config) {
        if (!config.allowsChip() && !config.allowsContactless()) {
            return true;
        }
        try {
            EmvFlowRuntime.init(EmvFlowRuntime.getApp());
        } catch (Throwable t) {
            LogUtils.e(TAG, "EmvFlowRuntime.init failed", t);
        }
        if (!EmvFlowRuntime.isReady()) {
            LogUtils.e(TAG, "Neptune DAL is not ready — cannot start EMV");
            return false;
        }

        initializeTerminalIfNeeded();

        // Defensive reset: close every reader before this attempt touches any of them, in case
        // the previous transaction (crash, process death, a partial closeReaders() failure) left
        // one open. Closing an already-closed reader is a no-op, so this is safe to run
        // unconditionally on every attempt, not just after a known-bad prior transaction.
        closeReaders(true);

        byte requested = 0;
        if (config.allowsChip()) {
            requested |= SearchMode.INSERT;
        }
        if (config.allowsContactless()) {
            requested |= SearchMode.INTERNAL_WAVE;
        }
        if (requested == 0) {
            return false;
        }
        try {
            // Fresh kernel object per transaction attempt, not a process-lifetime singleton —
            // this reset used to live inside EmvContactService#preTransProcess.
            kernel.contact = new ContactProcess();
            long setUpStart = SystemClock.elapsedRealtime();
            byte adjusted = runPreTransProcess(config, requested);
            engine().apduTrace().note("EMV", "kernel set-up for this transaction: "
                    + (SystemClock.elapsedRealtime() - setUpStart) + " ms");
            kernel.contactReady = SearchMode.isSupportIcc(adjusted);
            kernel.contactlessReady = SearchMode.isWave(adjusted);
            if (config.allowsContactless() && !kernel.contactlessReady) {
                engine().apduTrace().note("PICC",
                        "contactless disabled for this transaction: pre-processing failed");
            }
            if (adjusted == 0) {
                LogUtils.e(TAG, "preTransProcess disabled every search mode");
                return false;
            }
            return true;
        } catch (Exception e) {
            LogUtils.e(TAG, "preTransProcess failed", e);
            return false;
        }
    }

    /**
     * Terminal initialization: loads every EMV/CLSS parameter (AIDs, CAPKs, terminal config,
     * contactless kernels) from the database into memory, in step 0 before the card search —
     * the first transaction after an app start, a download or a seed
     * ({@link EmvParamService#isLoaded()}, the one owner). Every transaction after that reuses
     * them and only runs its own kernel set-up ({@link #runPreTransProcess}: amount, date/time,
     * 9F41, contactless pre-processing), which PAX and EMV need per transaction.
     */
    private void initializeTerminalIfNeeded() {
        if (EmvParamService.isLoaded()) {
            return;
        }
        long start = SystemClock.elapsedRealtime();
        kernel.params.getCachedEmvParam();
        engine().apduTrace().note("EMV", "terminal initialization (EMV parameters loaded"
                + " from the database): " + (SystemClock.elapsedRealtime() - start) + " ms");
    }

    /**
     * Amount, Other (9F03): no cashback, so always zero. The kernels get it in the trans param
     * only. It is never written into a kernel as a tag: the contact kernel then took the sale for
     * cashback in its Application Usage Control check (TVR byte 2 b5, "requested service not
     * allowed") and declined offline any card whose IAC-Denial has that bit (a 650.00 EGP Visa
     * sale, AUC FF00). The kernel still puts zeros in GENERATE AC when CDOL1 asks for 9F03 but
     * doesn't return it when read back, so {@link #readIccTag} answers zeros for field 55,
     * which the host needs to check the ARQC.
     */
    private static final long AMOUNT_OTHER_MINOR = 0L;

    private final PinBypassSettings pinBypass =
            new PinBypassSettings(BaseApplication.getAppContext());

    /**
     * Transaction Sequence Counter (9F41): always 0, as the old app sent it. Its field 55 with
     * 9F41 = 00000000 is the one the host approved.
     */
    private static final long TRANSACTION_SEQUENCE_COUNTER = 0L;

    private static byte[] amountOtherBcd() {
        return ConvertUtils.strToBcdPaddingLeft(String.format(Locale.US, "%012d", AMOUNT_OTHER_MINOR));
    }

    /**
     * Builds {@link EmvTransParam}/{@link EmvProcessParam} from {@code config} and the cached
     * EMV parameters, then runs contact / contactless {@code preTransProcess}, clearing the
     * corresponding {@link SearchMode} bit for whichever one fails. Ported from
     * {@code EmvPreProcessFacade#start} — single caller, no service-locator indirection, and
     * {@code emvflow} (the module it used to live in) is PAX-only already, so nothing else needed
     * it as a separate object.
     */
    private byte runPreTransProcess(@NonNull TransactionConfig config, byte searchCardMode) {
        if (!SearchMode.isSupportIcc(searchCardMode) && !SearchMode.isWave(searchCardMode)) {
            return 0;
        }
        DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
        byte[] proc = ConvertHelper.getConvert().strToBcdPaddingRight(config.getProcCode());
        EmvProcessParam cachedEmvParam = kernel.params.getCachedEmvParam();
        String dateTime = timestamp();
        // One trans param for both kernels. PCI mode, PIN lengths and PIN timeout are read by
        // the contact kernel only (EMVSetPCIModeParam); contactless ignores them.
        EmvTransParam transParam = new EmvTransParam.Builder()
                .setTransType(proc[0])
                .setAmount(config.getAmountMinor())
                .setAmountOther(AMOUNT_OTHER_MINOR)
                .setTerminalID(cachedEmvParam.getTermConfig().getTerminalId())
                .setTransCurrencyCode(CurrencyConverter.getCurrencyCode())
                .setTransCurrencyExponent((byte) CurrencyConverter.getDigitsNum())
                .setTransDate(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(2, 8)))
                .setTransTime(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(8)))
                .setTransTraceNo(TRANSACTION_SEQUENCE_COUNTER)
                .setFlowType(EmvTransParam.FLOWTYPE_COMPLETE)
                .setMaskPattern("")
                .setPciMode((byte) 1)
                .setPinLenSet(offlinePinLengths().getBytes(StandardCharsets.US_ASCII))
                .setPciTimeout(60 * 1000)
                .create();
        EmvProcessParam.Builder processParamBuilder = new EmvProcessParam.Builder()
                .setTermConfig(cachedEmvParam.getTermConfig())
                .setCapkParam(cachedEmvParam.getCapkParam())
                .setEmvTransParam(transParam);

        if (SearchMode.isSupportIcc(searchCardMode)) {
            processParamBuilder.setEmvAidList(cachedEmvParam.getEmvAidList());
            int contactRet = kernel.contact.preTransProcess(processParamBuilder.create());
            if (contactRet != RetCode.EMV_OK) {
                LogUtils.e(TAG, "contact pre process failed, ret=" + contactRet);
                searchCardMode = (byte) (searchCardMode & (~SearchMode.INSERT));
            }
        }
        if (SearchMode.isSupportInternalPicc(searchCardMode)
                || SearchMode.isSupportExternalPicc(searchCardMode)) {
            processParamBuilder.setAmexParam(cachedEmvParam.getAmexParam())
                    .setPassParam(cachedEmvParam.getPayPassParam())
                    .setPayWaveParam(cachedEmvParam.getPayWaveParam())
                    .setDpasParam(cachedEmvParam.getDpasParam())
                    .setEFTParam(cachedEmvParam.getEftParam())
                    .setJcbParam(cachedEmvParam.getJcbParam())
                    .setMirParam(cachedEmvParam.getMirParam())
                    .setPbocParam(cachedEmvParam.getPbocParam())
                    .setPureParam(cachedEmvParam.getPureParam())
                    .setRuPayParam(cachedEmvParam.getRuPayParam());
            int contactlessRet = kernel.contactless.preTransProcess(processParamBuilder.create());
            if (contactlessRet != RetCode.EMV_OK) {
                LogUtils.e(TAG, "contactless pre process failed, ret=" + contactlessRet
                        + " — contactless won't be offered for this transaction");
                searchCardMode = (byte) (searchCardMode & (~SearchMode.WAVE));
            }
        }
        return searchCardMode;
    }

    // ═══════════════════════════════════════════════════════════════════
    // IContactCallback + IContactlessCallback overrides
    // Each is tagged below — CONTACT / CONTACTLESS / SHARED (both interfaces
    // route through the same method; see class doc + §09/§14/§15 of the
    // EMV Transaction Backbone artifact for why).
    // ═══════════════════════════════════════════════════════════════════

    // [CONTACTLESS]
    @Override
    public void onReadCardOk() {
        if (!kernel.contactless.supportsGranularSteps()) {
            // An atomic kernel read the application data itself, inside its one bundled call:
            // publish the step for the UI and the trace — onReadApplicationData knows not to read
            // again. A granular kernel (EFT, PayPass, PayWave) already went through it via
            // runContactlessReadAppData(), so publishing it here would be a duplicate.
            goToStep(EmvStep.READ_APPLICATION_DATA, null);
        }
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"));
    }

    // [CONTACTLESS]
    @Override
    public int confirmCard() {
        if (!kernel.contactless.supportsGranularSteps()) {
            // An atomic kernel took the data inside its one call; a granular one already went
            // through SET_TRANSACTION_DATA (runContactlessSetTransactionData).
            goToStep(EmvStep.SET_TRANSACTION_DATA, null);
        }
        // The contactless kernels don't take the TID from the trans param; set it for field 55.
        kernel.contactless.setTlv(TagsTable.TERMINAL_ID,
                kernel.params.getCachedEmvParam().getTermConfig().getTerminalId());
        publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, safe(getContactlessPan()))
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "PAX Issuer")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, safe(getContactlessCardholderName()))
                .put(TransactionStepEvent.KEY_MODE, "Contactless")
                .build());
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    // [SHARED — same method satisfies both IContactCallback and IContactlessCallback]
    @Override
    public int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList) {
        // The kernel waits on the choice: the step method makes it (answerApplicationSelection).
        // Fail closed: a cancelled or ended transaction answers USER_CANCEL, never a hang.
        tx.candidates = candList;
        int candidates = candList == null ? 0 : candList.size();
        return askStep(isFirstSelect
                        ? EmvStep.WAIT_APPLICATION_SELECTION
                        : EmvStep.FINAL_APPLICATION_SELECTION,
                candidates + " candidate AID(s)"
                        + (showsAppSelectDialog(candList) ? "" : ", selecting first"),
                EmvConstant.ContactCallbackStatus.USER_CANCEL);
    }

    // [CONTACT]
    @Override
    public int showConfirmCard() {
        // Called by ContactProcess.setTransactionData(), inside SET_TRANSACTION_DATA's own step.
        publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, safe(getContactPan()))
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "PAX Issuer")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, safe(getContactCardholderName()))
                .put(TransactionStepEvent.KEY_MODE, "Contact")
                .build());
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    // [SHARED — same method satisfies both interfaces; branches internally on isOnlinePin]
    @Override
    public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
            byte[] pinData) {
        // The kernel (contact) or startContactlessTransProcess waits on the PIN: the step method
        // collects it (enterPin) and answers. Fail closed: a cancelled or ended transaction
        // answers USER_CANCEL, never a hang.
        tx.pinRequest = new PinRequest(isOnlinePin, supportPINByPass, leftTimes);
        try {
            if (isOnlinePin) {
                // Online PIN: CARDHOLDER_VERIFICATION's method collects it and answers.
                return askStep(EmvStep.CARDHOLDER_VERIFICATION, "online PIN",
                        EmvConstant.ContactCallbackStatus.USER_CANCEL);
            } else {
                // Offline PIN: CARDHOLDER_VERIFICATION is announced, OFFLINE_PIN_VERIFICATION's
                // method collects it and answers; the kernel verifies it with the card.
                goToStep(EmvStep.CARDHOLDER_VERIFICATION, "PIN");
                return askStep(EmvStep.OFFLINE_PIN_VERIFICATION, null,
                        EmvConstant.ContactCallbackStatus.USER_CANCEL);
            }
        } finally {
            tx.pinRequest = null;
        }
    }

    /**
     * The PIN step's work, answered to {@link #onCardHolderPwd}'s {@code askStep}: online PIN
     * from {@link #onCardholderVerification}, offline PIN from {@link #onOfflinePinVerification}.
     */
    private int enterPin(PinRequest request) {
        boolean isOnlinePin = request.online;
        boolean supportPINByPass = request.supportPinBypass;
        int leftTimes = request.leftTimes;
        publish(TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, isOnlinePin)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, supportPINByPass && (isOnlinePin
                        ? pinBypass.isOnlinePinBypassEnabled()
                        : pinBypass.isOfflinePinBypassEnabled()))
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, leftTimes)
                .build());

        PinService pinService = new PinService();

        if (!isOnlinePin) {
            // Offline PIN never produces a PIN block for the host — nothing goes in encPinBlock.
            clearOnlinePin();
            // Offline PIN is the kernel's job, not ours: once we say CONTACT_OK, it calls
            // EmvDeviceImpl#pedVerifyPlainPin / #pedVerifyCipherPin (the native IDevice
            // callback) directly, which is already fully wired to IPed#verifyPlainPin /
            // #verifyCipherPin — the real secure PIN pad, real verification against the ICC.
            // What's missing without the two lines below is only the on-screen feedback: PAX's
            // own demo (OfflinePinTask) always wires setInputPinListener + EmvDeviceImpl's
            // pinCallback and shows a dialog before returning CONTACT_OK, fire-and-forget —
            // the dialog is dismissed by whichever of PinCallback's terminal callbacks
            // EmvDeviceImpl invokes (onCancel/onTimeout/onError), or defensively in
            // finishContactStage() if none of those fire.
            if (tx.activePinPad != null) {
                tx.activePinPad.dismiss(); // the kernel asks again (wrong PIN): one pad on screen
            }
            tx.activePinPad = new PaxPinPad("Enter Offline PIN (" + leftTimes + " left)");
            tx.activePinPad.showForOfflinePin();
            pinService.setInputPinListener(tx.activePinPad);
            EmvDeviceImpl.getInstance().setPinCallback(tx.activePinPad);
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        // Online PIN never touches the card, so nothing in the native kernel collects it for
        // us — show the secure pad ourselves, matching PAX's own demo (OnlinePinTask): a dialog
        // shown and waited on via ConditionVariable before the blocking collect call, so the
        // key-event listener always has a live dialog to update. Uses PinService directly
        // (same object for both the listener and the actual collection) instead of duplicating
        // its already-correct getPinBlock(keyIndex, pinLenCsv, panBytes, mode, timeoutMs) call.
        //
        // Bypass needs the terminal setting, and for contact the selected AID too
        // (supportPINByPass); contactless passes the setting itself.
        boolean bypassAllowed = supportPINByPass && pinBypass.isOnlinePinBypassEnabled();
        tx.lastOnlinePinBlock = null;
        provisionOnlinePinKey();

        PaxPinPad pad = new PaxPinPad(bypassAllowed
                ? "Enter Online PIN (Enter to skip)" : "Enter Online PIN");
        tx.activePinPad = pad;
        pad.showForOnlinePin();
        pinService.setInputPinListener(pad);
        try {
            IPed ped = PedHelper.getPed();
            ped.setIntervalTime(1, 1);
            String pan = activeCard != null && activeCard.isContactless()
                    ? getContactlessPan() : getContactPan();
            byte[] pinBlock = pinService.getEncryptedPinData(safe(pan), bypassAllowed, false);
            if (pinBlock == null || pinBlock.length == 0) {
                // Enter with no digits: the PIN pad only allows it when bypass is allowed.
                clearOnlinePin();
                return bypassAllowed ? EmvConstant.ContactCallbackStatus.NO_PASSWORD
                        : EmvConstant.ContactCallbackStatus.USER_CANCEL;
            }
            tx.lastOnlinePinBlock = pinBlock;
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        } catch (PinException e) {
            // No PIN block, so no PIN key either — the host must not expect one.
            clearOnlinePin();
            if (bypassAllowed && NO_PIN_INPUT.equals(e.getErrCode())) {
                return EmvConstant.ContactCallbackStatus.NO_PASSWORD;
            }
            LogUtils.e(TAG, "online PIN entry failed", e);
            return EmvConstant.ContactCallbackStatus.USER_CANCEL;
        } catch (PedDevException e) {
            LogUtils.e(TAG, "online PIN entry failed", e);
            clearOnlinePin();
            return EmvConstant.ContactCallbackStatus.USER_CANCEL;
        } finally {
            pinService.setInputPinListener(null);
            pad.dismiss();
        }
    }

    /** {@code PinException#getErrCode} when the PIN pad's Enter was pressed with no digits. */
    private static final String NO_PIN_INPUT =
            String.valueOf(EPedDevException.PED_ERR_NO_PIN_INPUT.getErrCodeFromBasement());

    /**
     * PIN lengths the PIN pad accepts for offline PIN (the contact kernel's PCI mode). A {@code 0}
     * lets Enter with no digits through, which the kernel treats as a PIN bypass — only when
     * offline PIN bypass is enabled.
     */
    private String offlinePinLengths() {
        String lengths = "4,5,6,7,8,9,10,11,12";
        return (pinBypass.isOfflinePinBypassEnabled() ? "0," + lengths : lengths) + "\0";
    }

    /** Forgets this transaction's online PIN block and PIN key (none collected / not online). */
    private void clearOnlinePin() {
        tx.lastOnlinePinBlock = null;
        tx.lastOnlinePinKeyEncrypted = null;
    }

    /**
     * Generates a fresh random online PIN key (PEK) for this transaction, writes it to
     * {@link PosDeviceUtils#INDEX_AES_PEK} as an **AES** key so {@code getPinBlock} produces an
     * AES-encrypted PIN block (confirmed against the server team — it decrypts the PIN block with
     * AES, not 3DES), and RSA-wraps the same key value with the host's public key (from
     * onboarding — {@link OnboardingState#getPublicKey()}) so the host can independently recover
     * it. Runs every online-PIN transaction, not just once — a fresh key per transaction, not a
     * reused one, matching the old project's {@code preparePekKey()} being called at the start of
     * every sale.
     *
     * <p>Ported from the old project's {@code PaxUtils#writePekAES} — {@code EPedKeyType.AES_TPK}
     * (a distinct key type from the plain, 3DES-family {@code EPedKeyType.TPK}) written via
     * {@code IPed#writeAesKey}, erasing whatever's at that index first, wrapped under
     * {@code EPedKeyType.TLK} index 0 (not {@code TMK} — the AES key hierarchy uses a different
     * wrapping key type than the 3DES one). The key type written here must match the PIN-block
     * mode {@code PinService#getEncryptedPinData} reads with: the 3DES
     * {@code EPinBlockMode.ISO9564_0} call reads the 3DES {@code TPK} slot, not this
     * {@code AES_TPK}, so it uses the AES ISO 9564 format 4 mode (see {@code PinService}'s
     * {@code PIN_BLOCK_MODE_ISO9564_4_AES}).
     *
     * <p>That assumes a TLK is already present at index 0, true on PAX SDK demo/dev units out of
     * the box; a real deployment provisions its own TLK (see the onboarding online/TMK-provisioning
     * cycle, currently dormant — {@code OnboardingClient#runOnlineCycle} — which would need an AES
     * counterpart if it's ever turned on for this key hierarchy too). If the write fails (no TLK)
     * or no public key has been onboarded yet, this logs and leaves
     * {@link PaxTransactionState#lastOnlinePinKeyEncrypted} unset — online PIN entry itself still proceeds (matching
     * this method's predecessor's behavior), it just won't have a key the host can decrypt with.
     */
    private void provisionOnlinePinKey() {
        tx.lastOnlinePinKeyEncrypted = null;

        byte[] pinKey = new byte[ONLINE_PIN_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(pinKey);

        IPed ped = PedHelper.getPed();
        // Clear the slot before every PIN entry so nothing stale can be used. Both PIN key types
        // at this index are erased; an empty slot throws, which isn't a reason to skip the write.
        eraseKeyQuietly(ped, EPedKeyType.AES_TPK, PosDeviceUtils.INDEX_AES_PEK);
        eraseKeyQuietly(ped, EPedKeyType.TPK, PosDeviceUtils.INDEX_AES_PEK);
        try {
            ped.writeAesKey(EPedKeyType.TLK.getPedkeyType(), (byte) 0,
                    EPedKeyType.AES_TPK.getPedkeyType(), PosDeviceUtils.INDEX_AES_PEK, pinKey,
                    EAesCheckMode.KCV_NONE, null);
        } catch (PedDevException e) {
            LogUtils.e(TAG, "Online PIN key write failed — no TLK at index 0? Online PIN will "
                    + "keep failing 'Key does not exist' until a real TLK is injected.", e);
            return;
        }

        String hostPublicKey = new OnboardingState(BaseApplication.getAppContext()).getPublicKey();
        if (hostPublicKey == null) {
            LogUtils.w(TAG, "No host public key on file (onboarding not completed?) — online PIN "
                    + "key written to the PED, but the host has no way to decrypt with it.");
            return;
        }
        try {
            tx.lastOnlinePinKeyEncrypted = RsaPublicKeyEncryptor.encryptToHex(hostPublicKey, pinKey);
        } catch (GeneralSecurityException e) {
            LogUtils.e(TAG, "Failed to RSA-encrypt the online PIN key for the host", e);
        }
    }

    private static void eraseKeyQuietly(IPed ped, EPedKeyType keyType, byte index) {
        try {
            ped.eraseKey(keyType.getPedkeyType(), index);
        } catch (PedDevException e) {
            LogUtils.i(TAG, "No " + keyType + " to erase at index " + index + ": " + e.getErrMsg());
        }
    }

    // [SHARED — same method satisfies both interfaces; §08 of the backbone artifact covers
    // how the return-code handoff into this method works for the contact path]
    @NonNull
    @Override
    public OnlineResultWrapper startOnlineProcess() {
        // Right after the 1st GENERATE AC returned the ARQC: onStartOnlineProcess sends it —
        // field 55 (read through readIccTag) and the card data (addOnlineRequestData) attached.
        // Fail closed: if that step doesn't run (the transaction was cancelled), nothing goes
        // to the host and the answer is online failed.
        tx.lastAuth = null;
        tx.onlineRequested = true;
        try {
            goToStep(EmvStep.START_ONLINE_PROCESS, null);
        } finally {
            tx.onlineRequested = false;
        }
        if (tx.lastAuth == null) {
            engine().apduTrace().note("EMV", "online step did not run — online failed");
        }
        return toOnlineResultWrapper(tx.lastAuth);
    }

    /** How the host answered, for the ISSUER_AUTHENTICATION banner. */
    private String issuerAuthenticationDetail() {
        AuthResult auth = tx.lastAuth;
        if (auth == null) {
            return "online failed: no host response";
        }
        return auth.isApproved() ? "host approved"
                : auth.isFailed() ? "online failed: " + auth.getMessage() : "host declined";
    }

    /**
     * ISSUER_AUTHENTICATION with the host's answer for the card: {@link #onIssuerAuthentication}
     * runs the 2nd GENERATE AC (contact) or the 2nd-tap check (contactless). If the step doesn't
     * run (the transaction was cancelled meanwhile), the card still gets its 2nd GENERATE AC
     * here, as EMV Book 3 requires whatever the host said (see AET-146 below).
     */
    private void runIssuerAuthentication(OnlineResultWrapper online) {
        tx.issuerResponse = online;
        goToStep(EmvStep.ISSUER_AUTHENTICATION, issuerAuthenticationDetail());
        if (tx.issuerResponse != null) {
            engine().apduTrace().note("EMV",
                    "issuer authentication step did not run — completing with the card anyway");
            completeIssuerAuthentication();
        }
    }

    private void completeIssuerAuthentication() {
        OnlineResultWrapper online = tx.issuerResponse;
        tx.issuerResponse = null;
        if (activeCard != null && activeCard.isContactless()) {
            completeContactlessIssuerAuthentication(online);
        } else {
            completeContactIssuerAuthentication(online);
        }
    }

    /** Field 55 tags come from whichever PAX kernel ran this card — see {@link #readKernelTlv}. */
    @Override
    public byte[] readIccTag(int tag) {
        byte[] value = readKernelTlv(tag);
        if (tag == TagsTable.AMOUNT_OTHER && (value == null || value.length == 0)) {
            // The kernel filled 9F03 with zeros in GENERATE AC (CDOL1) but doesn't return it.
            return amountOtherBcd();
        }
        return value;
    }

    /**
     * Chip/contactless only: the PAN, online PIN block and {@link EmvTransactionResult} read from
     * the kernel, next to the field 55 {@link #authorizeOnline} already attached. Magstripe and
     * manual send the config as it is.
     */
    @Override
    protected TransactionConfig addOnlineRequestData(TransactionConfig config) {
        if (activeCard == null || isSynchronousEntry(activeCard)) {
            return config;
        }
        boolean contactless = activeCard.isContactless();
        String pan = contactless ? getContactlessPan() : getContactPan();
        String track2 = contactless ? getContactlessTrack2Data() : getContactTrack2Data();
        String cardHolderName = contactless ? getContactlessCardholderName() : getContactCardholderName();
        String onlinePinBlockHex = tx.lastOnlinePinBlock != null
                ? ConvertUtils.bcd2Str(tx.lastOnlinePinBlock, tx.lastOnlinePinBlock.length)
                : null;
        return config.withPan(pan)
                .withOnlinePinBlock(onlinePinBlockHex)
                .withOnlinePinKeyEncrypted(tx.lastOnlinePinKeyEncrypted)
                .withEmvResult(buildEmvTransactionResult(pan, track2, cardHolderName,
                        config.getIccData(), onlinePinBlockHex, contactless));
    }

    /**
     * Assembles the full EMV-kernel read right where {@link #startOnlineProcess} is about to go
     * online — vendor-agnostic mirror of the old project's {@code PosTransactionResult} (there,
     * built from a vendor SDK's EMV callback; here, built from direct kernel TLV reads plus the
     * {@link TransResult}/{@link CvmResultEnum} this class already captured earlier in the same
     * stage — see {@link #startContactTransProcess}/{@link #startContactlessTransProcess}, both of
     * which run before {@link #startOnlineProcess} is ever reached). See {@link EmvTransactionResult}.
     *
     * <p>{@link EmvTransactionResult#getIssuerName()} has no real source: this codebase has no
     * BIN-range/issuer-identification table (the old project's own source for one was never shared
     * either), so the EMV Application Preferred Name — falling back to the Application Label — is
     * reused as a best-available proxy; it's usually brand text like "VISA CREDIT" or
     * "MASTERCARD", which is what the old project's {@code SaleExtraData}/DCC lookups actually
     * keyed off of. Confirm against the server team before relying on this for real issuer routing.
     */
    private EmvTransactionResult buildEmvTransactionResult(@Nullable String pan, @Nullable String track2,
            @Nullable String cardHolderName, @Nullable String chipData,
            @Nullable String onlinePinBlockHex, boolean contactless) {
        CvmResultEnum cvmResult = contactless
                ? (tx.clsTransResult != null ? tx.clsTransResult.getCvmResult() : CvmResultEnum.CVM_NO_CVM)
                : (tx.transResult != null ? tx.transResult.getCvmResult() : CvmResultEnum.CVM_NO_CVM);
        // An online PIN CVM with no PIN block was bypassed: no PIN.
        boolean hasPin = onlinePinBlockHex != null
                || cvmResult == CvmResultEnum.CVM_OFFLINE_PIN;

        String cid = readKernelTlvHex(TagsTable.CRYPTO);
        int cidValue = cid != null && !cid.isEmpty() ? Integer.parseInt(cid, 16) : -1;
        boolean isArqc = (cidValue & 0xC0) == 0x80;
        boolean isTc = (cidValue & 0xC0) == 0x40;
        String cryptogram = readKernelTlvHex(TagsTable.APP_CRYPTO);

        String expDateRaw = readKernelTlvHex(TagsTable.EXPIRE_DATE);
        String expDate = expDateRaw != null && expDateRaw.length() >= 4 ? expDateRaw.substring(0, 4) : null;

        String emvAppLabel = readKernelAsciiTlv(TagsTable.APP_LABEL);
        String emvAppName = readKernelAsciiTlv(TagsTable.APP_NAME);
        // The old app's issuerName was the Application Preferred Name ("MasterCard"), not the
        // label ("MASTERCARD").
        String issuerName = emvAppName != null ? emvAppName : emvAppLabel;

        return new EmvTransactionResult.Builder()
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
                .arqc(isArqc ? cryptogram : null)
                .tc(isTc ? cryptogram : null)
                .issuerName(issuerName)
                .hasPin(hasPin)
                .entryMethod(contactless ? EntryMethod.CONTACTLESS : EntryMethod.CHIP)
                .build();
    }

    /** {@code null} pan (kernel hasn't read one) masks to {@code null} rather than throwing. */
    @Nullable
    private static String maskPan(@Nullable String pan) {
        if (pan == null || pan.length() <= 10) {
            return pan;
        }
        int visibleTail = 4;
        int visibleHead = 6;
        StringBuilder masked = new StringBuilder(pan.length());
        masked.append(pan, 0, visibleHead);
        for (int i = visibleHead; i < pan.length() - visibleTail; i++) {
            masked.append('*');
        }
        masked.append(pan.substring(pan.length() - visibleTail));
        return masked.toString();
    }

    /**
     * Unlike {@link #readKernelTlvHex} (numeric/binary tags rendered as hex), {@code APP_LABEL}/
     * {@code APP_NAME} are ASCII text per the EMV spec — decoded as such rather than hex-dumped so
     * {@link EmvTransactionResult#getEmvAppLabel()}/{@link EmvTransactionResult#getEmvAppName()}
     * carry human-readable text, matching how the old project's {@code PosTransactionResult} used
     * them (display strings, not hex blobs).
     */
    @Nullable
    private String readKernelAsciiTlv(int tag) {
        byte[] value = readKernelTlv(tag);
        return value.length == 0 ? null : asciiText(value);
    }

    /** {@link ContactProcess#getTlv}/{@link ClssProcess#getTlv} both return only value bytes. */
    private byte[] readKernelTlv(int tag) {
        if (activeCard != null && activeCard.isContactless()) {
            return kernel.contactless.getTlv(tag);
        }
        return kernel.contact.getTlv(tag);
    }

    // [CONTACTLESS]
    @Override
    public void onRemoveCard() {
        LogUtils.d(TAG, "remove card");
    }

    // [CONTACTLESS]
    @Override
    public void onDetect2ndTap() {
        // Visa issuer update needs the same card tapped a second time — show the card UI again
        // with the reason, like every other re-presentment (KEY_RETRY_PROMPT).
        String prompt = "Tap the same card again to finish";
        engine().apduTrace().note("PICC", "2nd tap requested — " + prompt);
        publish(TransactionStepEvent.builder(
                TransactionStep.WAITING_FOR_CARD).message(prompt)
                .put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt).build());
        goToStep(EmvStep.SEARCH_CARD, prompt);
    }

    // [CONTACTLESS]
    @Override
    public boolean needSeePhone() {
        boolean seePhone = clsLastNeedSeePhone;
        clsLastNeedSeePhone = false;
        return seePhone;
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
    public void offlineApproved(boolean needSignature) {
        LogUtils.e(TAG, "Offline approval reached without the host — reporting online failed");
        engine().apduTrace().note("EMV", "offline approval without the host — online failed");
        finishWithOutcome(TransactionStep.ERROR, "Online Failed",
                "Online Failed: not sent to the host");
    }

    // [SHARED — 2-arg overload; contact's checkContactResult() calls this one specifically]
    public void offlineApproved(boolean needSignature, boolean needSetARC) {
        offlineApproved(needSignature);
    }

    // [SHARED]
    public void onlineApproved(boolean needSignature) {
        completeApproved("RESULT_ONLINE_APPROVED", true);
    }

    // [SHARED]
    public void onlineDenied() {
        goToStep(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
        completeDeclined("Online Denied");
    }

    // [SHARED]
    public void onlineCardDenied(int resultCode) {
        goToStep(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
        completeDeclined("Online Card Denied code=" + resultCode);
    }

    // [SHARED]
    public void onlineFailed() {
        String reason = tx.lastAuth != null && tx.lastAuth.isFailed() && tx.lastAuth.getMessage() != null
                ? "Online Failed: " + tx.lastAuth.getMessage()
                : "Online Failed: no host response";
        finishWithOutcome(TransactionStep.ERROR, "Online Failed", reason);
    }

    // [SHARED]
    public void offlineDenied(int resultCode) {
        completeDeclined("Offline Denied code=" + resultCode);
    }

    /**
     * "See Phone" outcome (Mastercard message 0x20, Visa CLSS_REFER_CONSUMER_DEVICE): the phone
     * wants the cardholder to verify on it (unlock / Face ID / fingerprint) and tap again. Not a
     * decline — EMV contactless Book A's "end application with restart": show the prompt and
     * restart the same transaction, contactless only, waiting for the re-tap. Shares
     * {@link #MAX_TAP_RETRIES} with the other tap-again retries, so a phone that keeps asking
     * still ends in a decline.
     */
    // [CONTACTLESS — only checkContactlessResult() calls this; no CDCVM concept in contact]
    public void seePhone() {
        int retries = engine().getRetryCount();
        if (retries >= MAX_TAP_RETRIES) {
            engine().apduTrace().note("PICC", "SEE PHONE again after " + retries
                    + " retries — declining");
            completeDeclined("See Phone: verification on the phone not completed");
            return;
        }
        engine().apduTrace().note("PICC", "SEE PHONE — verify on the phone, then tap again");
        retryWithMode(EntryMethod.CONTACTLESS, "See Phone: waiting for the re-tap",
                "See phone — verify on your phone, then tap again");
    }

    /** Scheme declined the contactless attempt outright (e.g. low-value rules) — retry contact. */
    // [CONTACTLESS — only checkContactlessResult() calls this]
    public void tryAnotherInterface() {
        retryWithMode(EntryMethod.CHIP,
                "Try Another Interface: retrying with contact", "Insert the card");
    }

    /** Incomplete/glitchy tap (card pulled early, read error) — re-present the same interface. */
    // [CONTACTLESS — only checkContactlessResult() calls this]
    public void tryAgain() {
        int retries = engine().getRetryCount();
        if (retries >= MAX_TAP_RETRIES) {
            // A card that always fails ends in a decline, not an endless "tap again".
            LogUtils.w(TAG, "Try Again after " + retries + " retries — declining");
            engine().apduTrace().note("PICC", "card not read after " + retries
                    + " retries — declining");
            completeDeclined("Card not read — tried " + (retries + 1) + " times");
            return;
        }
        EntryMethod mode = activeConfig != null ? activeConfig.getMode()
                : EntryMethod.ANY;
        retryWithMode(mode, "Try Again: re-presenting card",
                "Card not read — tap again and hold the card still");
    }

    /** Chip read failed in a way EMV fallback rules require — retry magstripe. */
    // [CONTACT — only checkContactResult() calls this]
    public void fallback() {
        retryWithMode(EntryMethod.MAGSTRIPE, "Fallback: retrying with magstripe",
                "Chip not readable — swipe the card");
    }

    /**
     * Requests a same-transaction retry through {@link EmvEngine#requestRetry} — never a new
     * {@code startTransaction()} call, so it can't race the attempt that's still unwinding.
     */
    private void retryWithMode(EntryMethod mode, String reason, String prompt) {
        if (activeConfig == null) {
            finishError(reason + " — no active transaction");
            return;
        }
        LogUtils.i(TAG, reason);
        engine().requestRetry(activeConfig.withMode(mode), prompt);
    }

    // [SHARED — both checkContactResult() and checkContactlessResult() call this]
    public void simpleFlowEnd() {
        completeApproved("RESULT_SIMPLE_FLOW_END", false);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void completeApproved(String result, boolean scripts) {
        if (scripts && !tx.scriptsProcessed) {
            goToStep(EmvStep.SCRIPT_PROCESSING, null);
        }
        finishWithOutcome(TransactionStep.APPROVED, result, result);
    }

    private void completeDeclined(String reason) {
        finishWithOutcome(TransactionStep.DECLINED, reason, reason);
    }

    /**
     * Ends a chip/contactless transaction through its TRANSACTION_COMPLETION step:
     * {@link #onTransactionCompletion} reports the outcome. A transaction that has already ended
     * (an error earlier in the chain) drops the step, so it never reports a second outcome.
     */
    private void finishWithOutcome(TransactionStep step, String stepDetail, String message) {
        tx.outcome = new Outcome(step, message);
        goToStep(EmvStep.TRANSACTION_COMPLETION, stepDetail);
        tx.outcome = null;
    }

    private void reportOutcome(Outcome outcome) {
        switch (outcome.step) {
            case APPROVED:
                finishApproved(buildOutcomeEvent(TransactionStep.APPROVED,
                        TransactionStepEvent.KEY_RESULT, outcome.message));
                break;
            case DECLINED:
                finishDeclined(buildOutcomeEvent(TransactionStep.DECLINED,
                        TransactionStepEvent.KEY_ERROR, outcome.message));
                break;
            default:
                finishError(outcome.message);
                break;
        }
    }

    /**
     * Builds the APPROVED/DECLINED event, same shape {@code finishApproved(String)}/
     * {@code finishDeclined(String)} would build, plus the audit snapshot
     * {@link #captureTransactionSummary()} stashed earlier — those two String-only methods have
     * nowhere to attach it, so chip/CLSS outcomes pass this event to their event overloads
     * instead. Mag/manual still use the String ones (see {@link #onTransactionCompletion}) — they
     * never call {@link #captureTransactionSummary()}, so their event simply has no
     * PAN/TVR/TAC/IAC/Field 55 to attach.
     */
    private TransactionStepEvent buildOutcomeEvent(TransactionStep step, String messageKey,
            String message) {
        return TransactionStepEvent.builder(step)
                .put(messageKey, message)
                .put(TransactionStepEvent.KEY_PAN, safe(tx.lastPan))
                .put(TransactionStepEvent.KEY_TVR, safe(tx.lastTvr))
                .put(TransactionStepEvent.KEY_TAC_DENIAL, safe(tx.lastTacDenial))
                .put(TransactionStepEvent.KEY_IAC_DENIAL, safe(tx.lastIacDenial))
                .put(TransactionStepEvent.KEY_ICC_DATA, safe(tx.lastIccData))
                .build();
    }

    private void resetTransactionSummary() {
        tx.lastPan = null;
        tx.lastTvr = null;
        tx.lastTacDenial = null;
        tx.lastIacDenial = null;
        tx.lastIccData = null;
    }

    /**
     * Captures PAN/TVR/TAC Denial/IAC Denial/Field 55 while the kernel session is still open —
     * right after the bundled EMVStartTrans/startTransProcess call returns, before
     * {@link #closeReaders}. Runs for every chip/contactless outcome, online or offline,
     * approved or declined; {@link #buildOutcomeEvent} reads these fields once
     * {@code checkContactResult()}/{@code checkContactlessResult()} maps the result to
     * {@link #completeApproved}/{@link #completeDeclined}.
     */
    private void captureTransactionSummary() {
        boolean contactless = activeCard != null && activeCard.isContactless();
        tx.lastPan = safe(contactless ? getContactlessPan() : getContactPan());
        tx.lastTvr = readKernelTlvHex(TagsTable.TVR);
        tx.lastTacDenial = readKernelTlvHex(TagsTable.TAC_DENIAL);
        tx.lastIacDenial = readKernelTlvHex(TagsTable.IAC_DENIAL);
        // startOnlineProcess() (called from inside startXxxTransProcess for an online outcome)
        // already built and attached this to activeConfig — reuse it instead of re-reading the
        // same kernel tags a second time. Offline outcomes never call it, so build fresh here.
        tx.lastIccData = activeConfig != null && activeConfig.getIccData() != null
                ? activeConfig.getIccData() : buildField55();
    }

    @Nullable
    private String readKernelTlvHex(int tag) {
        byte[] value = readKernelTlv(tag);
        return value.length == 0 ? null : ConvertUtils.bcd2Str(value, value.length);
    }


    @NonNull
    private static OnlineResultWrapper toOnlineResultWrapper(@Nullable AuthResult auth) {
        OnlineResultWrapper wrapper = new OnlineResultWrapper();
        IssuerRspData rsp = new IssuerRspData();
        if (auth == null || auth.isFailed()) {
            wrapper.setResultCode(EOnlineResult.FAILED.getResultCode());
            wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_FAILED);
            rsp.setOnlineResult(EOnlineResult.FAILED.getEmvOnlineResult());
            wrapper.setIssuerRspData(rsp);
            return wrapper;
        }
        if (auth.isApproved()) {
            wrapper.setResultCode(EOnlineResult.APPROVE.getResultCode());
            wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_APPROVED);
            rsp.setOnlineResult(EOnlineResult.APPROVE.getEmvOnlineResult());
            if (auth.getAuthCode() != null) {
                rsp.setAuthCode(auth.getAuthCode().getBytes(StandardCharsets.US_ASCII));
            }
            if (auth.getResponseCode() != null) {
                rsp.setRespCode(auth.getResponseCode().getBytes(StandardCharsets.US_ASCII));
            }
            if (auth.getArpc() != null) {
                rsp.setAuthData(auth.getArpc());
            }
            if (auth.getIssuerScript() != null) {
                rsp.setScript(auth.getIssuerScript());
            }
        } else {
            wrapper.setResultCode(EOnlineResult.DENIAL.getResultCode());
            wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_DENIED);
            rsp.setOnlineResult(EOnlineResult.DENIAL.getEmvOnlineResult());
            if (auth.getResponseCode() != null) {
                rsp.setRespCode(auth.getResponseCode().getBytes(StandardCharsets.US_ASCII));
            }
        }
        wrapper.setIssuerRspData(rsp);
        return wrapper;
    }

    /**
     * Closes mag/icc/picc independently — each in its own try/catch — so one reader's close
     * failing (e.g. a reader already in a bad state) can't skip the rest. The previous
     * single-try version stopped at the first exception, silently leaving whatever came after it
     * open; that's the likely root cause of a "reader already open" failure on the next attempt.
     */
    private void closeReaders(boolean contactless) {
        if (EmvFlowRuntime.getDal() == null) {
            return;
        }
        try {
            EmvFlowRuntime.getDal().getMag().close();
        } catch (Exception e) {
            LogUtils.e(TAG, "close mag reader failed", e);
        }
        try {
            EmvFlowRuntime.getDal().getIcc().close((byte) 0);
        } catch (Exception e) {
            LogUtils.e(TAG, "close icc reader failed", e);
        }
        try {
            EmvFlowRuntime.getDal().getPicc(EPiccType.INTERNAL).close();
        } catch (Exception e) {
            LogUtils.e(TAG, "close internal picc reader failed", e);
        }
        if (contactless) {
            try {
                EmvFlowRuntime.getDal().getPicc(EPiccType.EXTERNAL).close();
            } catch (Exception e) {
                LogUtils.e(TAG, "close external picc reader failed", e);
            }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(new Date());
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }

    private static String panFromTrack2(String track2) {
        int sep = track2.indexOf('=');
        if (sep <= 0) {
            sep = track2.indexOf('D');
        }
        return sep > 0 ? track2.substring(0, sep) : track2;
    }
}

package com.emvenhance.vendor.pax;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.AbstractEmvBehavior;
import com.emvenhance.emvflow.device.EmvDeviceImpl;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.bizentity.entity.SearchMode;
import com.pax.bizlib.card.TrackUtils;
import com.pax.commonlib.currency.CurrencyConverter;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.EPiccType;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvbase.constant.TagsTable;
import com.pax.emvbase.param.EmvProcessParam;
import com.pax.emvbase.param.EmvTransParam;
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
import com.pax.emvservice.export.contact.IContactResultListener;
import com.pax.emvservice.export.contactless.IContactlessResultListener;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.device.DeviceManager;
import com.pax.poslib.gl.convert.ConvertHelper;
import com.pax.poslib.model.ModelInfo;
import java.nio.charset.StandardCharsets;
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
 *       {@link #runContactCardAuth} → {@link #runContactStartTransaction}) advances by calling
 *       {@code goToStep(EmvStep.NEXT)} rather than the next run* method directly, so
 *       {@code goToStep} both publishes that step on the observable and dispatches to its onXxx
 *       method (e.g. {@link #onReadApplicationData}), which hands straight back to the matching
 *       run* method for chip. Chaining continues only while
 *       {@link #isContactTransactionFinished()} stays false; APPLICATION_SELECTION itself is
 *       entered the normal way, via the framework's own goToStep call that invoked
 *       {@link #onApplicationSelection} in the first place. Sub-phases with no kernel callback
 *       and no dedicated stage of their own (terminal risk management, terminal action analysis
 *       — both folded into {@link #runContactStartTransaction}'s EMVStartTrans call) still can't
 *       be announced individually; CVM only gets announced when the kernel actually asks for it,
 *       via {@code onCardHolderPwd} below. Contactless: {@link #onApplicationSelection} starts
 *       the PAX kernel directly — its native API has no equivalent per-stage entry points, so it
 *       isn't split out, and its steps are still announced purely from kernel callbacks, never
 *       through goToStep.</li>
 * </ul>
 *
 * <p><b>Experiment branch:</b> talks to {@link ContactProcess} (emvlib:dpas) directly — no
 * {@code EmvContactService} service layer. Everything that used to live there — per-transaction
 * state ({@link #transResult}, {@link #cachedTrack2Data}), the online-processing decision inside
 * {@link #startContactTransProcess}, PAN/track2 derivation ({@link #getContactPan}), and the
 * result-to-callback mapping in {@link #checkContactResult()} — now lives here instead. The
 * contactless path got the same treatment: talks to {@link ClssProcess} (emvlib) directly — no
 * {@code ContactlessService} layer — with its own mirrored state
 * ({@link #clsTransResult}/{@link #clsCachedTrack2Data}/{@link #clsLastNeedSeePhone}), online
 * decision ({@link #startContactlessTransProcess}), PAN derivation
 * ({@link #getContactlessPan}), and result mapping ({@link #checkContactlessResult()}). See the
 * structure diagram (panel 10/11) for why this was tried and what it cost.
 *
 * <p><b>Sub-branch on top of the above:</b> {@link ContactProcess} (and its inner
 * {@code EmvCallBackListener}) now also holds a direct {@link EmvEngine} reference, set via
 * {@link ContactProcess#setEngine} alongside every {@code registerEmvProcessListener} call
 * below. This is a second, parallel path to the engine — {@code IContactCallback}
 * (this class) is still the primary one. Only one call site currently uses it
 * ({@code emvSetParam}, read-only — logs {@code engine.isRunning()}, no {@code notifyXxx} call),
 * specifically because it's the one native mid-call callback with no existing path to either
 * {@code emvProcessListener} or an {@code EmvStep}. Adding a real {@code notifyXxx}/
 * {@code announceStep} call from inside {@code EmvCallBackListener} for a callback that
 * <em>already</em> reaches {@code emvProcessListener} (e.g. {@code emvWaitAppSel},
 * {@code emvGetHolderPwd}) would fire that event twice — once from here, once from the existing
 * {@code onXxx} override — so deliberately not done.
 */
public class PaxEmvBehavior extends AbstractEmvBehavior
        implements IContactCallback, IContactlessCallback,
        IContactResultListener, IContactlessResultListener {

    private static final String TAG = "PaxEmvBehavior";

    /** See {@link #startContactlessTransProcess} javadoc for why this is a constant. */
    private static final int CONTACTLESS_FLOW_TYPE = EmvTransParam.FLOWTYPE_COMPLETE;

    private final PaxKernel kernel;

    private boolean initOk = true;

    @Nullable
    private AuthResult lastAuth;

    @Nullable
    private TransResult transResult;
    @Nullable
    private String cachedTrack2Data;

    @Nullable
    private TransResult clsTransResult;
    @Nullable
    private String clsCachedTrack2Data;
    private boolean clsLastNeedSeePhone;

    public PaxEmvBehavior(PaxKernel kernel) {
        this.kernel = kernel;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        initOk = true;
        lastAuth = null;
        super.prepare(engine, config);
        return initOk && !isCancelled() && engine.isRunning();
    }

    @Override
    public void onTerminalInitialization(EmvEngine engine, TransactionConfig config) {
        if (!prepareKernel(config)) {
            initOk = false;
            finishError("Terminal initialization failed");
        }
        // Do not goToStep — PosTerminal.searchCard runs next.
    }

    @Override
    protected EmvStep firstStepAfterSearch(CardPresence card) {
        if (card.isMagstripe() || card.isManual()) {
            return EmvStep.READ_APPLICATION_DATA;
        }
        // Chip / CLSS: enter APPLICATION_SELECTION → starts PAX kernel
        return EmvStep.APPLICATION_SELECTION;
    }

    // ─── Step methods (goToStep / kernel start) ──────────────────────────

    @Override
    public void onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isChip()) {
            runContactAppSelect();
            return;
        }
        if (card.isContactless()) {
            runContactlessKernel();
            return;
        }
        // Mag/manual should not land here
        goToStep(EmvStep.READ_APPLICATION_DATA);
    }

    @Override
    public void onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isManual()) {
            String pan = card.getManualPan() != null ? card.getManualPan() : "";
            if (pan.isEmpty()) {
                finishError("Manual entry requires a PAN");
                return;
            }
            engine.notifyCardDetected(pan, "MANUAL", "", card.getModeLabel());
            engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            goToStep(EmvStep.START_ONLINE_PROCESS, "manual");
            return;
        }
        if (card.isMagstripe()) {
            String track2 = card.getTrack2() != null ? card.getTrack2() : "";
            String pan = panFromTrack2(track2);
            engine.notifyCardDetected(pan, "MAG", "", card.getModeLabel());
            engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            goToStep(EmvStep.START_ONLINE_PROCESS, "magstripe");
            return;
        }
        if (card.isChip()) {
            // Reached via runContactAppSelect's goToStep(READ_APPLICATION_DATA) — never via the
            // framework's own cancellation short-circuit in goToStep, which returns before
            // dispatchStepMethod runs at all.
            runContactReadAppData();
        }
        // CLSS: still announced purely from kernel callbacks — nothing to chain here.
    }

    @Override
    public void onStartOnlineProcess(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Mag / manual sync path
        if (card.isMagstripe() || card.isManual()) {
            if (isCancelled()) {
                finishError("Cancelled");
                return;
            }
            lastAuth = engine.authorize(config);
            goToStep(EmvStep.TRANSACTION_COMPLETION);
            return;
        }
        // Chip/CLSS: startOnlineProcess() kernel callback handles host authorize.
    }

    @Override
    public void onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Mag / manual sync path
        if (card.isMagstripe() || card.isManual()) {
            if (lastAuth != null && lastAuth.isApproved()) {
                String msg = card.isManual() ? "MANUAL ONLINE APPROVED" : "MAG ONLINE APPROVED";
                finishApproved(msg);
            } else {
                finishDeclined(lastAuth != null && lastAuth.getMessage() != null
                        ? lastAuth.getMessage() : "DECLINED");
            }
            return;
        }
        // Chip/CLSS: result listeners call completeApproved / completeDeclined.
    }

    // Chip: reached via goToStep from the previous run* stage — dispatchStepMethod invokes
    // these, and each hands off to its run* method. See the class doc and runContactAppSelect
    // for the full chain.

    @Override
    public void onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isChip()) {
            // Reached via runContactReadAppData's goToStep(OFFLINE_DATA_AUTHENTICATION).
            runContactCardAuth();
        }
        // CLSS: kernel-internal — no callback exists; not individually announced.
    }

    @Override
    public void onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isChip()) {
            // Reached via runContactCardAuth's goToStep(PROCESS_RESTRICTIONS) — the entry point
            // into runContactStartTransaction's bundled EMVStartTrans call (see class doc).
            runContactStartTransaction();
        }
        // CLSS: kernel-internal — no callback exists; not individually announced.
    }

    // ─── Not reached via onXxx dispatch — required overrides, not stubs ──
    //
    // Chip/CLSS: PAX's own kernel (startContactTransProcess/startContactlessTransProcess)
    // runs these phases internally and reports them through announceStep(), called from
    // the IContactCallback / IContactResultListener methods further below — never through
    // goToStep, so dispatchStepMethod never invokes the onXxx form for this path.
    //
    // Mag/manual: these EMV data phases don't apply. onApplicationSelection and
    // onReadApplicationData above jump straight past them with explicit goToStep targets.
    //
    // EmvBehavior still requires an override for each — a compile error beats a vendor author
    // forgetting a phase and inheriting a silent no-op.

    @Override
    public void onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // See IContactCallback#onWaitAppSelect below for the chip/CLSS path.
    }

    @Override
    public void onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // See IContactCallback#onWaitAppSelect below.
    }

    @Override
    public void onSetTransactionData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // See IContactCallback#confirmCard / #showConfirmCard below.
    }

    @Override
    public void onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // See IContactCallback#onCardHolderPwd below.
    }

    @Override
    public void onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // See IContactCallback#onCardHolderPwd below.
    }

    @Override
    public void onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Kernel-internal — no callback exists; not individually announced.
    }

    @Override
    public void onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Kernel-internal — no callback exists; not individually announced.
    }

    @Override
    public void onIssuerAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // See IContactCallback#startOnlineProcess below.
    }

    @Override
    public void onScriptProcessing(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Kernel-internal — applied inside completeApproved()'s script-processing announce.
    }

    // ─── Kernel runners ──────────────────────────────────────────────────

    private void runContactlessKernel() {
        requireEngine();
        ClssProcess process = kernel.contactless;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contactless EMV ============");
            int ret = startContactlessTransProcess(process);
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless execution failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contactless EMV failed");
        } finally {
            process.unregisterClssProcessListener();
            closeReaders(true);
            if (!isCancelled()) {
                try {
                    checkContactlessResult();
                } catch (Exception e) {
                    LogUtils.e(TAG, "checkClsResult error", e);
                    finishError(e.getMessage() != null ? e.getMessage() : "checkClsResult failed");
                }
            }
        }
    }

    /**
     * Runs the full contactless kernel transaction — app selection through 1st GAC, CVM prompt,
     * and (if the kernel requests it) the online-authorization round trip including the 2nd-tap
     * check. Ported from {@code ContactlessService#startTransProcess}.
     *
     * <p>{@code CONTACTLESS_FLOW_TYPE} mirrors the one hardcoded value {@link #runPreTransProcess}
     * ever builds ({@code EmvTransParam.FLOWTYPE_COMPLETE}) — verified repo-wide, nothing ever
     * requests {@code FLOWTYPE_SIMPLE} for contactless — so the branch below is unreachable today,
     * kept only for parity with the ported logic in case that ever changes. {@link ClssProcess}
     * itself has no getter for the flow type it was built with, and per its own header comment it
     * shouldn't be touched to add one.
     */
    private int startContactlessTransProcess(ClssProcess process) {
        // reset every transaction
        clsCachedTrack2Data = null;
        process.registerClssProcessListener(this);
        clsTransResult = process.startTransProcess();
        CvmResultEnum cvmResult = clsTransResult.getCvmResult();
        int resultCode = clsTransResult.getResultCode();
        TransResultEnum transResultEnum = clsTransResult.getTransResult();
        if (resultCode != RetCode.EMV_OK) {
            return resultCode;
        }
        int ret = confirmCard();
        LogUtils.d(TAG, "confirm card: " + ret);
        if (ret != RetCode.EMV_OK) {
            // for example, timeout/data_error
            clsTransResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            return ret;
        }
        if (CONTACTLESS_FLOW_TYPE == EmvTransParam.FLOWTYPE_SIMPLE) {
            clsTransResult = new TransResult(0, TransResultEnum.RESULT_SIMPLE_FLOW_END, CvmResultEnum.CVM_NO_CVM);
            return 0;
        }
        if (cvmResult == CvmResultEnum.CVM_ONLINE_PIN || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
            ret = onCardHolderPwd(true, true, 0, null);
            // Because PIN Bypass is supported, it is necessary to exclude the case where the
            // returned result is NO PASSWORD.
            if (ret != RetCode.EMV_OK && ret != RetCode.EMV_NO_PASSWORD) {
                clsTransResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, cvmResult);
                return ret;
            }
        }
        // check whether need goes online
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            OnlineResultWrapper onlineResultWrapper = startOnlineProcess();
            IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
            TransResultEnum onlineTransResultEnum = onlineResultWrapper.getTransResultEnum();
            // handle online result after second GAC
            if (TransResultEnum.RESULT_ONLINE_APPROVED != onlineTransResultEnum) {
                clsTransResult.setTransResult(onlineTransResultEnum);
                clsTransResult.setResultCode(onlineTransResultEnum.ordinal());
                return onlineTransResultEnum.ordinal();
            }
            boolean needSecondTap = process.isNeedSecondTap(issuerRspData);
            if (needSecondTap) {
                onDetect2ndTap();
                clsTransResult = process.completeTransProcess(issuerRspData);
            } else {
                clsTransResult = new TransResult(RetCode.EMV_OK, TransResultEnum.RESULT_ONLINE_APPROVED, CvmResultEnum.CVM_NO_CVM);
            }
            // restore signature
            if (cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
                clsTransResult.setCvmResult(CvmResultEnum.CVM_SIG);
            }
        }
        return clsTransResult.getResultCode();
    }

    /**
     * Maps the recorded {@link #clsTransResult} to the matching {@code IContactlessResultListener}
     * callback on {@code this} — ported from {@code ContactlessService#checkClsResult}.
     */
    private void checkContactlessResult() {
        if (clsTransResult == null) {
            LogUtils.e(TAG, "check result: no clsTransResult recorded — treating as offline denied");
            offlineDenied(-1);
            return;
        }
        int resultCode = clsTransResult.getResultCode();
        TransResultEnum transResultEnum = clsTransResult.getTransResult();
        CvmResultEnum cvmResult = clsTransResult.getCvmResult();
        LogUtils.d(TAG, "result code: " + resultCode
                + ", trans result: " + transResultEnum.name()
                + ", cvm result: " + cvmResult.name());
        if (resultCode == RetCode.EMV_OK) {
            if (cvmResult == CvmResultEnum.CVM_CONSUMER_DEVICE) {
                clsLastNeedSeePhone = true;
                seePhone();
                return;
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
            if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE) {
                clsLastNeedSeePhone = true;
                seePhone();
            } else if (transResultEnum == TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE
                    || resultCode == RetCode.CLSS_USE_CONTACT) {
                // restart detect icc card and transaction
                tryAnotherInterface();
            } else if (transResultEnum == TransResultEnum.RESULT_TRY_AGAIN) {
                // PICC return USE_CONTACT 1.restart detect card and transaction
                tryAgain();
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
        if (clsCachedTrack2Data != null && !clsCachedTrack2Data.isEmpty()) {
            return TrackUtils.getPan(clsCachedTrack2Data);
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
        clsCachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contactless.getTlv(TagsTable.TRACK2));
        return clsCachedTrack2Data;
    }

    /** Ported from {@code ContactlessService#getCardholderName}. */
    private String getContactlessCardholderName() {
        byte[] cardholderName = kernel.contactless.getTlv(TagsTable.CARDHOLDER_NAME);
        return ConvertUtils.bcd2Str(cardholderName);
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
     * <p>{@link #goToStep} itself returns early on cancellation, before dispatching at all —
     * which for every other goToStep transition in this class (mag/manual,
     * APPLICATION_SELECTION) is fine, since there's no vendor-owned resource left open to clean
     * up. For chip the ICC reader stays activated across the whole app-select → start-transaction
     * span, so {@link #advanceContactStage} re-checks {@link #isCancelled()} itself right before
     * calling goToStep and falls back to {@link #finishContactStage} instead, guaranteeing
     * closeReaders/checkContactResult still run even if cancel() lands in the narrow window
     * between one stage finishing and the next stage's transition.
     *
     * <p>Processing restrictions, cardholder verification, terminal risk management and
     * terminal action analysis stay bundled inside {@link #runContactStartTransaction}'s
     * EMVStartTrans call — the PAX kernel has no separate native entry point for each, so they
     * can't be split out any further.
     */
    private void runContactAppSelect() {
        requireEngine();
        ContactProcess process = kernel.contact;
        boolean proceed = false;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Contact EMV: App Select ============");
            // reset every transaction — mirrors EmvContactService#selectApplication
            cachedTrack2Data = null;
            process.registerEmvProcessListener(this);
            // Experiment branch: ContactProcess/EmvCallBackListener can now reach EmvEngine
            // directly too — see ContactProcess#setEngine.
            process.setEngine(requireEngine());
            transResult = process.selectApplication();
            int ret = transResult.getResultCode();
            LogUtils.d(TAG, "selectApplication ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
            if (!proceed) {
                // Selection failed — the transaction ends here; release the listener now since
                // startTransProcess() (and its own unregister) will never run.
                process.registerEmvProcessListener(null);
            }
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

    /** Read App Data stage — reached via {@link #onReadApplicationData}'s chip branch. */
    private void runContactReadAppData() {
        ContactProcess process = kernel.contact;
        boolean proceed = false;
        try {
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Contact EMV: Read App Data ============");
            transResult = process.readApplicationData();
            int ret = transResult.getResultCode();
            LogUtils.d(TAG, "readApplicationData ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
            if (!proceed) {
                process.registerEmvProcessListener(null);
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "contact read app data failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV read app data failed");
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
        ContactProcess process = kernel.contact;
        boolean proceed = false;
        try {
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Contact EMV: Card Auth ============");
            transResult = process.cardAuthentication();
            int ret = transResult.getResultCode();
            LogUtils.d(TAG, "cardAuthentication ret=" + ret);
            proceed = ret == RetCode.EMV_OK && !isContactTransactionFinished();
            if (!proceed) {
                process.registerEmvProcessListener(null);
            }
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
     * Advances to {@code next} via {@link #goToStep} — unless {@code cancel()} landed in the
     * window between a stage's own work finishing and this call, in which case {@code goToStep}
     * would silently swallow it: it returns before {@code dispatchStepMethod} runs at all, so
     * the next run* method (and its {@link #finishContactStage} cleanup) would never fire.
     * Re-checking {@link #isCancelled()} here and falling back to {@link #finishContactStage}
     * directly closes that window instead of leaving readers open / the result unreported.
     */
    private void advanceContactStage(EmvStep next) {
        if (isCancelled()) {
            finishContactStage();
        } else {
            goToStep(next);
        }
    }

    /**
     * Final stage — reached via {@link #onProcessRestrictions}'s chip branch. EMVStartTrans
     * (processing restrictions → CVM → terminal risk management → terminal action analysis →
     * 1st GAC, bundled — see class doc), then online processing if the kernel requested it.
     * Always terminal — always reports via {@link #finishContactStage}.
     */
    private void runContactStartTransaction() {
        requireEngine();
        ContactProcess process = kernel.contact;
        try {
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Contact EMV: Start Transaction ============");
            // CVM is separately announced from onCardHolderPwd below when the kernel actually
            // asks for it; terminal risk management and terminal action analysis have no
            // callback and stay unannounced (see class doc).
            int ret = startContactTransProcess(process);
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contact execution failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV failed");
        } finally {
            process.registerEmvProcessListener(null);
            finishContactStage();
        }
    }

    /**
     * Runs EMVStartTrans and, if the kernel requests it, the online-authorization round trip
     * (host authorize via {@link #startOnlineProcess()} → EMVCompleteTrans). Ported from
     * {@code EmvContactService#startTransProcess} — the AET-146 comment below is carried over
     * verbatim.
     */
    private int startContactTransProcess(ContactProcess process) {
        process.registerEmvProcessListener(this);
        process.setEngine(requireEngine());
        transResult = process.startTransProcess();
        int resultCode = transResult.getResultCode();
        TransResultEnum transResultEnum = transResult.getTransResult();
        if (resultCode != RetCode.EMV_OK) {
            return resultCode;
        }
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            OnlineResultWrapper onlineResultWrapper = startOnlineProcess();
            IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
            int onlineResultCode = onlineResultWrapper.getResultCode();
            transResult.setResultCode(onlineResultWrapper.getResultCode());
            transResult.setTransResult(onlineResultWrapper.getTransResultEnum());
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
            TransResult secondTransResult = process.completeTransProcess(issuerRspData);
            transResult.setResultCode(secondTransResult.getResultCode());
            if (onlineResultCode == EOnlineResult.APPROVE.getResultCode()) {
                transResult.setTransResult(secondTransResult.getTransResult() != TransResultEnum.RESULT_ONLINE_APPROVED
                        ? TransResultEnum.RESULT_ONLINE_CARD_DENIED : TransResultEnum.RESULT_ONLINE_APPROVED);
            } else if (onlineResultCode == EOnlineResult.FAILED.getResultCode()) {
                transResult.setTransResult(secondTransResult.getTransResult() != TransResultEnum.RESULT_ONLINE_APPROVED
                        ? TransResultEnum.RESULT_ONLINE_FAILED : TransResultEnum.RESULT_ONLINE_FAILED_CARD_APPROVED);
            } else {
                transResult.setTransResult(TransResultEnum.RESULT_ONLINE_DENIED);
            }
            return secondTransResult.getResultCode();
        }
        return 0;
    }

    /** True once a stage has recorded a terminal outcome — no further stage should run. */
    private boolean isContactTransactionFinished() {
        return transResult != null && transResult.getTransResult() != null;
    }

    /** Common tail once a contact stage chain has reached a terminal outcome. */
    private void finishContactStage() {
        closeReaders(false);
        if (!isCancelled()) {
            try {
                checkContactResult();
            } catch (Exception e) {
                LogUtils.e(TAG, "checkContactResult error", e);
                finishError(e.getMessage() != null ? e.getMessage() : "checkContactResult failed");
            }
        }
    }

    /**
     * Maps the recorded {@link #transResult} to the matching {@code IContactResultListener}
     * callback on {@code this} — ported from {@code EmvContactService#checkContactResult}.
     */
    private void checkContactResult() {
        if (transResult == null) {
            LogUtils.e(TAG, "check result: no transResult recorded — treating as offline denied");
            offlineDenied(-1);
            return;
        }
        int resultCode = transResult.getResultCode();
        TransResultEnum transResultEnum = transResult.getTransResult();
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
        CvmResultEnum cvmResult = transResult.getCvmResult();
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
                offlineApproved(isNeedSignature, false);
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
        if (cachedTrack2Data != null && !cachedTrack2Data.isEmpty()) {
            return TrackUtils.getPan(cachedTrack2Data);
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
        cachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contact.getTlv(TagsTable.TRACK2));
        return cachedTrack2Data;
    }

    /** Ported from {@code EmvContactService#getCardholderName}. */
    private String getContactCardholderName() {
        byte[] cardholderName = kernel.contact.getTlv(TagsTable.CARDHOLDER_NAME);
        return ConvertUtils.bcd2Str(cardholderName);
    }

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
            byte adjusted = runPreTransProcess(config, requested);
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
     * Builds {@link EmvTransParam}/{@link EmvProcessParam} from {@code config} and the cached
     * EMV parameters, then runs contact / contactless {@code preTransProcess}, clearing the
     * corresponding {@link SearchMode} bit for whichever one fails. Ported from
     * {@code EmvPreProcessFacade#start} — single caller, no service-locator indirection, and
     * {@code emvflow} (the module it used to live in) is PAX-only already, so nothing else needed
     * it as a separate object.
     */
    private byte runPreTransProcess(@NonNull TransactionConfig config, byte searchCardMode) {
        DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
        byte[] proc = ConvertHelper.getConvert().strToBcdPaddingRight(config.getProcCode());
        if (!SearchMode.isSupportIcc(searchCardMode) && !SearchMode.isWave(searchCardMode)) {
            return 0;
        }
        if (kernel.params == null) {
            LogUtils.e(TAG, "EmvParamService missing");
            return searchCardMode;
        }
        EmvProcessParam cachedEmvParam = kernel.params.getCachedEmvParam();
        String dateTime = timestamp();
        EmvTransParam.Builder builder = new EmvTransParam.Builder();
        builder.setTransType(proc[0])
                .setAmount(config.getAmountMinor())
                .setAmountOther(0L)
                .setTerminalID(ModelInfo.getInstance().getSN().getBytes())
                .setTransCurrencyCode(CurrencyConverter.getCurrencyCode())
                .setTransCurrencyExponent((byte) CurrencyConverter.getDigitsNum())
                .setTransDate(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(2, 8)))
                .setTransTime(ConvertUtils.strToBcdPaddingLeft(dateTime.substring(8)))
                .setTransTraceNo(Long.parseLong(ConvertUtils.getPaddedNumber(0, 6)))
                .setFlowType(EmvTransParam.FLOWTYPE_COMPLETE)
                .setMaskPattern("")
                .setPinLenSet("0,4,5,6,7,8,9,10,11,12\0".getBytes())
                .setPciTimeout(60 * 1000);
        EmvProcessParam.Builder processParamBuilder = new EmvProcessParam.Builder()
                .setTermConfig(cachedEmvParam.getTermConfig())
                .setCapkParam(cachedEmvParam.getCapkParam());

        if (SearchMode.isSupportIcc(searchCardMode) && kernel.contact != null) {
            builder.setPciMode((byte) 1);
            processParamBuilder.setEmvTransParam(builder.create())
                    .setEmvAidList(cachedEmvParam.getEmvAidList());
            int contactRet = kernel.contact.preTransProcess(processParamBuilder.create());
            if (contactRet != RetCode.EMV_OK) {
                LogUtils.e(TAG, "contact pre process failed");
                searchCardMode = (byte) (searchCardMode & (~SearchMode.INSERT));
            }
        }
        if ((SearchMode.isSupportInternalPicc(searchCardMode)
                || SearchMode.isSupportExternalPicc(searchCardMode)) && kernel.contactless != null) {
            if (!SearchMode.isSupportIcc(searchCardMode)) {
                processParamBuilder.setEmvTransParam(builder.create());
            }
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
                LogUtils.e(TAG, "contactless pre process failed");
                searchCardMode = (byte) (searchCardMode & (~SearchMode.WAVE));
            }
        }
        return searchCardMode;
    }

    // ─── IContactCallback + IContactlessCallback ─────────────────────────

    @Override
    public int showEnterTip() {
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public void onReadCardOk() {
        announceStep(EmvStep.READ_APPLICATION_DATA, null);
        requireEngine().notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"));
    }

    @Override
    public int confirmCard() {
        announceStep(EmvStep.SET_TRANSACTION_DATA, null);
        EmvEngine eng = requireEngine();
        eng.notifyCardDetected(safe(getContactlessPan()), "PAX Issuer",
                safe(getContactlessCardholderName()), "Contactless");
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList) {
        int candidates = candList == null ? 0 : candList.size();
        announceStep(isFirstSelect
                        ? EmvStep.WAIT_APPLICATION_SELECTION
                        : EmvStep.FINAL_APPLICATION_SELECTION,
                candidates + " candidate AID(s), selecting first");
        requireEngine().notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int showConfirmCard() {
        announceStep(EmvStep.SET_TRANSACTION_DATA, null);
        EmvEngine eng = requireEngine();
        eng.notifyCardDetected(safe(getContactPan()), "PAX Issuer",
                safe(getContactCardholderName()), "Contact");
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
            byte[] pinData) {
        announceStep(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
        if (!isOnlinePin) {
            announceStep(EmvStep.OFFLINE_PIN_VERIFICATION, null);
        }
        requireEngine().notifyTransactionStep(TransactionStepEvent.builder(
                TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, isOnlinePin)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, supportPINByPass)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, leftTimes)
                .build());
        if (supportPINByPass) {
            return EmvConstant.ContactCallbackStatus.NO_PASSWORD;
        }
        return EmvConstant.ContactCallbackStatus.USER_CANCEL;
    }

    @NonNull
    @Override
    public OnlineResultWrapper startOnlineProcess() {
        announceStep(EmvStep.START_ONLINE_PROCESS, null);
        AuthResult auth = requestOnline(requireEngine());
        lastAuth = auth;
        announceStep(EmvStep.ISSUER_AUTHENTICATION,
                auth.isApproved() ? "host approved" : "host declined");
        return toOnlineResultWrapper(auth);
    }

    @Override
    public void onRemoveCard() {
        LogUtils.d(TAG, "remove card");
    }

    @Override
    public void onDetect2ndTap() {
        requireEngine().notifyTransactionStep(TransactionStepEvent.of(
                TransactionStep.WAITING_FOR_CARD, "Present card again"));
    }

    @Override
    public boolean needSeePhone() {
        boolean seePhone = clsLastNeedSeePhone;
        clsLastNeedSeePhone = false;
        return seePhone;
    }

    // ─── Result listeners ────────────────────────────────────────────────

    @Override
    public void offlineApproved(boolean needSignature) {
        completeApproved("RESULT_OFFLINE_APPROVED", false);
    }

    @Override
    public void offlineApproved(boolean needSignature, boolean needSetARC) {
        completeApproved("RESULT_OFFLINE_APPROVED", false);
    }

    @Override
    public void onlineApproved(boolean needSignature) {
        completeApproved("RESULT_ONLINE_APPROVED", true);
    }

    @Override
    public void onlineDenied() {
        announceStep(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
        completeDeclined("Online Denied");
    }

    @Override
    public void onlineCardDenied(int resultCode) {
        announceStep(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
        completeDeclined("Online Card Denied code=" + resultCode);
    }

    @Override
    public void onlineFailed() {
        announceStep(EmvStep.TRANSACTION_COMPLETION, "Online Failed");
        finishError("Online Failed: no host response");
    }

    @Override
    public void offlineDenied(int resultCode) {
        completeDeclined("Offline Denied code=" + resultCode);
    }

    /**
     * Not a failure — the kernel wants the cardholder to complete CDCVM on their phone.
     * Whether the transaction can still conclude afterward, or this is terminal, is a
     * PAX/scheme-behavior question this codebase doesn't yet answer; treated as a hard stop
     * until that's confirmed, unlike the three retry signals below.
     */
    @Override
    public void seePhone() {
        finishError("See Phone: Continue on the phone");
    }

    /** Scheme declined the contactless attempt outright (e.g. low-value rules) — retry contact. */
    @Override
    public void tryAnotherInterface() {
        retryWithMode(EntryMethod.CHIP,
                "Try Another Interface: retrying with contact");
    }

    /** Incomplete/glitchy tap (card pulled early, read error) — re-present the same interface. */
    @Override
    public void tryAgain() {
        EntryMethod mode = activeConfig != null ? activeConfig.getMode()
                : EntryMethod.ANY;
        retryWithMode(mode, "Try Again: re-presenting card");
    }

    /** Chip read failed in a way EMV fallback rules require — retry magstripe. */
    @Override
    public void fallback() {
        retryWithMode(EntryMethod.MAGSTRIPE, "Fallback: retrying with magstripe");
    }

    /**
     * Requests a same-transaction retry through {@link EmvEngine#requestRetry} — never a new
     * {@code startTransaction()} call, so it can't race the attempt that's still unwinding.
     */
    private void retryWithMode(EntryMethod mode, String reason) {
        if (isCancelled() || activeConfig == null) {
            finishError(reason + " — cancelled");
            return;
        }
        LogUtils.i(TAG, reason);
        requireEngine().requestRetry(activeConfig.withMode(mode));
    }

    @Override
    public void simpleFlowEnd() {
        completeApproved("RESULT_SIMPLE_FLOW_END", false);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void completeApproved(String result, boolean scripts) {
        if (scripts) {
            announceStep(EmvStep.SCRIPT_PROCESSING, null);
        }
        announceStep(EmvStep.TRANSACTION_COMPLETION, result);
        finishApproved(result);
    }

    private void completeDeclined(String reason) {
        announceStep(EmvStep.TRANSACTION_COMPLETION, reason);
        finishDeclined(reason);
    }

    @NonNull
    private AuthResult requestOnline(@NonNull EmvEngine eng) {
        if (isCancelled()) {
            return AuthResult.declined("17", "Cancelled");
        }
        return eng.authorize(activeConfig);
    }

    @NonNull
    private EmvEngine requireEngine() {
        if (engine == null) {
            throw new IllegalStateException("PaxEmvBehavior used without an active engine");
        }
        return engine;
    }

    @NonNull
    private static OnlineResultWrapper toOnlineResultWrapper(@Nullable AuthResult auth) {
        OnlineResultWrapper wrapper = new OnlineResultWrapper();
        IssuerRspData rsp = new IssuerRspData();
        if (auth == null) {
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
            if (auth.getIssuerData() != null) {
                rsp.setScript(auth.getIssuerData());
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

    private void closeReaders(boolean contactless) {
        try {
            if (EmvFlowRuntime.getDal() != null) {
                EmvFlowRuntime.getDal().getMag().close();
                EmvFlowRuntime.getDal().getIcc().close((byte) 0);
                EmvFlowRuntime.getDal().getPicc(EPiccType.INTERNAL).close();
                if (contactless) {
                    EmvFlowRuntime.getDal().getPicc(EPiccType.EXTERNAL).close();
                }
            }
        } catch (Exception e) {
            LogUtils.e(TAG, "close readers failed", e);
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

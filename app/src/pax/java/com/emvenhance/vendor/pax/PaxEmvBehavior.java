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
import com.emvenhance.emvflow.preprocess.EmvPreProcessFacade;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.bizentity.entity.SearchMode;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.entity.EPiccType;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvservice.emv.contact.EmvContactService;
import com.pax.emvservice.emv.contactless.ContactlessService;
import com.pax.emvservice.export.contact.IContactResultListener;
import com.pax.emvservice.export.contactless.IContactlessResultListener;
import com.pax.jemv.clcommon.RetCode;
import com.pax.jemv.device.DeviceManager;
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
 *   <li>Chip: {@link #onApplicationSelection} calls EMV app select directly
 *       ({@link #runContactAppSelect}) before handing the rest of the flow to the PAX kernel
 *       ({@link #runContactKernel}). Contactless: {@link #onApplicationSelection} starts the
 *       PAX kernel directly — app select isn't split out there.
 *       Either way, kernel callbacks use {@link #announceStep} (observable only — kernel owns
 *       the phase). Steps the kernel handles with no callback are simply never announced
 *       individually; the next real callback's step is published as-is, with no gap-filling.</li>
 * </ul>
 */
public class PaxEmvBehavior extends AbstractEmvBehavior
        implements IContactCallback, IContactlessCallback,
        IContactResultListener, IContactlessResultListener {

    private static final String TAG = "PaxEmvBehavior";

    private final PaxKernel kernel;

    private boolean initOk = true;

    @Nullable
    private AuthResult lastAuth;

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
    public void cancel() {
        super.cancel();
        try {
            kernel.contact.setUserCancel(true);
        } catch (Exception e) {
            LogUtils.e(TAG, "setUserCancel failed", e);
        }
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
        // Chip/CLSS: announced from kernel callbacks — nothing to chain here.
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

    // ─── Not reached via onXxx dispatch — required overrides, not stubs ──
    //
    // Chip/CLSS: PAX's own kernel (EmvContactService/ContactlessService.startTransProcess)
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
    public void onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Kernel-internal (SDA/DDA/CDA) — no callback exists; not individually announced.
    }

    @Override
    public void onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Kernel-internal — no callback exists; not individually announced.
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
        EmvEngine eng = requireEngine();
        ContactlessService emv = kernel.contactless;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contactless EMV ============");
            int ret = emv.startTransProcess(this);
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless execution failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contactless EMV failed");
        } finally {
            closeReaders(true);
            if (!isCancelled()) {
                try {
                    emv.checkClsResult(this);
                } catch (Exception e) {
                    LogUtils.e(TAG, "checkClsResult error", e);
                    finishError(e.getMessage() != null ? e.getMessage() : "checkClsResult failed");
                }
            }
        }
    }

    /**
     * Runs EMV application selection (EMVCallback.EMVAppSelect, via
     * {@link EmvContactService#selectApplication}) directly from the APPLICATION_SELECTION
     * step. On success the kernel-owned rest of the flow (read app data → card auth → GAC)
     * continues in {@link #runContactKernel}; on failure the transaction ends here.
     */
    private void runContactAppSelect() {
        requireEngine();
        EmvContactService emv = kernel.contact;
        boolean selected;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contact EMV: App Select ============");
            int ret = emv.selectApplication(this);
            LogUtils.d(TAG, "selectApplication ret=" + ret);
            selected = ret == RetCode.EMV_OK;
        } catch (Exception e) {
            LogUtils.e(TAG, "contact app select failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV app select failed");
            return;
        }
        if (selected) {
            runContactKernel();
            return;
        }
        closeReaders(false);
        if (!isCancelled()) {
            try {
                emv.checkContactResult(this);
            } catch (Exception e) {
                LogUtils.e(TAG, "checkContactResult error", e);
                finishError(e.getMessage() != null ? e.getMessage() : "checkContactResult failed");
            }
        }
    }

    /** Continues the contact kernel flow after a successful {@link #runContactAppSelect}. */
    private void runContactKernel() {
        EmvEngine eng = requireEngine();
        EmvContactService emv = kernel.contact;
        try {
            if (isCancelled()) {
                finishError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Continue Contact EMV ============");
            int ret = emv.startTransProcess(this);
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contact execution failed", e);
            finishError(e.getMessage() != null ? e.getMessage() : "Contact EMV failed");
        } finally {
            closeReaders(false);
            if (!isCancelled()) {
                try {
                    emv.checkContactResult(this);
                } catch (Exception e) {
                    LogUtils.e(TAG, "checkContactResult error", e);
                    finishError(e.getMessage() != null
                            ? e.getMessage() : "checkContactResult failed");
                }
            }
        }
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
            byte adjusted = new EmvPreProcessFacade(
                    config.getProcCode(),
                    config.getAmountMinor(),
                    timestamp(),
                    0,
                    requested,
                    kernel.params,
                    kernel.contact,
                    kernel.contactless).start();
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
        eng.notifyCardDetected(safe(kernel.contactless.getPan()), "PAX Issuer",
                safe(kernel.contactless.getCardholderName()), "Contactless");
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
        eng.notifyCardDetected(safe(kernel.contact.getPan()), "PAX Issuer",
                safe(kernel.contact.getCardholderName()), "Contact");
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
        boolean seePhone = kernel.contactless.getIsLastNeedSeePhone();
        kernel.contactless.setIsLastNeedSeePhone(false);
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

package com.emvenhance.vendor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.EmvStepReporter;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.emvflow.EmvPreProcessFacade;
import com.emvenhance.emvflow.EmvStepProgress;
import com.emvenhance.emvflow.device.EmvDeviceImpl;
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
import com.pax.emvservice.export.EmvServiceConstant;
import com.pax.emvservice.export.IEmvContactService;
import com.pax.emvservice.export.IEmvContactlessService;
import com.pax.emvservice.export.contact.IContactResultListener;
import com.pax.emvservice.export.contactless.IContactlessResultListener;
import com.pax.jemv.device.DeviceManager;
import com.sankuai.waimai.router.Router;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sole integration layer between the PAX SDK and the EMV engine.
 *
 * <p>This class owns every PAX service call and every PAX callback. It contains no transaction
 * orchestration — each callback, state change, and result is translated immediately into
 * engine emissions ({@code emitEmvStep} / {@code emitTransactionStep}).
 * {@link com.emvenhance.core.PosTerminal} then routes EMV steps through
 * {@code dispatchEmvStep(...)}.
 *
 * <pre>
 *   PAX SDK  →  PaxEmvBehavior  →  PaxEmvEngine (EmvEngine subjects)
 *            →  PosTerminal.dispatchEmvStep
 * </pre>
 */
public class PaxEmvBehavior {

    private static final String TAG = "PaxEmvBehavior";

    private final PaxEmvEngine engine;

    private final AtomicReference<AuthResult> pendingAuth = new AtomicReference<>();
    private volatile CountDownLatch authLatch;

    public PaxEmvBehavior(@NonNull PaxEmvEngine engine) {
        this.engine = engine;
    }

    /**
     * Terminal / kernel pre-process for the given config.
     *
     * @return true when at least one search mode remains enabled
     */
    public boolean prepare(@NonNull TransactionConfig config) {
        byte requested = 0;
        if (config.isContact()) {
            requested |= SearchMode.INSERT;
        }
        if (config.isContactless()) {
            requested |= SearchMode.INTERNAL_WAVE;
        }
        try {
            byte adjusted = new EmvPreProcessFacade(
                    config.getProcCode(),
                    config.getAmountMinor(),
                    timestamp(),
                    0,
                    requested).start();
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

    /** Runs a contact EMV transaction against the PAX contact service. Blocking. */
    public void executeContact() {
        runContact();
    }

    /** Runs a contactless EMV transaction against the PAX contactless service. Blocking. */
    public void executeContactless() {
        runContactless();
    }

    /**
     * Delivers the host authorization result into a blocked {@code startOnlineProcess}
     * callback so the PAX kernel can continue (2nd GAC / script processing).
     */
    public void deliverAuthResult(@NonNull AuthResult authResult) {
        pendingAuth.set(authResult);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    // ─── Contactless execution ───────────────────────────────────────────

    private void runContactless() {
        EmvStepProgress progress = newProgress();
        IEmvContactlessService emv = null;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            emv = Router.getService(
                    IEmvContactlessService.class, EmvServiceConstant.EMVSERVICE_CONTACTLESS);
            if (emv == null) {
                engine.reportError("EMV service missing: Router returned null IEmvContactlessService");
                return;
            }
            progress.advanceTo(EmvStep.SEARCH_CARD, "card tapped");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_DETECTED,
                    "Contactless"));
            LogUtils.d(TAG, "============ Start Contactless EMV ============");
            int ret = emv.startTransProcess(new ContactlessCallbackBridge(emv, progress));
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless execution failed", e);
            engine.reportError(e.getMessage() != null ? e.getMessage() : "Contactless EMV failed");
        } finally {
            closeReaders(true);
            checkContactlessResult(emv, progress);
        }
    }

    private void checkContactlessResult(@Nullable IEmvContactlessService emv,
            @NonNull EmvStepProgress progress) {
        if (emv == null) {
            return;
        }
        try {
            emv.checkClsResult(new ContactlessResultBridge(progress));
        } catch (Exception e) {
            LogUtils.e(TAG, "checkClsResult error", e);
            engine.reportError(e.getMessage() != null ? e.getMessage() : "checkClsResult failed");
        }
    }

    // ─── Contact execution ───────────────────────────────────────────────

    private void runContact() {
        EmvStepProgress progress = newProgress();
        IEmvContactService emv = null;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            emv = Router.getService(
                    IEmvContactService.class, EmvServiceConstant.EMVSERVICE_CONTACT);
            if (emv == null) {
                engine.reportError("EMV service missing: Router returned null IEmvContactService");
                return;
            }
            progress.advanceTo(EmvStep.SEARCH_CARD, "card present");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_DETECTED,
                    "Contact"));
            LogUtils.d(TAG, "============ Start Contact EMV ============");
            int ret = emv.startTransProcess(new ContactCallbackBridge(emv, progress));
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contact execution failed", e);
            engine.reportError(e.getMessage() != null ? e.getMessage() : "Contact EMV failed");
        } finally {
            closeReaders(false);
            checkContactResult(emv, progress);
        }
    }

    private void checkContactResult(@Nullable IEmvContactService emv,
            @NonNull EmvStepProgress progress) {
        if (emv == null) {
            return;
        }
        try {
            emv.checkContactResult(new ContactResultBridge(progress));
        } catch (Exception e) {
            LogUtils.e(TAG, "checkContactResult error", e);
            engine.reportError(e.getMessage() != null ? e.getMessage() : "checkContactResult failed");
        }
    }

    // ─── Shared helpers ──────────────────────────────────────────────────

    private EmvStepProgress newProgress() {
        EmvStepReporter reporter = (step, detail) -> engine.reportEmvStep(step, detail);
        return new EmvStepProgress(reporter);
    }

    @NonNull
    private OnlineResultWrapper awaitOnlineResult(@NonNull EmvStepProgress progress) {
        progress.advanceTo(EmvStep.START_ONLINE_PROCESS, null);
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));

        AuthResult auth;
        try {
            authLatch.await();
            auth = pendingAuth.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auth = null;
        } finally {
            authLatch = null;
        }

        progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION,
                auth != null && auth.isApproved() ? "host approved" : "host declined");

        return toOnlineResultWrapper(auth);
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

    // ─── Contactless PAX callbacks → engine ──────────────────────────────

    private final class ContactlessCallbackBridge implements IContactlessCallback {
        private final IEmvContactlessService emv;
        private final EmvStepProgress progress;

        ContactlessCallbackBridge(IEmvContactlessService emv, EmvStepProgress progress) {
            this.emv = emv;
            this.progress = progress;
        }

        @Override
        public int showEnterTip() {
            progress.advanceTo(EmvStep.SEARCH_CARD, "present card");
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public void onReadCardOk() {
            progress.advanceTo(EmvStep.READ_APPLICATION_DATA, null);
            engine.reportTransactionStep(
                    TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"));
        }

        @Override
        public int confirmCard() {
            progress.advanceTo(EmvStep.SET_TRANSACTION_DATA, null);
            String pan = safe(emv.getPan());
            String holder = safe(emv.getCardholderName());
            engine.reportCardDetected(pan, "PAX Issuer", holder, "Contactless");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
                byte[] pinData) {
            progress.advanceTo(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
            if (!isOnlinePin) {
                progress.advanceTo(EmvStep.OFFLINE_PIN_VERIFICATION, null);
            }
            engine.reportTransactionStep(TransactionStepEvent.builder(
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
            return awaitOnlineResult(progress);
        }

        @Override
        public void onRemoveCard() {
            LogUtils.d(TAG, "remove card");
        }

        @Override
        public void onDetect2ndTap() {
            LogUtils.d(TAG, "second tap required");
            engine.reportTransactionStep(TransactionStepEvent.of(
                    TransactionStep.WAITING_FOR_CARD, "Present card again"));
        }

        @Override
        public boolean needSeePhone() {
            boolean seePhone = emv.getIsLastNeedSeePhone();
            emv.setIsLastNeedSeePhone(false);
            if (seePhone) {
                LogUtils.d(TAG, "see phone");
            }
            return seePhone;
        }
    }

    private final class ContactlessResultBridge implements IContactlessResultListener {
        private final EmvStepProgress progress;

        ContactlessResultBridge(EmvStepProgress progress) {
            this.progress = progress;
        }

        @Override
        public void offlineApproved(boolean needSignature) {
            LogUtils.d(TAG, "offlineApproved sig=" + needSignature);
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_OFFLINE_APPROVED");
            engine.reportApproved("RESULT_OFFLINE_APPROVED");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineApproved(boolean needSignature) {
            progress.advanceTo(EmvStep.SCRIPT_PROCESSING, null);
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_ONLINE_APPROVED");
            engine.reportApproved("RESULT_ONLINE_APPROVED");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineDenied() {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Denied");
            engine.reportDeclined("Online Denied");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineCardDenied(int resultCode) {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Card Denied");
            engine.reportDeclined("Online Card Denied code=" + resultCode);
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineFailed() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Failed");
            engine.reportError("Online Failed: no host response");
        }

        @Override
        public void offlineDenied(int resultCode) {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Offline Denied");
            engine.reportDeclined("Offline Denied code=" + resultCode);
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void seePhone() {
            engine.reportError("See Phone: Continue on the phone");
        }

        @Override
        public void tryAnotherInterface() {
            engine.reportError("Try Another Interface: Use contact instead");
        }

        @Override
        public void tryAgain() {
            engine.reportError("Try Again: Present card again");
        }

        @Override
        public void simpleFlowEnd() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_SIMPLE_FLOW_END");
            engine.reportApproved("RESULT_SIMPLE_FLOW_END");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }
    }

    // ─── Contact PAX callbacks → engine ──────────────────────────────────

    private final class ContactCallbackBridge implements IContactCallback {
        private final IEmvContactService emv;
        private final EmvStepProgress progress;

        ContactCallbackBridge(IEmvContactService emv, EmvStepProgress progress) {
            this.emv = emv;
            this.progress = progress;
        }

        @Override
        public int showEnterTip() {
            progress.advanceTo(EmvStep.SEARCH_CARD, "insert card");
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList) {
            int candidates = candList == null ? 0 : candList.size();
            progress.advanceTo(
                    isFirstSelect ? EmvStep.WAIT_APPLICATION_SELECTION
                            : EmvStep.FINAL_APPLICATION_SELECTION,
                    candidates + " candidate AID(s), selecting first");
            engine.reportTransactionStep(
                    TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int showConfirmCard() {
            progress.advanceTo(EmvStep.SET_TRANSACTION_DATA, null);
            String pan = safe(emv.getPan());
            String holder = safe(emv.getCardholderName());
            engine.reportCardDetected(pan, "PAX Issuer", holder, "Contact");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
                byte[] pinData) {
            progress.advanceTo(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
            if (!isOnlinePin) {
                progress.advanceTo(EmvStep.OFFLINE_PIN_VERIFICATION, null);
            }
            engine.reportTransactionStep(TransactionStepEvent.builder(
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
            return awaitOnlineResult(progress);
        }
    }

    private final class ContactResultBridge implements IContactResultListener {
        private final EmvStepProgress progress;

        ContactResultBridge(EmvStepProgress progress) {
            this.progress = progress;
        }

        @Override
        public void offlineApproved(boolean needSignature, boolean needSetARC) {
            LogUtils.d(TAG, "offlineApproved sig=" + needSignature);
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_OFFLINE_APPROVED");
            engine.reportApproved("RESULT_OFFLINE_APPROVED");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineApproved(boolean needSignature) {
            progress.advanceTo(EmvStep.SCRIPT_PROCESSING, null);
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_ONLINE_APPROVED");
            engine.reportApproved("RESULT_ONLINE_APPROVED");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineDenied() {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Denied");
            engine.reportDeclined("Online Denied");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineCardDenied(int resultCode) {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Card Denied");
            engine.reportDeclined("Online Card Denied code=" + resultCode);
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void onlineFailed() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Failed");
            engine.reportError("Online Failed: no host response");
        }

        @Override
        public void offlineDenied(int resultCode) {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Offline Denied");
            engine.reportDeclined("Offline Denied code=" + resultCode);
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }

        @Override
        public void fallback() {
            engine.reportError("Fallback: Contact fallback required");
        }

        @Override
        public void simpleFlowEnd() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_SIMPLE_FLOW_END");
            engine.reportApproved("RESULT_SIMPLE_FLOW_END");
            engine.reportTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        }
    }
}

package com.emvenhance.vendor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvEngine;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PAX SDK adapter — receives every PAX callback and notifies {@link EmvEngine} immediately.
 *
 * <p>No card search and no business orchestration. Created by {@link PaxTerminal}.
 */
public class PaxEmvBehavior implements EmvBehavior {

    private static final String TAG = "PaxEmvBehavior";

    private final AtomicReference<AuthResult> pendingAuth = new AtomicReference<>();
    private volatile CountDownLatch authLatch;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    private volatile IEmvContactService contactService;

    @Override
    public boolean prepare(@NonNull TransactionConfig config) {
        cancelled.set(false);
        // Magstripe-only has no EMV pre-process.
        if (config.isMagstripe() && !config.isContact() && !config.isContactless()) {
            return true;
        }
        byte requested = 0;
        if (config.isContact()) {
            requested |= SearchMode.INSERT;
        }
        if (config.isContactless()) {
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

    @Override
    public void startEmv(@NonNull EmvEngine engine, @NonNull CardPresence card) {
        cancelled.set(false);
        if (card.isMagstripe()) {
            runMagstripe(engine, card);
            return;
        }
        if (card.isContact()) {
            runContact(engine);
            return;
        }
        runContactless(engine);
    }

    @Override
    public void completeOnline(@NonNull AuthResult authResult) {
        pendingAuth.set(authResult);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            pendingAuth.compareAndSet(null, AuthResult.declined("17", "Cancelled"));
            latch.countDown();
        }
        IEmvContactService contact = contactService;
        if (contact != null) {
            try {
                contact.setUserCancel(true);
            } catch (Exception e) {
                LogUtils.e(TAG, "setUserCancel failed", e);
            }
        }
    }

    // ─── Magstripe (no EMV kernel) ───────────────────────────────────────

    private void runMagstripe(@NonNull EmvEngine engine, @NonNull CardPresence card) {
        String track2 = card.getTrack2() != null ? card.getTrack2() : "";
        String pan = panFromTrack2(track2);
        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA, "magstripe");
        engine.notifyCardDetected(pan, "MAG", "", card.getModeLabel());
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        // Host authorize → completeOnline unblocks nothing for mag; terminal will call
        // completeOnline then we finish in a latch wait below for a consistent online path.
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
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
        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth != null && auth.isApproved()) {
            engine.notifyApproved("MAG ONLINE APPROVED");
        } else {
            engine.notifyDeclined(auth != null ? auth.getMessage() : "MAG declined");
        }
        engine.notifyCompleted();
    }

    // ─── Contactless ─────────────────────────────────────────────────────

    private void runContactless(@NonNull EmvEngine engine) {
        EmvStepProgress progress = newProgress(engine);
        IEmvContactlessService emv = null;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            emv = Router.getService(
                    IEmvContactlessService.class, EmvServiceConstant.EMVSERVICE_CONTACTLESS);
            if (emv == null) {
                engine.notifyError("EMV service missing: IEmvContactlessService");
                return;
            }
            if (cancelled.get()) {
                engine.notifyError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contactless EMV ============");
            int ret = emv.startTransProcess(new ContactlessCallbackAdapter(engine, emv, progress));
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless execution failed", e);
            engine.notifyError(e.getMessage() != null ? e.getMessage() : "Contactless EMV failed");
        } finally {
            closeReaders(true);
            checkContactlessResult(engine, emv, progress);
        }
    }

    private void checkContactlessResult(@NonNull EmvEngine engine,
            @Nullable IEmvContactlessService emv, @NonNull EmvStepProgress progress) {
        if (emv == null || cancelled.get()) {
            return;
        }
        try {
            emv.checkClsResult(new ContactlessResultAdapter(engine, progress));
        } catch (Exception e) {
            LogUtils.e(TAG, "checkClsResult error", e);
            engine.notifyError(e.getMessage() != null ? e.getMessage() : "checkClsResult failed");
        }
    }

    // ─── Contact ─────────────────────────────────────────────────────────

    private void runContact(@NonNull EmvEngine engine) {
        EmvStepProgress progress = newProgress(engine);
        IEmvContactService emv = null;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            emv = Router.getService(
                    IEmvContactService.class, EmvServiceConstant.EMVSERVICE_CONTACT);
            contactService = emv;
            if (emv == null) {
                engine.notifyError("EMV service missing: IEmvContactService");
                return;
            }
            if (cancelled.get()) {
                engine.notifyError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contact EMV ============");
            int ret = emv.startTransProcess(new ContactCallbackAdapter(engine, emv, progress));
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contact execution failed", e);
            engine.notifyError(e.getMessage() != null ? e.getMessage() : "Contact EMV failed");
        } finally {
            contactService = null;
            closeReaders(false);
            checkContactResult(engine, emv, progress);
        }
    }

    private void checkContactResult(@NonNull EmvEngine engine, @Nullable IEmvContactService emv,
            @NonNull EmvStepProgress progress) {
        if (emv == null || cancelled.get()) {
            return;
        }
        try {
            emv.checkContactResult(new ContactResultAdapter(engine, progress));
        } catch (Exception e) {
            LogUtils.e(TAG, "checkContactResult error", e);
            engine.notifyError(e.getMessage() != null ? e.getMessage() : "checkContactResult failed");
        }
    }

    // ─── Shared ──────────────────────────────────────────────────────────

    private EmvStepProgress newProgress(EmvEngine engine) {
        EmvStepReporter reporter = engine::notifyEmvStep;
        return new EmvStepProgress(reporter);
    }

    @NonNull
    private OnlineResultWrapper awaitOnlineResult(@NonNull EmvEngine engine,
            @NonNull EmvStepProgress progress) {
        progress.advanceTo(EmvStep.START_ONLINE_PROCESS, null);
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));

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

    private static String panFromTrack2(String track2) {
        int sep = track2.indexOf('=');
        if (sep <= 0) {
            sep = track2.indexOf('D');
        }
        return sep > 0 ? track2.substring(0, sep) : track2;
    }

    // ─── PAX contactless callbacks → engine ──────────────────────────────

    private final class ContactlessCallbackAdapter implements IContactlessCallback {
        private final EmvEngine engine;
        private final IEmvContactlessService emv;
        private final EmvStepProgress progress;

        ContactlessCallbackAdapter(EmvEngine engine, IEmvContactlessService emv,
                EmvStepProgress progress) {
            this.engine = engine;
            this.emv = emv;
            this.progress = progress;
        }

        @Override
        public int showEnterTip() {
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public void onReadCardOk() {
            progress.advanceTo(EmvStep.READ_APPLICATION_DATA, null);
            engine.notifyTransactionStep(
                    TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"));
        }

        @Override
        public int confirmCard() {
            progress.advanceTo(EmvStep.SET_TRANSACTION_DATA, null);
            engine.notifyCardDetected(safe(emv.getPan()), "PAX Issuer",
                    safe(emv.getCardholderName()), "Contactless");
            engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
                byte[] pinData) {
            progress.advanceTo(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
            if (!isOnlinePin) {
                progress.advanceTo(EmvStep.OFFLINE_PIN_VERIFICATION, null);
            }
            engine.notifyTransactionStep(TransactionStepEvent.builder(
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
            return awaitOnlineResult(engine, progress);
        }

        @Override
        public void onRemoveCard() {
            LogUtils.d(TAG, "remove card");
        }

        @Override
        public void onDetect2ndTap() {
            engine.notifyTransactionStep(TransactionStepEvent.of(
                    TransactionStep.WAITING_FOR_CARD, "Present card again"));
        }

        @Override
        public boolean needSeePhone() {
            boolean seePhone = emv.getIsLastNeedSeePhone();
            emv.setIsLastNeedSeePhone(false);
            return seePhone;
        }
    }

    private final class ContactlessResultAdapter implements IContactlessResultListener {
        private final EmvEngine engine;
        private final EmvStepProgress progress;

        ContactlessResultAdapter(EmvEngine engine, EmvStepProgress progress) {
            this.engine = engine;
            this.progress = progress;
        }

        @Override
        public void offlineApproved(boolean needSignature) {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_OFFLINE_APPROVED");
            engine.notifyApproved("RESULT_OFFLINE_APPROVED");
            engine.notifyCompleted();
        }

        @Override
        public void onlineApproved(boolean needSignature) {
            progress.advanceTo(EmvStep.SCRIPT_PROCESSING, null);
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_ONLINE_APPROVED");
            engine.notifyApproved("RESULT_ONLINE_APPROVED");
            engine.notifyCompleted();
        }

        @Override
        public void onlineDenied() {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Denied");
            engine.notifyDeclined("Online Denied");
            engine.notifyCompleted();
        }

        @Override
        public void onlineCardDenied(int resultCode) {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Card Denied");
            engine.notifyDeclined("Online Card Denied code=" + resultCode);
            engine.notifyCompleted();
        }

        @Override
        public void onlineFailed() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Failed");
            engine.notifyError("Online Failed: no host response");
        }

        @Override
        public void offlineDenied(int resultCode) {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Offline Denied");
            engine.notifyDeclined("Offline Denied code=" + resultCode);
            engine.notifyCompleted();
        }

        @Override
        public void seePhone() {
            engine.notifyError("See Phone: Continue on the phone");
        }

        @Override
        public void tryAnotherInterface() {
            engine.notifyError("Try Another Interface: Use contact instead");
        }

        @Override
        public void tryAgain() {
            engine.notifyError("Try Again: Present card again");
        }

        @Override
        public void simpleFlowEnd() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_SIMPLE_FLOW_END");
            engine.notifyApproved("RESULT_SIMPLE_FLOW_END");
            engine.notifyCompleted();
        }
    }

    // ─── PAX contact callbacks → engine ──────────────────────────────────

    private final class ContactCallbackAdapter implements IContactCallback {
        private final EmvEngine engine;
        private final IEmvContactService emv;
        private final EmvStepProgress progress;

        ContactCallbackAdapter(EmvEngine engine, IEmvContactService emv,
                EmvStepProgress progress) {
            this.engine = engine;
            this.emv = emv;
            this.progress = progress;
        }

        @Override
        public int showEnterTip() {
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList) {
            int candidates = candList == null ? 0 : candList.size();
            progress.advanceTo(
                    isFirstSelect ? EmvStep.WAIT_APPLICATION_SELECTION
                            : EmvStep.FINAL_APPLICATION_SELECTION,
                    candidates + " candidate AID(s), selecting first");
            engine.notifyTransactionStep(
                    TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int showConfirmCard() {
            progress.advanceTo(EmvStep.SET_TRANSACTION_DATA, null);
            engine.notifyCardDetected(safe(emv.getPan()), "PAX Issuer",
                    safe(emv.getCardholderName()), "Contact");
            engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

        @Override
        public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
                byte[] pinData) {
            progress.advanceTo(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
            if (!isOnlinePin) {
                progress.advanceTo(EmvStep.OFFLINE_PIN_VERIFICATION, null);
            }
            engine.notifyTransactionStep(TransactionStepEvent.builder(
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
            return awaitOnlineResult(engine, progress);
        }
    }

    private final class ContactResultAdapter implements IContactResultListener {
        private final EmvEngine engine;
        private final EmvStepProgress progress;

        ContactResultAdapter(EmvEngine engine, EmvStepProgress progress) {
            this.engine = engine;
            this.progress = progress;
        }

        @Override
        public void offlineApproved(boolean needSignature, boolean needSetARC) {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_OFFLINE_APPROVED");
            engine.notifyApproved("RESULT_OFFLINE_APPROVED");
            engine.notifyCompleted();
        }

        @Override
        public void onlineApproved(boolean needSignature) {
            progress.advanceTo(EmvStep.SCRIPT_PROCESSING, null);
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_ONLINE_APPROVED");
            engine.notifyApproved("RESULT_ONLINE_APPROVED");
            engine.notifyCompleted();
        }

        @Override
        public void onlineDenied() {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Denied");
            engine.notifyDeclined("Online Denied");
            engine.notifyCompleted();
        }

        @Override
        public void onlineCardDenied(int resultCode) {
            progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Card Denied");
            engine.notifyDeclined("Online Card Denied code=" + resultCode);
            engine.notifyCompleted();
        }

        @Override
        public void onlineFailed() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Online Failed");
            engine.notifyError("Online Failed: no host response");
        }

        @Override
        public void offlineDenied(int resultCode) {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "Offline Denied");
            engine.notifyDeclined("Offline Denied code=" + resultCode);
            engine.notifyCompleted();
        }

        @Override
        public void fallback() {
            engine.notifyError("Fallback: Contact fallback required");
        }

        @Override
        public void simpleFlowEnd() {
            progress.advanceTo(EmvStep.TRANSACTION_COMPLETION, "RESULT_SIMPLE_FLOW_END");
            engine.notifyApproved("RESULT_SIMPLE_FLOW_END");
            engine.notifyCompleted();
        }
    }
}

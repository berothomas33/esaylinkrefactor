package com.emvenhance.vendor.pax;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.AbstractEmvBehavior;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.CommunicationBehavior;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.PrinterBehavior;
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
import com.pax.emvservice.emv.contact.EmvContactService;
import com.pax.emvservice.emv.contactless.ContactlessService;
import com.pax.emvservice.export.contact.IContactResultListener;
import com.pax.emvservice.export.contactless.IContactlessResultListener;
import com.pax.jemv.device.DeviceManager;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PAX vendor EMV behavior — direct composition with concrete PAX kernels.
 *
 * <p>No Router, no service-locator lookup, no callback-adapter inner classes.
 * This class <em>is</em> the PAX {@link IContactCallback} / {@link IContactlessCallback}
 * (and result listeners) and notifies {@link EmvEngine} from those callbacks.
 *
 * <pre>
 *   PaxTerminal ──owns──► PaxKernel (EmvContactService, ContactlessService, …)
 *        │
 *        └── PaxEmvBehavior ──implements──► IContactCallback / IContactlessCallback
 *                              ──calls───► contact.startTransProcess(this)
 * </pre>
 */
public class PaxEmvBehavior extends AbstractEmvBehavior
        implements IContactCallback, IContactlessCallback,
        IContactResultListener, IContactlessResultListener {

    private static final String TAG = "PaxEmvBehavior";

    private final PaxKernel kernel;

    private final AtomicReference<AuthResult> pendingAuth = new AtomicReference<>();
    private volatile CountDownLatch authLatch;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    /** Active gap-filler while a kernel transaction is running. */
    @Nullable
    private EmvStepProgress progress;

    public PaxEmvBehavior(CommunicationBehavior communication, PrinterBehavior printer,
            PaxKernel kernel) {
        super(communication, printer);
        this.kernel = kernel;
    }

    // ─── Lifecycle (search is owned by PaxTerminal) ──────────────────────

    @Override
    public boolean prepare(@NonNull EmvEngine engine, @NonNull TransactionConfig config) {
        this.engine = engine;
        this.activeConfig = config;
        cancelled.set(false);
        progress = null;

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        engine.notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION);
        return prepareKernel(config);
    }

    @Override
    public void start(@NonNull EmvEngine engine, @NonNull TransactionConfig config,
            @NonNull CardPresence card) {
        this.engine = engine;
        this.activeConfig = config;
        cancelled.set(false);
        progress = null;

        if (card.isManual()) {
            runManual(card);
        } else if (card.isMagstripe()) {
            runMagstripe(card);
        } else if (card.isChip()) {
            runContact();
        } else {
            runContactless();
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
        try {
            kernel.contact.setUserCancel(true);
        } catch (Exception e) {
            LogUtils.e(TAG, "setUserCancel failed", e);
        }
    }

    @Override
    protected void deliverOnlineResult(AuthResult authResult) {
        pendingAuth.set(authResult);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    // ─── Prepare / run ───────────────────────────────────────────────────

    private boolean prepareKernel(@NonNull TransactionConfig config) {
        // Mag / manual need no EMV preprocess.
        if (!config.allowsChip() && !config.allowsContactless()) {
            return true;
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

    private void runManual(@NonNull CardPresence card) {
        EmvEngine eng = requireEngine();
        String pan = card.getManualPan() != null ? card.getManualPan() : "";
        if (pan.isEmpty()) {
            eng.notifyError("Manual entry requires a PAN");
            return;
        }
        eng.notifyEmvStep(EmvStep.READ_APPLICATION_DATA, "manual");
        eng.notifyCardDetected(pan, "MANUAL", "", card.getModeLabel());
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        AuthResult auth = awaitAuth();
        eng.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth != null && auth.isApproved()) {
            eng.notifyApproved("MANUAL ONLINE APPROVED");
        } else {
            eng.notifyDeclined(auth != null ? auth.getMessage() : "MANUAL declined");
        }
        eng.notifyCompleted();
    }

    private void runMagstripe(@NonNull CardPresence card) {
        EmvEngine eng = requireEngine();
        String track2 = card.getTrack2() != null ? card.getTrack2() : "";
        String pan = panFromTrack2(track2);
        eng.notifyEmvStep(EmvStep.READ_APPLICATION_DATA, "magstripe");
        eng.notifyCardDetected(pan, "MAG", "", card.getModeLabel());
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        AuthResult auth = awaitAuth();
        eng.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth != null && auth.isApproved()) {
            eng.notifyApproved("MAG ONLINE APPROVED");
        } else {
            eng.notifyDeclined(auth != null ? auth.getMessage() : "MAG declined");
        }
        eng.notifyCompleted();
    }

    private void runContactless() {
        EmvEngine eng = requireEngine();
        progress = new EmvStepProgress(eng::notifyEmvStep);
        ContactlessService emv = kernel.contactless;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            if (cancelled.get()) {
                eng.notifyError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contactless EMV ============");
            // this == IContactlessCallback — no adapter
            int ret = emv.startTransProcess(this);
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contactless execution failed", e);
            eng.notifyError(e.getMessage() != null ? e.getMessage() : "Contactless EMV failed");
        } finally {
            closeReaders(true);
            if (!cancelled.get()) {
                try {
                    // this == IContactlessResultListener — no adapter
                    emv.checkClsResult(this);
                } catch (Exception e) {
                    LogUtils.e(TAG, "checkClsResult error", e);
                    eng.notifyError(e.getMessage() != null ? e.getMessage() : "checkClsResult failed");
                }
            }
            progress = null;
        }
    }

    private void runContact() {
        EmvEngine eng = requireEngine();
        progress = new EmvStepProgress(eng::notifyEmvStep);
        EmvContactService emv = kernel.contact;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            if (cancelled.get()) {
                eng.notifyError("Transaction cancelled");
                return;
            }
            LogUtils.d(TAG, "============ Start Contact EMV ============");
            // this == IContactCallback — no adapter
            int ret = emv.startTransProcess(this);
            LogUtils.d(TAG, "startTransProcess ret=" + ret);
        } catch (Exception e) {
            LogUtils.e(TAG, "contact execution failed", e);
            eng.notifyError(e.getMessage() != null ? e.getMessage() : "Contact EMV failed");
        } finally {
            closeReaders(false);
            if (!cancelled.get()) {
                try {
                    // this == IContactResultListener — no adapter
                    emv.checkContactResult(this);
                } catch (Exception e) {
                    LogUtils.e(TAG, "checkContactResult error", e);
                    eng.notifyError(e.getMessage() != null
                            ? e.getMessage() : "checkContactResult failed");
                }
            }
            progress = null;
        }
    }

    // ─── IContactCallback + IContactlessCallback (shared / contactless) ──

    @Override
    public int showEnterTip() {
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public void onReadCardOk() {
        advance(EmvStep.READ_APPLICATION_DATA, null);
        requireEngine().notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"));
    }

    @Override
    public int confirmCard() {
        // Contactless confirm
        advance(EmvStep.SET_TRANSACTION_DATA, null);
        EmvEngine eng = requireEngine();
        eng.notifyCardDetected(safe(kernel.contactless.getPan()), "PAX Issuer",
                safe(kernel.contactless.getCardholderName()), "Contactless");
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList) {
        int candidates = candList == null ? 0 : candList.size();
        advance(isFirstSelect ? EmvStep.WAIT_APPLICATION_SELECTION
                        : EmvStep.FINAL_APPLICATION_SELECTION,
                candidates + " candidate AID(s), selecting first");
        requireEngine().notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int showConfirmCard() {
        // Contact confirm
        advance(EmvStep.SET_TRANSACTION_DATA, null);
        EmvEngine eng = requireEngine();
        eng.notifyCardDetected(safe(kernel.contact.getPan()), "PAX Issuer",
                safe(kernel.contact.getCardholderName()), "Contact");
        eng.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
            byte[] pinData) {
        advance(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
        if (!isOnlinePin) {
            advance(EmvStep.OFFLINE_PIN_VERIFICATION, null);
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
        advance(EmvStep.START_ONLINE_PROCESS, null);
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        requireEngine().notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        AuthResult auth = awaitAuth();
        advance(EmvStep.ISSUER_AUTHENTICATION,
                auth != null && auth.isApproved() ? "host approved" : "host declined");
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

    // ─── Result listeners (contact + contactless) ────────────────────────

    @Override
    public void offlineApproved(boolean needSignature) {
        // Contactless
        finishApproved("RESULT_OFFLINE_APPROVED", false);
    }

    @Override
    public void offlineApproved(boolean needSignature, boolean needSetARC) {
        // Contact
        finishApproved("RESULT_OFFLINE_APPROVED", false);
    }

    @Override
    public void onlineApproved(boolean needSignature) {
        finishApproved("RESULT_ONLINE_APPROVED", true);
    }

    @Override
    public void onlineDenied() {
        advance(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
        finishDeclined("Online Denied");
    }

    @Override
    public void onlineCardDenied(int resultCode) {
        advance(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
        finishDeclined("Online Card Denied code=" + resultCode);
    }

    @Override
    public void onlineFailed() {
        advance(EmvStep.TRANSACTION_COMPLETION, "Online Failed");
        requireEngine().notifyError("Online Failed: no host response");
    }

    @Override
    public void offlineDenied(int resultCode) {
        finishDeclined("Offline Denied code=" + resultCode);
    }

    @Override
    public void seePhone() {
        requireEngine().notifyError("See Phone: Continue on the phone");
    }

    @Override
    public void tryAnotherInterface() {
        requireEngine().notifyError("Try Another Interface: Use contact instead");
    }

    @Override
    public void tryAgain() {
        requireEngine().notifyError("Try Again: Present card again");
    }

    @Override
    public void fallback() {
        requireEngine().notifyError("Fallback: Contact fallback required");
    }

    @Override
    public void simpleFlowEnd() {
        finishApproved("RESULT_SIMPLE_FLOW_END", false);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private void finishApproved(String result, boolean scripts) {
        if (scripts) {
            advance(EmvStep.SCRIPT_PROCESSING, null);
        }
        advance(EmvStep.TRANSACTION_COMPLETION, result);
        EmvEngine eng = requireEngine();
        eng.notifyApproved(result);
        eng.notifyCompleted();
    }

    private void finishDeclined(String reason) {
        advance(EmvStep.TRANSACTION_COMPLETION, reason);
        EmvEngine eng = requireEngine();
        eng.notifyDeclined(reason);
        eng.notifyCompleted();
    }

    private void advance(EmvStep step, @Nullable String detail) {
        if (progress != null) {
            progress.advanceTo(step, detail);
        } else {
            requireEngine().notifyEmvStep(step, detail);
        }
    }

    @Nullable
    private AuthResult awaitAuth() {
        try {
            authLatch.await();
            return pendingAuth.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            authLatch = null;
        }
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

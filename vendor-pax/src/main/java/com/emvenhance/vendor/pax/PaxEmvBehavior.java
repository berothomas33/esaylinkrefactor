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
import com.emvenhance.vendor.pax.PaxKernelSession.Outcome;
import com.emvenhance.vendor.pax.PaxKernelSession.PinRequest;
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

public class PaxEmvBehavior extends AbstractEmvBehavior
        implements IContactCallback, IContactlessCallback {

    private static final String TAG = "PaxEmvBehavior";

    private static final int CONTACTLESS_FLOW_TYPE = EmvTransParam.FLOWTYPE_COMPLETE;

    private static final int MAX_TAP_RETRIES = 3;

    private static final int ICC_APP_SELECT_MAX_ATTEMPTS = 3;
    private static final long ICC_POWER_SETTLE_MS = 200L;

    private static final int ONLINE_PIN_KEY_LENGTH_BYTES = 16;

    private static final int TAG_SELECTED_AID = 0x84;

    private final PaxKernel kernel;

    private final PaxCardSearch cardSearch;

    private boolean clsLastNeedSeePhone;

    private volatile PaxKernelSession tx = new PaxKernelSession();

    public PaxEmvBehavior(PaxKernel kernel) {
        this.kernel = kernel;
        this.cardSearch = new PaxCardSearch(kernel, () -> !isRunning());
    }

    @Override
    public void onTerminalInitialization(TransactionConfig config) {
        tx = new PaxKernelSession();
        EmvDeviceImpl.getInstance().setApduTrace(engine().apduTrace());
        if (!prepareKernel(config)) {
            finishError("Terminal initialization failed");
        }
    }

    @Override
    public void onSearchCard(TransactionConfig config, @Nullable CardPresence card) {
        cardSearch.search(config, cardSearchListener(), engine().apduTrace());
    }

    @Override
    public void cancelCardSearch() {
        cardSearch.stop();
    }

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
            String pan = PaxCardData.panFromTrack2(track2);
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
            runContactReadAppData();
        }
        if (card.isContactless() && kernel.contactless.supportsGranularSteps()) {
            runContactlessReadAppData();
        }
    }

    @Override
    public void onStartOnlineProcess(TransactionConfig config, CardPresence card) {
        if (isSynchronousEntry(card)) {
            tx.lastAuth = authorizeOnline();
            goToStep(EmvStep.TRANSACTION_COMPLETION);
            return;
        }
        tx.lastAuth = authorizeOnline();
    }

    @Override
    public void onTransactionCompletion(TransactionConfig config, CardPresence card) {
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
        Outcome outcome = tx.outcome;
        tx.outcome = null;
        if (outcome != null) {
            reportOutcome(outcome);
        }
    }

    @Override
    public void onOfflineDataAuthentication(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            runContactCardAuth();
        }
        if (card.isContactless()) {
            runContactlessCardAuth();
        }
    }

    @Override
    public void onProcessRestrictions(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            runContactStartTransaction();
        }
        if (card.isContactless()) {
            runContactlessProcessRestrictions();
        }
    }

    @Override
    public void onWaitApplicationSelection(TransactionConfig config, CardPresence card) {
        answerApplicationSelection();
    }

    @Override
    public void onFinalApplicationSelection(TransactionConfig config, CardPresence card) {
        answerApplicationSelection();
    }

    private void answerApplicationSelection() {
        List<CandidateAID> candList = tx.candidates;
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        if (!showsAppSelectDialog(candList)) {
            answerStep(EmvConstant.ContactCallbackStatus.CONTACT_OK);
            return;
        }
        int index = new PaxAppSelectDialog().selectBlocking(
                PaxAppSelectDialog.labels(candList), () -> !isRunning());
        if (index == AbstractAppSelectDialog.NO_ANSWER) {
            LogUtils.i(TAG, "Application not selected (timed out, cancelled or no screen)");
            return;
        }
        answerStep(index);
    }

    private static boolean showsAppSelectDialog(@Nullable List<CandidateAID> candList) {
        int candidates = candList == null ? 0 : candList.size();
        return candidates >= 2 || (BuildConfig.DEBUG && candidates >= 1);
    }

    @Override
    public void onSetTransactionData(TransactionConfig config, CardPresence card) {
        if (card.isChip()) {
            runContactSetTransactionData();
        }
        if (card.isContactless() && kernel.contactless.supportsGranularSteps()) {
            runContactlessSetTransactionData();
        }
    }

    @Override
    public void onCardholderVerification(TransactionConfig config, CardPresence card) {
        PinRequest request = tx.pinRequest;
        if (request != null && request.online) {
            answerStep(enterPin(request));
        }
    }

    @Override
    public void onOfflinePinVerification(TransactionConfig config, CardPresence card) {
        PinRequest request = tx.pinRequest;
        if (request != null && !request.online) {
            answerStep(enterPin(request));
        }
    }

    @Override
    public void onTerminalRiskManagement(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onTerminalActionAnalysis(TransactionConfig config, CardPresence card) {
    }

    @Override
    public void onIssuerAuthentication(TransactionConfig config, CardPresence card) {
        if (tx.issuerResponse != null) {
            completeIssuerAuthentication();
        }
    }

    @Override
    public void onScriptProcessing(TransactionConfig config, CardPresence card) {
        if (tx.pendingScripts != null) {
            processIssuerScripts();
        }
    }

    private void runContactlessAppSelect() {
        boolean proceed = false;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            LogUtils.d(TAG, "============ Contactless EMV: App Select ============");
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
            advanceContactlessStage(firstStageAfterAppSelect());
        }
    }

    private void captureSummaryAfterCardRead() {
        try {
            captureTransactionSummary();
        } catch (Exception e) {
            LogUtils.e(TAG, "transaction summary failed", e);
        }
    }

    private EmvStep firstStageAfterAppSelect() {
        return kernel.contactless.supportsGranularSteps()
                ? EmvStep.SET_TRANSACTION_DATA
                : EmvStep.PROCESS_RESTRICTIONS;
    }

    private int reselectApplication() {
        LogUtils.d(TAG, "CLSS_RESELECT_APP — selecting the next candidate");
        engine().apduTrace().note("EMV", "kernel asked to reselect — next application");
        tx.clsTransResult = kernel.contactless.reselectApplication();
        int ret = tx.clsTransResult.getResultCode();
        LogUtils.d(TAG, "reselectApplication ret=" + ret);
        return ret;
    }

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
                runContactlessStartTransaction();
            } else {
                advanceContactlessStage(EmvStep.PROCESS_RESTRICTIONS);
            }
        }
    }

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
                advanceContactlessStage(EmvStep.OFFLINE_DATA_AUTHENTICATION);
            } else {
                runContactlessStartTransaction();
            }
        }
    }

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

    private int startContactlessTransProcess() {
        tx.clsTransResult = kernel.contactless.startTransProcess();
        CvmResultEnum cvmResult = tx.clsTransResult.getCvmResult();
        int resultCode = tx.clsTransResult.getResultCode();
        TransResultEnum transResultEnum = tx.clsTransResult.getTransResult();
        if (resultCode != RetCode.EMV_OK) {
            markTryAgainOnCommunicationError(resultCode);
            return resultCode;
        }
        if (transResultEnum == TransResultEnum.RESULT_CLSS_SEE_PHONE
                || transResultEnum == TransResultEnum.RESULT_TRY_AGAIN) {
            return resultCode;
        }
        int ret = confirmCard();
        LogUtils.d(TAG, "confirm card: " + ret);
        if (ret != RetCode.EMV_OK) {
            tx.clsTransResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, CvmResultEnum.CVM_NO_CVM);
            return ret;
        }
        if (CONTACTLESS_FLOW_TYPE == EmvTransParam.FLOWTYPE_SIMPLE) {
            tx.clsTransResult = new TransResult(0, TransResultEnum.RESULT_SIMPLE_FLOW_END, CvmResultEnum.CVM_NO_CVM);
            return 0;
        }
        if (cvmResult == CvmResultEnum.CVM_ONLINE_PIN || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
            ret = onCardHolderPwd(true, pinBypass.isOnlinePinBypassEnabled(), 0, null);
            if (ret != RetCode.EMV_OK && ret != EmvConstant.ContactCallbackStatus.NO_PASSWORD) {
                engine().apduTrace().note("PICC", "online PIN not entered (" + ret + ")");
                finishError("Online PIN not entered — cancelled or timed out");
                tx.clsTransResult = new TransResult(ret, TransResultEnum.RESULT_OFFLINE_DENIED, cvmResult);
                return ret;
            }
        }
        if (needsHostWithoutArqc(transResultEnum, resultCode)) {
            noteOnlineWithoutArqc(transResultEnum);
            OnlineResultWrapper online = startOnlineProcess();
            tx.clsTransResult = hostOutcome(online, cvmResult);
            return tx.clsTransResult.getResultCode();
        }
        if (transResultEnum == TransResultEnum.RESULT_REQ_ONLINE) {
            OnlineResultWrapper onlineResultWrapper = startOnlineProcess();
            TransResultEnum onlineTransResultEnum = onlineResultWrapper.getTransResultEnum();
            if (TransResultEnum.RESULT_ONLINE_APPROVED != onlineTransResultEnum) {
                tx.clsTransResult.setTransResult(onlineTransResultEnum);
                tx.clsTransResult.setResultCode(onlineTransResultEnum.ordinal());
                return onlineTransResultEnum.ordinal();
            }
            runIssuerAuthentication(onlineResultWrapper);
            if (cvmResult == CvmResultEnum.CVM_SIG || cvmResult == CvmResultEnum.CVM_ONLINE_PIN_SIG) {
                tx.clsTransResult.setCvmResult(CvmResultEnum.CVM_SIG);
            }
        }
        return tx.clsTransResult.getResultCode();
    }

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

    private static TransResult hostOutcome(OnlineResultWrapper online, CvmResultEnum cvmResult) {
        TransResultEnum outcome = online.getTransResultEnum();
        int code = outcome == TransResultEnum.RESULT_ONLINE_APPROVED ? RetCode.EMV_OK
                : outcome.ordinal();
        return new TransResult(code, outcome, cvmResult != null ? cvmResult : CvmResultEnum.CVM_NO_CVM);
    }

    private void markTryAgainOnCommunicationError(int ret) {
        boolean communicationError = ret == RetCode.ICC_CMD_ERR || ret == RetCode.ICC_RESET_ERR
                || ret == RetCode.CLSS_TRY_AGAIN;
        if (!communicationError) {
            return;
        }
        LogUtils.w(TAG, "Contactless communication error ret=" + ret + " — asking to tap again");
        engine().apduTrace().note("PICC",
                "card communication error " + ret + " — tap again");
        tx.clsTransResult = new TransResult(ret, TransResultEnum.RESULT_TRY_AGAIN,
                CvmResultEnum.CVM_NO_CVM);
    }

    private boolean isContactlessTransactionFinished() {
        return tx.clsTransResult != null && tx.clsTransResult.getTransResult() != null;
    }

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

    private void noteResultNotReported() {
        LogUtils.i(TAG, "Cancelled — the card's result is not reported");
        engine().apduTrace().note("EMV", "cancelled — the card's result is not reported");
    }

    private void advanceContactlessStage(EmvStep next) {
        if (!goToStep(next)) {
            finishContactlessStage();
        }
    }

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
                offlineDenied(RetCode.CLSS_DECLINE);
            }
        } else {
            if (transResultEnum == TransResultEnum.RESULT_CLSS_TRY_ANOTHER_INTERFACE
                    || resultCode == RetCode.CLSS_USE_CONTACT) {
                tryAnotherInterface();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_DENIED) {
                onlineDenied();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_FAILED) {
                onlineFailed();
            } else if (transResultEnum == TransResultEnum.RESULT_ONLINE_CARD_DENIED) {
                onlineCardDenied(resultCode);
            } else if (transResultEnum == TransResultEnum.RESULT_OFFLINE_DENIED) {
                offlineDenied(resultCode);
            } else {
                offlineDenied(resultCode);
            }
        }
    }

    private String getContactlessPan() {
        if (tx.clsCachedTrack2Data != null && !tx.clsCachedTrack2Data.isEmpty()) {
            return TrackUtils.getPan(tx.clsCachedTrack2Data);
        }
        String track2Data = getContactlessTrack2Data();
        if (track2Data != null && !track2Data.isEmpty()) {
            return TrackUtils.getPan(track2Data);
        }
        return PaxCardData.panFromTag(kernel.contactless.getTlv(TagsTable.PAN));
    }

    private String getContactlessTrack2Data() {
        tx.clsCachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contactless.getTlv(TagsTable.TRACK2));
        return tx.clsCachedTrack2Data;
    }

    private String getContactlessCardholderName() {
        return PaxCardData.asciiText(kernel.contactless.getTlv(TagsTable.CARDHOLDER_NAME));
    }

    private void runContactAppSelect() {
        boolean proceed = false;
        try {
            DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
            LogUtils.d(TAG, "============ Contact EMV: App Select ============");
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
            if (!proceed) {
                finishContactStage();
            }
        }
        if (proceed) {
            advanceContactStage(EmvStep.READ_APPLICATION_DATA);
        }
    }

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

    private void advanceContactStage(EmvStep next) {
        if (!goToStep(next)) {
            finishContactStage();
        }
    }

    private void runContactStartTransaction() {
        try {
            LogUtils.d(TAG, "============ Contact EMV: Start Transaction ============");
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

    private int startContactTransProcess() {
        tx.transResult = kernel.contact.startTransProcess();
        int resultCode = tx.transResult.getResultCode();
        TransResultEnum transResultEnum = tx.transResult.getTransResult();
        if (resultCode != RetCode.EMV_OK) {
            return resultCode;
        }
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

    private void completeContactIssuerAuthentication(OnlineResultWrapper onlineResultWrapper) {
        IssuerRspData issuerRspData = onlineResultWrapper.getIssuerRspData();
        int onlineResultCode = onlineResultWrapper.getResultCode();
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

    private boolean isContactTransactionFinished() {
        return tx.transResult != null && tx.transResult.getTransResult() != null;
    }

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

    private void checkContactResult() {
        if (tx.transResult == null) {
            LogUtils.e(TAG, "check result: no transResult recorded — treating as offline denied");
            offlineDenied(-1);
            return;
        }
        int resultCode = tx.transResult.getResultCode();
        TransResultEnum transResultEnum = tx.transResult.getTransResult();
        if (transResultEnum == null) {
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

    private String getContactPan() {
        if (tx.cachedTrack2Data != null && !tx.cachedTrack2Data.isEmpty()) {
            return TrackUtils.getPan(tx.cachedTrack2Data);
        }
        String track2Data = getContactTrack2Data();
        if (track2Data != null && !track2Data.isEmpty()) {
            return TrackUtils.getPan(track2Data);
        }
        return PaxCardData.panFromTag(kernel.contact.getTlv(TagsTable.PAN));
    }

    private String getContactTrack2Data() {
        tx.cachedTrack2Data = TrackUtils.getTrack2FromTag57(kernel.contact.getTlv(TagsTable.TRACK2));
        return tx.cachedTrack2Data;
    }

    private String getContactCardholderName() {
        return PaxCardData.asciiText(kernel.contact.getTlv(TagsTable.CARDHOLDER_NAME));
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

        initializeTerminalIfNeeded();

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

    private void initializeTerminalIfNeeded() {
        if (EmvParamService.isLoaded()) {
            return;
        }
        long start = SystemClock.elapsedRealtime();
        kernel.params.getCachedEmvParam();
        engine().apduTrace().note("EMV", "terminal initialization (EMV parameters loaded"
                + " from the database): " + (SystemClock.elapsedRealtime() - start) + " ms");
    }

    private static final long AMOUNT_OTHER_MINOR = 0L;

    private final PinBypassSettings pinBypass =
            new PinBypassSettings(BaseApplication.getAppContext());

    private static final long TRANSACTION_SEQUENCE_COUNTER = 0L;

    private static byte[] amountOtherBcd() {
        return ConvertUtils.strToBcdPaddingLeft(String.format(Locale.US, "%012d", AMOUNT_OTHER_MINOR));
    }

    private byte runPreTransProcess(@NonNull TransactionConfig config, byte searchCardMode) {
        if (!SearchMode.isSupportIcc(searchCardMode) && !SearchMode.isWave(searchCardMode)) {
            return 0;
        }
        DeviceManager.getInstance().setIDevice(EmvDeviceImpl.getInstance());
        byte[] proc = ConvertHelper.getConvert().strToBcdPaddingRight(config.getProcCode());
        EmvProcessParam cachedEmvParam = kernel.params.getCachedEmvParam();
        String dateTime = timestamp();
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

    @Override
    public void onReadCardOk() {
        if (!kernel.contactless.supportsGranularSteps()) {
            goToStep(EmvStep.READ_APPLICATION_DATA, null);
        }
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED, "contactless"));
    }

    @Override
    public int confirmCard() {
        if (!kernel.contactless.supportsGranularSteps()) {
            goToStep(EmvStep.SET_TRANSACTION_DATA, null);
        }
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

    @Override
    public int onWaitAppSelect(boolean isFirstSelect, List<CandidateAID> candList) {
        tx.candidates = candList;
        int candidates = candList == null ? 0 : candList.size();
        return askStep(isFirstSelect
                        ? EmvStep.WAIT_APPLICATION_SELECTION
                        : EmvStep.FINAL_APPLICATION_SELECTION,
                candidates + " candidate AID(s)"
                        + (showsAppSelectDialog(candList) ? "" : ", selecting first"),
                EmvConstant.ContactCallbackStatus.USER_CANCEL);
    }

    @Override
    public int showConfirmCard() {
        publish(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, safe(getContactPan()))
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "PAX Issuer")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, safe(getContactCardholderName()))
                .put(TransactionStepEvent.KEY_MODE, "Contact")
                .build());
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
            byte[] pinData) {
        tx.pinRequest = new PinRequest(isOnlinePin, supportPINByPass, leftTimes);
        try {
            if (isOnlinePin) {
                return askStep(EmvStep.CARDHOLDER_VERIFICATION, "online PIN",
                        EmvConstant.ContactCallbackStatus.USER_CANCEL);
            } else {
                goToStep(EmvStep.CARDHOLDER_VERIFICATION, "PIN");
                return askStep(EmvStep.OFFLINE_PIN_VERIFICATION, null,
                        EmvConstant.ContactCallbackStatus.USER_CANCEL);
            }
        } finally {
            tx.pinRequest = null;
        }
    }

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
            clearOnlinePin();
            if (tx.activePinPad != null) {
                tx.activePinPad.dismiss();
            }
            tx.activePinPad = new PaxPinPad("Enter Offline PIN (" + leftTimes + " left)");
            tx.activePinPad.showForOfflinePin();
            pinService.setInputPinListener(tx.activePinPad);
            EmvDeviceImpl.getInstance().setPinCallback(tx.activePinPad);
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        }

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
                clearOnlinePin();
                return bypassAllowed ? EmvConstant.ContactCallbackStatus.NO_PASSWORD
                        : EmvConstant.ContactCallbackStatus.USER_CANCEL;
            }
            tx.lastOnlinePinBlock = pinBlock;
            return EmvConstant.ContactCallbackStatus.CONTACT_OK;
        } catch (PinException e) {
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

    private static final String NO_PIN_INPUT =
            String.valueOf(EPedDevException.PED_ERR_NO_PIN_INPUT.getErrCodeFromBasement());

    private String offlinePinLengths() {
        String lengths = "4,5,6,7,8,9,10,11,12";
        return (pinBypass.isOfflinePinBypassEnabled() ? "0," + lengths : lengths) + "\0";
    }

    private void clearOnlinePin() {
        tx.lastOnlinePinBlock = null;
        tx.lastOnlinePinKeyEncrypted = null;
    }

    private void provisionOnlinePinKey() {
        tx.lastOnlinePinKeyEncrypted = null;

        byte[] pinKey = new byte[ONLINE_PIN_KEY_LENGTH_BYTES];
        new SecureRandom().nextBytes(pinKey);

        IPed ped = PedHelper.getPed();
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

    @NonNull
    @Override
    public OnlineResultWrapper startOnlineProcess() {
        tx.lastAuth = null;
        goToStep(EmvStep.START_ONLINE_PROCESS, null);
        if (tx.lastAuth == null) {
            engine().apduTrace().note("EMV", "online step did not run — online failed");
        }
        return toOnlineResultWrapper(tx.lastAuth);
    }

    private String issuerAuthenticationDetail() {
        AuthResult auth = tx.lastAuth;
        if (auth == null) {
            return "online failed: no host response";
        }
        return auth.isApproved() ? "host approved"
                : auth.isFailed() ? "online failed: " + auth.getMessage() : "host declined";
    }

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

    @Override
    public byte[] readIccTag(int tag) {
        byte[] value = readKernelTlv(tag);
        if (tag == TagsTable.AMOUNT_OTHER && (value == null || value.length == 0)) {
            return amountOtherBcd();
        }
        return value;
    }

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

    private EmvTransactionResult buildEmvTransactionResult(@Nullable String pan, @Nullable String track2,
            @Nullable String cardHolderName, @Nullable String chipData,
            @Nullable String onlinePinBlockHex, boolean contactless) {
        CvmResultEnum cvmResult = contactless
                ? (tx.clsTransResult != null ? tx.clsTransResult.getCvmResult() : CvmResultEnum.CVM_NO_CVM)
                : (tx.transResult != null ? tx.transResult.getCvmResult() : CvmResultEnum.CVM_NO_CVM);
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
        String issuerName = emvAppName != null ? emvAppName : emvAppLabel;

        return new EmvTransactionResult.Builder()
                .pan(pan)
                .maskedPan(PaxCardData.maskPan(pan))
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

    @Nullable
    private String readKernelAsciiTlv(int tag) {
        byte[] value = readKernelTlv(tag);
        return value.length == 0 ? null : PaxCardData.asciiText(value);
    }

    private byte[] readKernelTlv(int tag) {
        if (activeCard != null && activeCard.isContactless()) {
            return kernel.contactless.getTlv(tag);
        }
        return kernel.contact.getTlv(tag);
    }

    @Override
    public void onRemoveCard() {
        LogUtils.d(TAG, "remove card");
    }

    @Override
    public void onDetect2ndTap() {
        String prompt = "Tap the same card again to finish";
        engine().apduTrace().note("PICC", "2nd tap requested — " + prompt);
        publish(TransactionStepEvent.builder(
                TransactionStep.WAITING_FOR_CARD).message(prompt)
                .put(TransactionStepEvent.KEY_RETRY_PROMPT, prompt).build());
        goToStep(EmvStep.SEARCH_CARD, prompt);
    }

    @Override
    public boolean needSeePhone() {
        boolean seePhone = clsLastNeedSeePhone;
        clsLastNeedSeePhone = false;
        return seePhone;
    }

    public void offlineApproved(boolean needSignature) {
        LogUtils.e(TAG, "Offline approval reached without the host — reporting online failed");
        engine().apduTrace().note("EMV", "offline approval without the host — online failed");
        finishWithOutcome(TransactionStep.ERROR, "Online Failed",
                "Online Failed: not sent to the host");
    }

    public void offlineApproved(boolean needSignature, boolean needSetARC) {
        offlineApproved(needSignature);
    }

    public void onlineApproved(boolean needSignature) {
        completeApproved("RESULT_ONLINE_APPROVED", true);
    }

    public void onlineDenied() {
        goToStep(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
        completeDeclined("Online Denied");
    }

    public void onlineCardDenied(int resultCode) {
        goToStep(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
        completeDeclined("Online Card Denied code=" + resultCode);
    }

    public void onlineFailed() {
        String reason = tx.lastAuth != null && tx.lastAuth.isFailed() && tx.lastAuth.getMessage() != null
                ? "Online Failed: " + tx.lastAuth.getMessage()
                : "Online Failed: no host response";
        finishWithOutcome(TransactionStep.ERROR, "Online Failed", reason);
    }

    public void offlineDenied(int resultCode) {
        completeDeclined("Offline Denied code=" + resultCode);
    }

    public void seePhone() {
        int retries = retryCount();
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

    public void tryAnotherInterface() {
        retryWithMode(EntryMethod.CHIP,
                "Try Another Interface: retrying with contact", "Insert the card");
    }

    public void tryAgain() {
        int retries = retryCount();
        if (retries >= MAX_TAP_RETRIES) {
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

    public void fallback() {
        retryWithMode(EntryMethod.MAGSTRIPE, "Fallback: retrying with magstripe",
                "Chip not readable — swipe the card");
    }

    private void retryWithMode(EntryMethod mode, String reason, String prompt) {
        if (activeConfig == null) {
            finishError(reason + " — no active transaction");
            return;
        }
        LogUtils.i(TAG, reason);
        requestRetry(activeConfig.withMode(mode), prompt);
    }

    public void simpleFlowEnd() {
        completeApproved("RESULT_SIMPLE_FLOW_END", false);
    }

    private void completeApproved(String result, boolean scripts) {
        if (scripts && !tx.scriptsProcessed) {
            goToStep(EmvStep.SCRIPT_PROCESSING, null);
        }
        finishWithOutcome(TransactionStep.APPROVED, result, result);
    }

    private void completeDeclined(String reason) {
        finishWithOutcome(TransactionStep.DECLINED, reason, reason);
    }

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

    private void captureTransactionSummary() {
        boolean contactless = activeCard != null && activeCard.isContactless();
        tx.lastPan = safe(contactless ? getContactlessPan() : getContactPan());
        tx.lastTvr = readKernelTlvHex(TagsTable.TVR);
        tx.lastTacDenial = readKernelTlvHex(TagsTable.TAC_DENIAL);
        tx.lastIacDenial = readKernelTlvHex(TagsTable.IAC_DENIAL);
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
}

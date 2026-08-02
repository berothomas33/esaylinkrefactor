package com.emvenhance.emvflow.contact;

import androidx.annotation.NonNull;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;
import com.emvenhance.emvflow.EmvStepProgress;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.contact.IContactCallback;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvservice.export.IEmvContactService;
import java.util.List;

/**
 * Contact callback: auto-selects the AID, bypasses PIN, stubs the online request, and
 * reports EMV progress.
 *
 * <p>Each hook announces the furthest step the kernel must have reached to get here, and
 * {@link EmvStepProgress} fills in the steps between. That covers the whole enum even though
 * {@link IContactCallback} only exposes five hooks: the kernel performs offline data
 * authentication, processing restrictions, risk management and action analysis internally
 * without ever calling back.
 */
public class SimpleContactCallback implements IContactCallback {
    private static final String TAG = "SimpleContactCallback";

    private final IEmvContactService emv;
    private final EmvBehavior.Callback callback;
    private final EmvStepProgress progress;

    public SimpleContactCallback(@NonNull IEmvContactService emv,
            @NonNull EmvBehavior.Callback callback, @NonNull EmvStepProgress progress) {
        this.emv = emv;
        this.callback = callback;
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
        return EmvConstant.ContactCallbackStatus.CONTACT_OK; // index 0
    }

    @Override
    public int showConfirmCard() {
        progress.advanceTo(EmvStep.SET_TRANSACTION_DATA, null);
        callback.onConfirmCard(safe(emv.getPan()), "PAX Issuer", safe(emv.getCardholderName()));
        return EmvConstant.ContactCallbackStatus.CONTACT_OK;
    }

    @Override
    public int onCardHolderPwd(boolean isOnlinePin, boolean supportPINByPass, int leftTimes,
            byte[] pinData) {
        progress.advanceTo(EmvStep.CARDHOLDER_VERIFICATION, isOnlinePin ? "online PIN" : "PIN");
        if (!isOnlinePin) {
            progress.advanceTo(EmvStep.OFFLINE_PIN_VERIFICATION, null);
        }
        callback.onAskPin(isOnlinePin, supportPINByPass, leftTimes);
        if (supportPINByPass) {
            return EmvConstant.ContactCallbackStatus.NO_PASSWORD;
        }
        return EmvConstant.ContactCallbackStatus.USER_CANCEL;
    }

    @NonNull
    @Override
    public OnlineResultWrapper startOnlineProcess() {
        progress.advanceTo(EmvStep.START_ONLINE_PROCESS, null);
        callback.onNeedOnline();
        LogUtils.d(TAG, "Stub online → FAILED (no host in this demo)");
        OnlineResultWrapper wrapper = new OnlineResultWrapper();
        wrapper.setResultCode(EOnlineResult.FAILED.getResultCode());
        wrapper.setTransResultEnum(TransResultEnum.RESULT_ONLINE_FAILED);
        wrapper.setIssuerRspData(new IssuerRspData());
        return wrapper;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

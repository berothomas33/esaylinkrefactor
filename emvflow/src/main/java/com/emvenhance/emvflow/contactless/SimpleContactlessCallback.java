package com.emvenhance.emvflow.contactless;

import androidx.annotation.NonNull;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;
import com.emvenhance.emvflow.EmvStepProgress;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvbase.constant.EmvConstant;
import com.pax.emvbase.process.contactless.IContactlessCallback;
import com.pax.emvbase.process.entity.EOnlineResult;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.enums.TransResultEnum;
import com.pax.emvservice.export.IEmvContactlessService;

/**
 * Contactless callback: reports EMV progress plus the contactless-only prompts (remove card,
 * second tap, see phone), bypasses PIN and stubs the online request.
 *
 * <p>There is no application-selection hook on the contactless kernel, so
 * {@link EmvStepProgress} fills those steps in when the card data arrives.
 */
public class SimpleContactlessCallback implements IContactlessCallback {
    private static final String TAG = "SimpleContactlessCb";

    private final IEmvContactlessService emv;
    private final EmvBehavior.Callback callback;
    private final EmvStepProgress progress;

    public SimpleContactlessCallback(@NonNull IEmvContactlessService emv,
            @NonNull EmvBehavior.Callback callback, @NonNull EmvStepProgress progress) {
        this.emv = emv;
        this.callback = callback;
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
    }

    @Override
    public int confirmCard() {
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

    @Override
    public void onRemoveCard() {
        callback.onRemoveCard();
    }

    @Override
    public void onDetect2ndTap() {
        callback.onSecondTap();
    }

    @Override
    public boolean needSeePhone() {
        boolean seePhone = emv.getIsLastNeedSeePhone();
        emv.setIsLastNeedSeePhone(false);
        if (seePhone) {
            callback.onSeePhone();
        }
        return seePhone;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}

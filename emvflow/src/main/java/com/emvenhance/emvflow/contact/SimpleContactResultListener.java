package com.emvenhance.emvflow.contact;

import androidx.annotation.NonNull;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;
import com.emvenhance.emvflow.EmvStepProgress;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvservice.export.contact.IContactResultListener;

/**
 * Turns the kernel's contact outcome into the final EMV steps and a result.
 *
 * <p>Only the online outcomes advance past {@link EmvStep#START_ONLINE_PROCESS}: an offline
 * approval or decline never reaches issuer authentication or script processing, so reporting
 * those steps would be a lie.
 */
public class SimpleContactResultListener implements IContactResultListener {
    private static final String TAG = "SimpleContactResult";

    private final EmvBehavior.Callback callback;
    private final EmvStepProgress progress;

    public SimpleContactResultListener(@NonNull EmvBehavior.Callback callback,
            @NonNull EmvStepProgress progress) {
        this.callback = callback;
        this.progress = progress;
    }

    @Override
    public void offlineApproved(boolean needSignature, boolean needSetARC) {
        LogUtils.d(TAG, "offlineApproved sig=" + needSignature);
        callback.onApproved("RESULT_OFFLINE_APPROVED");
    }

    @Override
    public void onlineApproved(boolean needSignature) {
        progress.advanceTo(EmvStep.SCRIPT_PROCESSING, null);
        callback.onApproved("RESULT_ONLINE_APPROVED");
    }

    @Override
    public void onlineDenied() {
        progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "denied by issuer");
        callback.onFailed("Online Denied", null);
    }

    @Override
    public void onlineCardDenied(int resultCode) {
        progress.advanceTo(EmvStep.ISSUER_AUTHENTICATION, "declined by card");
        callback.onFailed("Online Card Denied", "code=" + resultCode);
    }

    @Override
    public void onlineFailed() {
        callback.onFailed("Online Failed", "no host response");
    }

    @Override
    public void offlineDenied(int resultCode) {
        callback.onFailed("Offline Denied", "code=" + resultCode);
    }

    @Override
    public void fallback() {
        callback.onFailed("Fallback", "Contact fallback required");
    }

    @Override
    public void simpleFlowEnd() {
        callback.onApproved("RESULT_SIMPLE_FLOW_END");
    }
}

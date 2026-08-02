package com.emvenhance.emvflow.contactless;

import androidx.annotation.NonNull;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;
import com.emvenhance.emvflow.EmvStepProgress;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvservice.export.contactless.IContactlessResultListener;

/**
 * Turns the kernel's contactless outcome into the final EMV steps and a result.
 *
 * <p>As on the contact side, only the online outcomes advance past
 * {@link EmvStep#START_ONLINE_PROCESS}.
 */
public class SimpleContactlessResultListener implements IContactlessResultListener {
    private static final String TAG = "SimpleContactlessResult";

    private final EmvBehavior.Callback callback;
    private final EmvStepProgress progress;

    public SimpleContactlessResultListener(@NonNull EmvBehavior.Callback callback,
            @NonNull EmvStepProgress progress) {
        this.callback = callback;
        this.progress = progress;
    }

    @Override
    public void offlineApproved(boolean needSignature) {
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
    public void seePhone() {
        callback.onSeePhone();
        callback.onFailed("See Phone", "Continue on the phone");
    }

    @Override
    public void tryAnotherInterface() {
        callback.onFailed("Try Another Interface", "Use contact instead");
    }

    @Override
    public void tryAgain() {
        callback.onSecondTap();
        callback.onFailed("Try Again", "Present card again");
    }

    @Override
    public void simpleFlowEnd() {
        callback.onApproved("RESULT_SIMPLE_FLOW_END");
    }
}

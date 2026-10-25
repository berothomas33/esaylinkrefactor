package com.emvenhance.vendor.ingenico;

import android.util.Log;
import com.emvenhance.core.terminal.AbstractPinPadBehavior;

class IngenicoPinPad extends AbstractPinPadBehavior {

    private static final String TAG = "IngenicoPinPad";

    @Override
    public void showForOnlinePin() {
        Log.w(TAG, "Ingenico PIN pad SDK not attached — stub showForOnlinePin");
    }

    @Override
    public void showForOfflinePin() {
        Log.w(TAG, "Ingenico PIN pad SDK not attached — stub showForOfflinePin");
    }

    @Override
    public void dismiss() {
    }

    @Override
    protected void onDigitCountChanged(int count) {
    }

    @Override
    public void onFinished() {
    }

    @Override
    public void onCancelled() {
    }

    @Override
    public void onNoPinPad() {
    }

    @Override
    public void onTimedOut() {
    }

    @Override
    public void onError(String reason) {
        Log.w(TAG, "Ingenico PIN pad error (stub): " + reason);
    }
}

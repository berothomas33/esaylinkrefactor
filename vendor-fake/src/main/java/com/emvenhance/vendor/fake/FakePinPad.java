package com.emvenhance.vendor.fake;

import android.util.Log;
import com.emvenhance.core.terminal.AbstractPinPadBehavior;

class FakePinPad extends AbstractPinPadBehavior {

    private static final String TAG = "FakePinPad";

    @Override
    public void showForOnlinePin() {
        Log.d(TAG, "fake flavor — stub showForOnlinePin, no dialog shown");
    }

    @Override
    public void showForOfflinePin() {
        Log.d(TAG, "fake flavor — stub showForOfflinePin, no dialog shown");
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
        Log.d(TAG, "fake flavor — stub onError: " + reason);
    }
}

package com.emvenhance.vendor.fake;

import android.util.Log;
import com.emvenhance.core.terminal.AbstractPinPadBehavior;

/**
 * Fake-flavor placeholder for {@link com.emvenhance.core.terminal.PinPadBehavior} — the
 * {@code fake} flavor deliberately has no hardware or vendor SDK on its classpath at all (see
 * the System Reference artifact, §11), so this simulates nothing and shows no dialog; every
 * method just logs.
 *
 * <p>Not wired into {@link FakeEmvBehavior} yet — its {@code onOfflinePinVerification} already
 * simulates a completed PIN entry synthetically (a {@code TransactionStepEvent}, no UI), so
 * there's currently nothing for a dialog to add. Wire this in only if a future dev/demo need
 * calls for an actual on-screen PIN dialog during a fake-flavor run.
 */
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
        // No dialog was ever shown — nothing to dismiss.
    }

    @Override
    protected void onDigitCountChanged(int count) {
        // No dialog to update.
    }

    @Override
    public void onFinished() {
        // No dialog to dismiss.
    }

    @Override
    public void onCancelled() {
        // No dialog to dismiss.
    }

    @Override
    public void onNoPinPad() {
        // No dialog to dismiss.
    }

    @Override
    public void onTimedOut() {
        // No dialog to dismiss.
    }

    @Override
    public void onError(String reason) {
        Log.d(TAG, "fake flavor — stub onError: " + reason);
    }
}

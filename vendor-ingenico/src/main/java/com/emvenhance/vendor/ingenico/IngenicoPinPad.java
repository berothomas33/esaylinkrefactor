package com.emvenhance.vendor.ingenico;

import android.util.Log;
import com.emvenhance.core.terminal.AbstractPinPadBehavior;

/**
 * Ingenico placeholder for {@link com.emvenhance.core.terminal.PinPadBehavior} — mirrors
 * {@link IngenicoEmvBehavior}'s own "stub, no SDK attached" shape: every method here logs
 * instead of touching real hardware or showing a dialog.
 *
 * <p>Not wired into {@link IngenicoEmvBehavior} yet — its {@code onCardholderVerification}/
 * {@code onOfflinePinVerification} are currently unreached ("stub always goes straight to
 * online"), so there's nothing real for this class to plug into. Once a real Ingenico PIN pad
 * SDK exists, replace the bodies below with real calls and wire an instance in from those two
 * methods, the same way {@code PaxEmvBehavior#onCardHolderPwd} wires up {@code PaxPinPad}.
 */
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
        Log.w(TAG, "Ingenico PIN pad error (stub): " + reason);
    }
}

package com.emvenhance.vendor.ingenico

import android.util.Log
import com.emvenhance.core.terminal.AbstractPinPadBehavior

/**
 * Ingenico placeholder for [com.emvenhance.core.terminal.PinPadBehavior] — mirrors
 * [IngenicoEmvBehavior]'s own "stub, no SDK attached" shape: every method here logs instead of
 * touching real hardware or showing a dialog.
 *
 * Not wired into [IngenicoEmvBehavior] yet — its `onCardholderVerification`/
 * `onOfflinePinVerification` are currently unreached ("stub always goes straight to online"), so
 * there is nothing real for this class to plug into. Once a real Ingenico PIN pad SDK exists,
 * replace the bodies below with real calls and wire an instance in from those two methods, the
 * same way `PaxEmvBehavior#onCardHolderPwd` wires up `PaxPinPad`.
 */
internal class IngenicoPinPad : AbstractPinPadBehavior() {

    override fun showForOnlinePin() {
        Log.w(TAG, "Ingenico PIN pad SDK not attached — stub showForOnlinePin")
    }

    override fun showForOfflinePin() {
        Log.w(TAG, "Ingenico PIN pad SDK not attached — stub showForOfflinePin")
    }

    override fun dismiss() {
        // No dialog was ever shown — nothing to dismiss.
    }

    override fun onDigitCountChanged(count: Int) {
        // No dialog to update.
    }

    override fun onFinished() {
        // No dialog to dismiss.
    }

    override fun onCancelled() {
        // No dialog to dismiss.
    }

    override fun onNoPinPad() {
        // No dialog to dismiss.
    }

    override fun onTimedOut() {
        // No dialog to dismiss.
    }

    override fun onError(reason: String?) {
        Log.w(TAG, "Ingenico PIN pad error (stub): " + reason)
    }

    private companion object {
        const val TAG = "IngenicoPinPad"
    }
}

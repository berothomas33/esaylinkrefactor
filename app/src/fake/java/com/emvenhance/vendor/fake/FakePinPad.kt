package com.emvenhance.vendor.fake

import android.util.Log
import com.emvenhance.core.terminal.AbstractPinPadBehavior

/**
 * Fake-flavor placeholder for [com.emvenhance.core.terminal.PinPadBehavior] — the `fake` flavor
 * deliberately has no hardware or vendor SDK on its classpath at all (see the System Reference
 * artifact, §11), so this simulates nothing and shows no dialog; every method just logs.
 *
 * Not wired into [FakeEmvBehavior] yet — its `onOfflinePinVerification` already simulates a
 * completed PIN entry synthetically (a `TransactionStepEvent`, no UI), so there is currently
 * nothing for a dialog to add. Wire this in only if a future dev/demo need calls for an actual
 * on-screen PIN dialog during a fake-flavor run.
 */
internal class FakePinPad : AbstractPinPadBehavior() {

    override fun showForOnlinePin() {
        Log.d(TAG, "fake flavor — stub showForOnlinePin, no dialog shown")
    }

    override fun showForOfflinePin() {
        Log.d(TAG, "fake flavor — stub showForOfflinePin, no dialog shown")
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
        Log.d(TAG, "fake flavor — stub onError: " + reason)
    }

    private companion object {
        const val TAG = "FakePinPad"
    }
}

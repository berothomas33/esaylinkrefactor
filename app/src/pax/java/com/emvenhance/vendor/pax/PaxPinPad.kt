package com.emvenhance.vendor.pax

import android.app.AlertDialog
import android.os.ConditionVariable
import android.widget.TextView
import com.emvenhance.core.terminal.AbstractPinPadBehavior
import com.emvenhance.core.terminal.PinKey
import com.emvenhance.emvflow.pin.IPinTask
import com.pax.commonlib.application.ActivityStack
import com.pax.commonlib.application.BaseApplication
import com.pax.emvservice.export.pin.PinInputCallback

/**
 * On-screen PIN-entry feedback, ported from PAX's own EMV demo (`OnlinePinTask`/`OfflinePinTask`
 * + their `BasePinTask.PinServiceInputListener` key-event adapter) — this app has no
 * `DialogUtils` of its own, so this is a minimal stand-in with the same two roles merged into one
 * class: a dot-per-digit dialog, and the `PinInputCallback` key listener that drives it.
 *
 * This is the PAX implementation of [com.emvenhance.core.terminal.PinPadBehavior] — see that
 * interface's doc for the vendor-agnostic contract this class satisfies (Ingenico/Fake get their
 * own implementations of the same contract). [IPinTask.PinCallback] and
 * [PinInputCallback.NormalCallback] are PAX SDK requirements on top of that contract —
 * `EmvDeviceImpl`/`PinService` only know how to call these two, not the shared contract directly
 * — so most of their methods are thin adapters onto it, renamed only where the PAX SDK's own
 * naming (`onFinish`/`onCancel`/`onTimeout`) doesn't already match the contract's
 * (`onFinished`/`onCancelled`/`onTimedOut`).
 *
 * Collection, encryption, and verification of the PIN are entirely `IPed`'s job (via
 * [com.pax.emvservice.emv.pin.PinService]) — this class only renders the key-event feedback
 * `IPed` reports as the cardholder types, and dismisses itself on any terminal outcome. Every
 * call here is a plain (non-`suspend`) function: the kernel and `IPed` call it on the
 * transaction thread.
 */
internal class PaxPinPad(private val title: String) :
    AbstractPinPadBehavior(), IPinTask.PinCallback, PinInputCallback.NormalCallback {

    private var dialog: AlertDialog? = null
    private var dots: TextView? = null

    // ═══════════════════════════════════════════════════════════════════
    // PinPadBehavior — the vendor-agnostic contract (core)
    // No CONTACT/CONTACTLESS split here either, same reason as PaxEmvBehavior's PIN methods:
    // ONLINE PIN PATH (showForOnlinePin, app-driven/blocking) vs OFFLINE PIN PATH
    // (showForOfflinePin, kernel-driven/fire-and-forget) vs SHARED.
    // ═══════════════════════════════════════════════════════════════════

    // [ONLINE PIN PATH — blocks the EMV-kernel thread until the dialog is on screen, since
    // PaxEmvBehavior calls PinService.getEncryptedPinData() itself immediately after]
    override fun showForOnlinePin() {
        val cv = ConditionVariable()
        BaseApplication.getAppContext().runOnUiThread {
            createDialog()
            cv.open()
        }
        cv.block()
    }

    // [OFFLINE PIN PATH — fire-and-forget; the native kernel collects/verifies the PIN itself
    // via EmvDeviceImpl#pedVerifyPlainPin/CipherPin, this dialog is UI feedback only]
    override fun showForOfflinePin() {
        BaseApplication.getAppContext().runOnUiThread { createDialog() }
    }

    // [SHARED — used by both showForOnlinePin() and showForOfflinePin()]
    private fun createDialog() {
        val activity = ActivityStack.getInstance().top()
        if (activity == null || activity.isFinishing) {
            return
        }
        val dots = TextView(activity)
        dots.textSize = 28f
        dots.setPadding(64, 48, 64, 48)
        this.dots = dots
        dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(dots)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    // [SHARED — offline path dismisses via onFinished/onCancelled/onNoPinPad/onTimedOut/onError
    // below; online path calls this directly from PaxEmvBehavior's finally block instead]
    override fun dismiss() {
        BaseApplication.getAppContext().runOnUiThread {
            dialog?.dismiss()
            dialog = null
        }
    }

    // [SHARED — AbstractPinPadBehavior.onKeyPressed() calls this after every CLEAR/INPUT event;
    // also reachable directly from onInput(int) below (IPinTask.PinCallback, offline path only)]
    override fun onDigitCountChanged(count: Int) {
        BaseApplication.getAppContext().runOnUiThread {
            dots?.text = "●".repeat(count)
        }
    }

    // [SHARED — both checkContactResult()-equivalent PAX outcomes reach these through the
    // adapters below; satisfies both PinPadBehavior.onNoPinPad/onError and IPinTask.PinCallback's
    // identically-named/-signatured methods at once, no adapter needed for these two]
    override fun onNoPinPad() {
        dismiss()
    }

    override fun onError(reason: String?) {
        dismiss()
    }

    override fun onFinished() {
        dismiss()
    }

    override fun onCancelled() {
        dismiss()
    }

    override fun onTimedOut() {
        dismiss()
    }

    // ─── PinInputCallback.NormalCallback — raw key events from IPed, via PinService. SHARED:
    // PinService.setInputPinListener(this) is wired in both branches of onCardHolderPwd(). ────
    // Translates PAX's own key-code enum into the vendor-agnostic PinKey, then hands off to
    // AbstractPinPadBehavior's shared counting logic.

    override fun keyEvent(key: PinInputCallback.EKeyCode?) {
        when (key) {
            PinInputCallback.EKeyCode.KEY_CLEAR -> onKeyPressed(PinKey.CLEAR)
            PinInputCallback.EKeyCode.KEY_ENTER -> onKeyPressed(PinKey.ENTER)
            PinInputCallback.EKeyCode.KEY_CANCEL -> onKeyPressed(PinKey.CANCEL)
            else -> onKeyPressed(PinKey.INPUT)
        }
    }

    // ─── IPinTask.PinCallback — also wired directly as EmvDeviceImpl's pinCallback, but ONLY
    // in the offline branch of onCardHolderPwd() (EmvDeviceImpl.setPinCallback(this) is never
    // called on the online path). onInput/onFinish/onCancel/onTimeout are thin adapters onto
    // the PinPadBehavior contract above, renamed only where the PAX SDK's own naming doesn't
    // already match it — see the class doc. ────────────────────────────────────────────────

    // [OFFLINE PIN PATH ONLY — same reachability as onFinish/onCancel/onTimeout below]
    override fun onInput(inputLen: Int) {
        onDigitCountChanged(inputLen)
    }

    override fun onFinish() {
        onFinished()
    }

    override fun onCancel() {
        onCancelled()
    }

    override fun onTimeout() {
        onTimedOut()
    }
}

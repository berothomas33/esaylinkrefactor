package com.emvenhance.vendor.pax

import android.app.AlertDialog
import android.os.ConditionVariable
import com.pax.commonlib.application.ActivityStack
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.utils.ConvertUtils
import com.pax.emvbase.process.contact.CandidateAID

/**
 * Multi-application selection — the cardholder picks which candidate AID to use, for a card that
 * actually offers more than one (see `PaxEmvBehavior#onWaitAppSelect`, which only builds this
 * dialog when `candList.size >= 2`; a single-application card auto-selects without ever reaching
 * here).
 *
 * Blocks the calling (EMV-kernel) thread, same pattern as [PaxPinPad.showForOnlinePin] —
 * `ContactProcess.EmvCallBackListener#emvWaitAppSel` feeds `onWaitAppSelect`'s return value
 * straight into the native kernel's `EMVCallback.setCallBackResult(index)` as the selected
 * candidate's index, so there is no async (or `suspend`) path here: the kernel is genuinely
 * waiting on this method to return, on the same stack.
 */
internal class AppSelectDialog(private val candidates: List<CandidateAID>) {

    private val done = ConditionVariable()

    @Volatile
    private var selectedIndex = 0
    private var dialog: AlertDialog? = null

    /** Shows the dialog and blocks until the cardholder taps a row, or [TIMEOUT_MS] elapses. */
    fun selectBlocking(): Int {
        BaseApplication.getAppContext().runOnUiThread { createDialog() }
        val answered = done.block(TIMEOUT_MS)
        if (!answered) {
            dismiss()
        }
        return if (answered) selectedIndex else 0
    }

    private fun createDialog() {
        val activity = ActivityStack.getInstance().top()
        if (activity == null || activity.isFinishing) {
            // No foreground Activity to attach to — fail to the same safe default the timeout
            // path uses, instead of leaving the kernel blocked with nothing on screen at all.
            done.open()
            return
        }
        val labels = Array(candidates.size) { i -> label(candidates[i]) }
        dialog = AlertDialog.Builder(activity)
            .setTitle("Select application")
            .setCancelable(false)
            .setItems(labels) { _, which ->
                selectedIndex = which
                done.open()
            }
            .create()
            .also { it.show() }
    }

    private fun dismiss() {
        BaseApplication.getAppContext().runOnUiThread {
            dialog?.dismiss()
            dialog = null
        }
    }

    private companion object {
        /**
         * Falls back to index 0 (the card's own highest-priority application — same choice the
         * previous auto-select-first behavior always made) if the cardholder doesn't answer,
         * rather than blocking the transaction forever.
         */
        const val TIMEOUT_MS = 30_000L

        /**
         * `appName` is the one field `ContactProcess`'s kernel listener actually populates on
         * every [CandidateAID] (from the native `EMV_APPLIST.appName`) —
         * `appPreName`/`appLabel`/`issDiscrData` are left at their constructor-initialized zero
         * bytes, so they're not worth reading here.
         */
        fun label(candidate: CandidateAID): String {
            val name = trimmed(candidate.appName)
            if (name.isNotEmpty()) {
                return name
            }
            val aidLen = candidate.aidLen.toInt() and 0xFF
            return "AID " + ConvertUtils.bcd2Str(candidate.aid, aidLen)
        }

        fun trimmed(bytes: ByteArray?): String {
            if (bytes == null) {
                return ""
            }
            var len = 0
            while (len < bytes.size && bytes[len].toInt() != 0) {
                len++
            }
            return String(bytes, 0, len, Charsets.US_ASCII).trim()
        }
    }
}

package com.emvenhance.vendor.pax

import android.app.AlertDialog
import com.emvenhance.core.terminal.AbstractAppSelectDialog
import com.pax.commonlib.application.ActivityStack
import com.pax.commonlib.application.BaseApplication
import com.pax.commonlib.utils.ConvertUtils
import com.pax.emvbase.process.contact.CandidateAID

/**
 * PAX's application choice: the PAX kernel's [CandidateAID]s as labels, in an [AlertDialog] on
 * the foreground Activity. The wait, the timeout and the cancel are [AbstractAppSelectDialog]'s.
 * Asked by [PaxEmvBehavior]'s application-selection step; the index goes back to the kernel's
 * `emvWaitAppSel` through `askStep`.
 */
internal class PaxAppSelectDialog : AbstractAppSelectDialog() {

    private var dialog: AlertDialog? = null

    override fun show(labels: List<String>) {
        BaseApplication.getAppContext().runOnUiThread {
            val activity = ActivityStack.getInstance().top()
            if (activity == null || activity.isFinishing) {
                onNoScreen()
                return@runOnUiThread
            }
            dialog = AlertDialog.Builder(activity)
                .setTitle("Select application")
                .setCancelable(false)
                .setItems(labels.toTypedArray()) { _, which -> onSelected(which) }
                .create()
                .also { it.show() }
        }
    }

    override fun dismiss() {
        BaseApplication.getAppContext().runOnUiThread {
            dialog?.dismiss()
            dialog = null
        }
    }

    companion object {
        /** The labels the cardholder chooses from, one per candidate, in the kernel's order. */
        fun labels(candidates: List<CandidateAID>): List<String> = candidates.map { label(it) }

        /**
         * `appName` is the one field `ContactProcess`'s kernel listener actually populates on
         * every [CandidateAID] (from the native `EMV_APPLIST.appName`) —
         * `appPreName`/`appLabel`/`issDiscrData` are left at their constructor-initialized zero
         * bytes, so they're not worth reading here.
         */
        private fun label(candidate: CandidateAID): String {
            val name = trimmed(candidate.appName)
            if (name.isNotEmpty()) {
                return name
            }
            val aidLen = candidate.aidLen.toInt() and 0xFF
            return "AID " + ConvertUtils.bcd2Str(candidate.aid, aidLen)
        }

        private fun trimmed(bytes: ByteArray?): String {
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

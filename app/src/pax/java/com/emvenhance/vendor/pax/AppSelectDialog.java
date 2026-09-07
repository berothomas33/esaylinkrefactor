package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.ConditionVariable;
import androidx.annotation.Nullable;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.emvbase.process.contact.CandidateAID;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Multi-application selection — the cardholder picks which candidate AID to use, for a card
 * that actually offers more than one (see {@link PaxEmvBehavior#onWaitAppSelect}, which only
 * builds this dialog when {@code candList.size() >= 2}; a single-application card auto-selects
 * without ever reaching here).
 *
 * <p>Blocks the calling (EMV-kernel) thread, same pattern as {@link PaxPinPad#showAndWait} —
 * {@code ContactProcess.EmvCallBackListener#emvWaitAppSel} feeds
 * {@code onWaitAppSelect}'s return value straight into the native kernel's
 * {@code EMVCallback.setCallBackResult(index)} as the selected candidate's index, so there is no
 * async path here: the kernel is genuinely waiting on this method to return.
 */
final class AppSelectDialog {

    /**
     * Falls back to index 0 (the card's own highest-priority application — same choice the
     * previous auto-select-first behavior always made) if the cardholder doesn't answer, rather
     * than blocking the transaction forever.
     */
    private static final long TIMEOUT_MS = 30_000L;

    private final List<CandidateAID> candidates;
    private final ConditionVariable done = new ConditionVariable();
    private volatile int selectedIndex;
    @Nullable
    private AlertDialog dialog;

    AppSelectDialog(List<CandidateAID> candidates) {
        this.candidates = candidates;
    }

    /** Shows the dialog and blocks until the cardholder taps a row, or {@link #TIMEOUT_MS} elapses. */
    int selectBlocking() {
        BaseApplication.getAppContext().runOnUiThread(this::createDialog);
        boolean answered = done.block(TIMEOUT_MS);
        if (!answered) {
            dismiss();
        }
        return answered ? selectedIndex : 0;
    }

    private void createDialog() {
        Activity activity = ActivityStack.getInstance().top();
        if (activity == null || activity.isFinishing()) {
            // No foreground Activity to attach to — fail to the same safe default the timeout
            // path uses, instead of leaving the kernel blocked with nothing on screen at all.
            done.open();
            return;
        }
        String[] labels = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            labels[i] = label(candidates.get(i));
        }
        dialog = new AlertDialog.Builder(activity)
                .setTitle("Select application")
                .setCancelable(false)
                .setItems(labels, (d, which) -> {
                    selectedIndex = which;
                    done.open();
                })
                .create();
        dialog.show();
    }

    private void dismiss() {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
    }

    /**
     * {@code appName} is the one field {@code ContactProcess}'s kernel listener actually
     * populates on every {@link CandidateAID} (from the native {@code EMV_APPLIST.appName}) —
     * {@code appPreName}/{@code appLabel}/{@code issDiscrData} are left at their
     * constructor-initialized zero bytes, so they're not worth reading here.
     */
    private static String label(CandidateAID candidate) {
        String name = trimmed(candidate.getAppName());
        if (!name.isEmpty()) {
            return name;
        }
        int aidLen = candidate.getAidLen() & 0xFF;
        return "AID " + ConvertUtils.bcd2Str(candidate.getAid(), aidLen);
    }

    private static String trimmed(@Nullable byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.US_ASCII).trim();
    }
}

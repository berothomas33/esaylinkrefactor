package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.ConditionVariable;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.emvbase.process.contact.CandidateAID;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Multi-application selection — the cardholder picks which candidate AID to use, for a card
 * that actually offers more than one (see {@link PaxEmvBehavior#onWaitAppSelect}, which only
 * builds this dialog when {@code candList.size() >= 2}; a single-application card auto-selects
 * without ever reaching here).
 *
 * <p>Blocks the calling (EMV-kernel) thread, same pattern as {@link PaxPinPad#showForOnlinePin} —
 * {@code ContactProcess.EmvCallBackListener#emvWaitAppSel} feeds
 * {@code onWaitAppSelect}'s return value straight into the native kernel's
 * {@code EMVCallback.setCallBackResult(index)} as the selected candidate's index, so there is no
 * async path here: the kernel is genuinely waiting on this method to return.
 */
final class AppSelectDialog {

    /** No answer: the cardholder didn't choose in time, the transaction ended, or no screen. */
    static final int NO_ANSWER = -1;

    /** How long the cardholder has to choose; then {@link #NO_ANSWER} — never "pick the first". */
    private static final long TIMEOUT_MS = 30_000L;

    /** How often the wait checks whether the transaction ended (a cancel closes the dialog). */
    private static final long POLL_MS = 250L;

    private final List<CandidateAID> candidates;
    private final ConditionVariable done = new ConditionVariable();
    private volatile int selectedIndex;
    @Nullable
    private AlertDialog dialog;

    AppSelectDialog(List<CandidateAID> candidates) {
        this.candidates = candidates;
    }

    /**
     * Shows the dialog and blocks until the cardholder taps a row. {@link #NO_ANSWER} (and the
     * dialog closed) once {@link #TIMEOUT_MS} elapses or {@code ended} turns true (a cancel).
     */
    int selectBlocking(BooleanSupplier ended) {
        BaseApplication.getAppContext().runOnUiThread(this::createDialog);
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        while (!done.block(POLL_MS)) {
            if (ended.getAsBoolean() || SystemClock.elapsedRealtime() >= deadline) {
                dismiss();
                return NO_ANSWER;
            }
        }
        return selectedIndex;
    }

    private void createDialog() {
        Activity activity = ActivityStack.getInstance().top();
        if (activity == null || activity.isFinishing()) {
            // No foreground Activity to attach to: no answer (fail closed), instead of leaving the
            // kernel blocked with nothing on screen at all.
            selectedIndex = NO_ANSWER;
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

package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import androidx.annotation.Nullable;
import com.emvenhance.core.cardholder.AbstractAppSelectDialog;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.emvbase.process.contact.CandidateAID;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * PAX's application choice: the PAX kernel's {@link CandidateAID}s as labels, in an
 * {@link AlertDialog} on the foreground Activity. The wait, the timeout and the cancel are
 * {@link AbstractAppSelectDialog}'s. Asked by {@link PaxEmvBehavior}'s application-selection
 * step; the index goes back to the kernel's {@code emvWaitAppSel} through {@code askStep}.
 */
final class PaxAppSelectDialog extends AbstractAppSelectDialog {

    @Nullable
    private AlertDialog dialog;

    /** The labels the cardholder chooses from, one per candidate, in the kernel's order. */
    static List<String> labels(List<CandidateAID> candidates) {
        List<String> labels = new ArrayList<>();
        for (CandidateAID candidate : candidates) {
            labels.add(label(candidate));
        }
        return labels;
    }

    @Override
    protected void show(List<String> labels) {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            Activity activity = ActivityStack.getInstance().top();
            if (activity == null || activity.isFinishing()) {
                onNoScreen();
                return;
            }
            dialog = new AlertDialog.Builder(activity)
                    .setTitle("Select application")
                    .setCancelable(false)
                    .setItems(labels.toArray(new String[0]), (d, which) -> onSelected(which))
                    .create();
            dialog.show();
        });
    }

    @Override
    protected void dismiss() {
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

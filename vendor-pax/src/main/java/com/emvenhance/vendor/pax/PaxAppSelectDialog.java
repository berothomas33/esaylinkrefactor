package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import androidx.annotation.Nullable;
import com.emvenhance.core.terminal.AbstractAppSelectDialog;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.ConvertUtils;
import com.pax.emvbase.process.contact.CandidateAID;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class PaxAppSelectDialog extends AbstractAppSelectDialog {

    @Nullable
    private AlertDialog dialog;

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

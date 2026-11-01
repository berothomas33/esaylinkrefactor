package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.ConditionVariable;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.emvenhance.core.cardholder.AbstractPinPadBehavior;
import com.emvenhance.core.cardholder.PinKey;
import com.emvenhance.emvflow.pin.IPinTask;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;
import com.pax.emvservice.export.pin.PinInputCallback;

final class PaxPinPad extends AbstractPinPadBehavior
        implements IPinTask.PinCallback, PinInputCallback.NormalCallback {

    private final String title;
    @Nullable
    private AlertDialog dialog;
    @Nullable
    private TextView dots;

    private static final String TAG = "PaxPinPad";

    PaxPinPad(String title) {
        this.title = title;
    }

    @Override
    public void showForOnlinePin() {
        ConditionVariable cv = new ConditionVariable();
        BaseApplication.getAppContext().runOnUiThread(() -> {
            createDialog();
            cv.open();
        });
        if (!cv.block(SHOW_TIMEOUT_MS)) {
            LogUtils.w(TAG, "online PIN pad not shown within " + SHOW_TIMEOUT_MS + " ms");
        }
    }

    private static final long SHOW_TIMEOUT_MS = 5_000L;

    @Override
    public void showForOfflinePin() {
        BaseApplication.getAppContext().runOnUiThread(this::createDialog);
    }

    private void createDialog() {
        Activity activity = ActivityStack.getInstance().top();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        dots = new TextView(activity);
        dots.setTextSize(28);
        dots.setPadding(64, 48, 64, 48);
        dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(dots)
                .setCancelable(false)
                .create();
        dialog.show();
    }

    @Override
    public void dismiss() {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
    }

    @Override
    protected void onDigitCountChanged(int count) {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dots == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < count; i++) {
                sb.append('●');
            }
            dots.setText(sb.toString());
        });
    }

    @Override
    public void onNoPinPad() {
        dismiss();
    }

    @Override
    public void onError(String reason) {
        dismiss();
    }

    @Override
    public void onFinished() {
        dismiss();
    }

    @Override
    public void onCancelled() {
        dismiss();
    }

    @Override
    public void onTimedOut() {
        dismiss();
    }

    @Override
    public void keyEvent(PinInputCallback.EKeyCode key) {
        if (key == PinInputCallback.EKeyCode.KEY_CLEAR) {
            onKeyPressed(PinKey.CLEAR);
        } else if (key == PinInputCallback.EKeyCode.KEY_ENTER) {
            onKeyPressed(PinKey.ENTER);
        } else if (key == PinInputCallback.EKeyCode.KEY_CANCEL) {
            onKeyPressed(PinKey.CANCEL);
        } else {
            onKeyPressed(PinKey.INPUT);
        }
    }

    @Override
    public void onInput(int inputLen) {
        onDigitCountChanged(inputLen);
    }

    @Override
    public void onFinish() {
        onFinished();
    }

    @Override
    public void onCancel() {
        onCancelled();
    }

    @Override
    public void onTimeout() {
        onTimedOut();
    }
}

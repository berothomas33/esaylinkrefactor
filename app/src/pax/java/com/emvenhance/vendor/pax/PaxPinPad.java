package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.ConditionVariable;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.emvenhance.emvflow.pin.IPinTask;
import com.pax.commonlib.application.ActivityStack;
import com.pax.commonlib.application.BaseApplication;
import com.pax.emvservice.export.pin.PinInputCallback;

/**
 * On-screen PIN-entry feedback, ported from PAX's own EMV demo
 * ({@code OnlinePinTask}/{@code OfflinePinTask} + their {@code BasePinTask.PinServiceInputListener}
 * key-event adapter) — this app has no {@code DialogUtils} of its own, so this is a minimal
 * stand-in with the same two roles merged into one class: a dot-per-digit dialog, and the
 * {@code PinInputCallback} key listener that drives it.
 *
 * <p>Collection, encryption, and verification of the PIN are entirely {@code IPed}'s job (via
 * {@link com.pax.emvservice.emv.pin.PinService}) — this class only renders the key-event feedback
 * {@code IPed} reports as the cardholder types, and dismisses itself on any terminal outcome.
 */
final class PaxPinPad implements IPinTask.PinCallback, PinInputCallback.NormalCallback {

    private final String title;
    private int len;
    @Nullable
    private AlertDialog dialog;
    @Nullable
    private TextView dots;

    PaxPinPad(String title) {
        this.title = title;
    }

    /** Blocks the calling (background, EMV-kernel) thread until the dialog is actually shown. */
    void showAndWait() {
        ConditionVariable cv = new ConditionVariable();
        BaseApplication.getAppContext().runOnUiThread(() -> {
            createDialog();
            cv.open();
        });
        cv.block();
    }

    /** Fire-and-forget variant — used for the offline/PCI path, which doesn't wait on this dialog. */
    void showAsync() {
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

    void dismiss() {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
    }

    // ─── PinInputCallback.NormalCallback — raw key events from IPed, via PinService ──────────

    @Override
    public void keyEvent(PinInputCallback.EKeyCode key) {
        if (key == PinInputCallback.EKeyCode.KEY_CLEAR) {
            len = 0;
        } else if (key != PinInputCallback.EKeyCode.KEY_ENTER
                && key != PinInputCallback.EKeyCode.KEY_CANCEL) {
            len++;
        } else {
            len = 0;
            return;
        }
        onInput(len);
    }

    // ─── IPinTask.PinCallback — also wired directly as EmvDeviceImpl's pinCallback ───────────

    @Override
    public void onInput(int inputLen) {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dots == null) {
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < inputLen; i++) {
                sb.append('●');
            }
            dots.setText(sb.toString());
        });
    }

    @Override
    public void onFinish() {
        dismiss();
    }

    @Override
    public void onCancel() {
        dismiss();
    }

    @Override
    public void onNoPinPad() {
        dismiss();
    }

    @Override
    public void onTimeout() {
        dismiss();
    }

    @Override
    public void onError(String reason) {
        dismiss();
    }
}

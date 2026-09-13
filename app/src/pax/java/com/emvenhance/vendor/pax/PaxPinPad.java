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

    // ═══════════════════════════════════════════════════════════════════
    // No CONTACT/CONTACTLESS split here — PIN entry (onCardHolderPwd) is
    // SHARED between both, per PaxEmvBehavior. The real split in this class
    // is which side collects the PIN: ONLINE PIN PATH (app-driven, blocking)
    // vs OFFLINE PIN PATH (kernel-driven, fire-and-forget) vs SHARED.
    // ═══════════════════════════════════════════════════════════════════

    // [ONLINE PIN PATH — app-driven: blocks the EMV-kernel thread until the dialog is on
    // screen, then PaxEmvBehavior calls PinService.getEncryptedPinData() itself]
    /** Blocks the calling (background, EMV-kernel) thread until the dialog is actually shown. */
    void showAndWait() {
        ConditionVariable cv = new ConditionVariable();
        BaseApplication.getAppContext().runOnUiThread(() -> {
            createDialog();
            cv.open();
        });
        cv.block();
    }

    // [OFFLINE PIN PATH — kernel-driven: the native kernel collects/verifies the PIN itself via
    // EmvDeviceImpl#pedVerifyPlainPin/CipherPin; this dialog is UI feedback only]
    /** Fire-and-forget variant — used for the offline/PCI path, which doesn't wait on this dialog. */
    void showAsync() {
        BaseApplication.getAppContext().runOnUiThread(this::createDialog);
    }

    // [SHARED — used by both showAndWait() and showAsync()]
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

    // [SHARED — offline path dismisses via onFinish/onCancel/onNoPinPad/onTimeout/onError below;
    // online path calls this directly from PaxEmvBehavior's finally block instead]
    void dismiss() {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
    }

    // ─── PinInputCallback.NormalCallback — raw key events from IPed, via PinService ──────────
    // [SHARED — PinService.setInputPinListener(this) is wired in both branches of
    // onCardHolderPwd(), offline and online alike]

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

    // ─── IPinTask.PinCallback — also wired directly as EmvDeviceImpl's pinCallback, but ONLY
    // in the offline branch of onCardHolderPwd() (EmvDeviceImpl.setPinCallback(this) is never
    // called on the online path) ──────────────────────────────────────────────────────────

    // [SHARED — reachable from both paths: directly here for offline (via EmvDeviceImpl), and
    // indirectly for both via keyEvent() above, which this class's own keyEvent() calls]
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

    // [OFFLINE PIN PATH ONLY — these five only fire when EmvDeviceImpl.setPinCallback(this) is
    // wired, i.e. never on the online path; online dismisses via PaxEmvBehavior's finally block]
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

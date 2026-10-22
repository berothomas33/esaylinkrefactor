package com.emvenhance.vendor.pax;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.ConditionVariable;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.emvenhance.core.terminal.AbstractPinPadBehavior;
import com.emvenhance.core.terminal.PinKey;
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
 * <p>This is the PAX implementation of {@link com.emvenhance.core.terminal.PinPadBehavior} — see
 * that interface's javadoc for the vendor-agnostic contract this class satisfies (Ingenico/Fake
 * get their own implementations of the same contract). {@link IPinTask.PinCallback} and
 * {@link PinInputCallback.NormalCallback} are PAX SDK requirements on top of that contract —
 * {@code EmvDeviceImpl}/{@code PinService} only know how to call these two, not the shared
 * contract directly — so most of their methods are thin adapters onto it, renamed only where the
 * PAX SDK's own naming ({@code onFinish}/{@code onCancel}/{@code onTimeout}) doesn't already
 * match the contract's ({@code onFinished}/{@code onCancelled}/{@code onTimedOut}).
 *
 * <p>Collection, encryption, and verification of the PIN are entirely {@code IPed}'s job (via
 * {@link com.pax.emvservice.emv.pin.PinService}) — this class only renders the key-event feedback
 * {@code IPed} reports as the cardholder types, and dismisses itself on any terminal outcome.
 */
final class PaxPinPad extends AbstractPinPadBehavior
        implements IPinTask.PinCallback, PinInputCallback.NormalCallback {

    private final String title;
    @Nullable
    private AlertDialog dialog;
    @Nullable
    private TextView dots;

    PaxPinPad(String title) {
        this.title = title;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PinPadBehavior — the vendor-agnostic contract (core)
    // No CONTACT/CONTACTLESS split here either, same reason as PaxEmvBehavior's PIN methods:
    // ONLINE PIN PATH (showForOnlinePin, app-driven/blocking) vs OFFLINE PIN PATH
    // (showForOfflinePin, kernel-driven/fire-and-forget) vs SHARED.
    // ═══════════════════════════════════════════════════════════════════

    // [ONLINE PIN PATH — blocks the EMV-kernel thread until the dialog is on screen, since
    // PaxEmvBehavior calls PinService.getEncryptedPinData() itself immediately after]
    @Override
    public void showForOnlinePin() {
        ConditionVariable cv = new ConditionVariable();
        BaseApplication.getAppContext().runOnUiThread(() -> {
            createDialog();
            cv.open();
        });
        cv.block();
    }

    // [OFFLINE PIN PATH — fire-and-forget; the native kernel collects/verifies the PIN itself
    // via EmvDeviceImpl#pedVerifyPlainPin/CipherPin, this dialog is UI feedback only]
    @Override
    public void showForOfflinePin() {
        BaseApplication.getAppContext().runOnUiThread(this::createDialog);
    }

    // [SHARED — used by both showForOnlinePin() and showForOfflinePin()]
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

    // [SHARED — offline path dismisses via onFinished/onCancelled/onNoPinPad/onTimedOut/onError
    // below; online path calls this directly from PaxEmvBehavior's finally block instead]
    @Override
    public void dismiss() {
        BaseApplication.getAppContext().runOnUiThread(() -> {
            if (dialog != null) {
                dialog.dismiss();
                dialog = null;
            }
        });
    }

    // [SHARED — AbstractPinPadBehavior.onKeyPressed() calls this after every CLEAR/INPUT event;
    // also reachable directly from onInput(int) below (IPinTask.PinCallback, offline path only)]
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

    // [SHARED — both checkContactResult()-equivalent PAX outcomes reach these through the
    // adapters below; satisfies both PinPadBehavior.onNoPinPad/onError and IPinTask.PinCallback's
    // identically-named/-signatured methods at once, no adapter needed for these two]
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

    // ─── PinInputCallback.NormalCallback — raw key events from IPed, via PinService. SHARED:
    // PinService.setInputPinListener(this) is wired in both branches of onCardHolderPwd(). ────
    // Translates PAX's own key-code enum into the vendor-agnostic PinKey, then hands off to
    // AbstractPinPadBehavior's shared counting logic.

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

    // ─── IPinTask.PinCallback — also wired directly as EmvDeviceImpl's pinCallback, but ONLY
    // in the offline branch of onCardHolderPwd() (EmvDeviceImpl.setPinCallback(this) is never
    // called on the online path). onInput/onFinish/onCancel/onTimeout are thin adapters onto
    // the PinPadBehavior contract above, renamed only where the PAX SDK's own naming doesn't
    // already match it — see the class doc. ────────────────────────────────────────────────

    // [OFFLINE PIN PATH ONLY — same reachability as onFinish/onCancel/onTimeout below]
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

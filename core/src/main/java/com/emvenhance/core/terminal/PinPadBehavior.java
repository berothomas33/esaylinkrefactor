package com.emvenhance.core.terminal;

/**
 * Vendor PIN-pad contract — same shape of promise as {@link EmvBehavior}: every vendor flavor
 * (pax / ingenico / fake) implements this once, and the rest of the app never has to know
 * which hardware is actually collecting the PIN.
 *
 * <p>This is deliberately narrow. Collection, encryption, and verification of the PIN are the
 * vendor SDK's own job (on PAX, {@code IPed} via {@code PinService}) — this contract covers
 * only the on-screen feedback a PIN entry needs: show a dialog, render keystroke feedback as
 * it happens, and tear the dialog down on whatever terminal outcome the vendor SDK reports.
 *
 * <p>Two show methods, not one, because PIN entry has two genuinely different shapes
 * (see the EMV Transaction Backbone artifact, §08, for the contact-path timeline this maps
 * onto):
 * <ul>
 *   <li>{@link #showForOnlinePin()} — app-driven. Online PIN never touches the card, so the
 *       app itself must collect it; the vendor behavior typically blocks the calling thread
 *       until the dialog is actually on screen, since a blocking PIN-collection call follows
 *       immediately.</li>
 *   <li>{@link #showForOfflinePin()} — kernel-driven. The vendor's native kernel collects and
 *       verifies the PIN itself; this dialog is fire-and-forget UI feedback only, driven by
 *       {@link #onKeyPressed} events the kernel reports back through this same instance.</li>
 * </ul>
 */
public interface PinPadBehavior {

    void showForOnlinePin();

    void showForOfflinePin();

    void dismiss();

    void onKeyPressed(PinKey key);

    void onFinished();

    void onCancelled();

    void onNoPinPad();

    void onTimedOut();

    void onError(String reason);
}

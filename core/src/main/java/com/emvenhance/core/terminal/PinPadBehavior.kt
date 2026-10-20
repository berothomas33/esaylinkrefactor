package com.emvenhance.core.terminal

/**
 * Vendor PIN-pad contract — same shape of promise as [EmvBehavior]: every vendor flavor
 * (pax / ingenico / fake) implements this once, and the rest of the app never has to know which
 * hardware is actually collecting the PIN.
 *
 * This is deliberately narrow. Collection, encryption, and verification of the PIN are the
 * vendor SDK's own job (on PAX, `IPed` via `PinService`) — this contract covers only the
 * on-screen feedback a PIN entry needs: show a dialog, render keystroke feedback as it happens,
 * and tear the dialog down on whatever terminal outcome the vendor SDK reports.
 *
 * Two show methods, not one, because PIN entry has two genuinely different shapes:
 * - [showForOnlinePin] — app-driven. Online PIN never touches the card, so the app itself must
 *   collect it; the vendor behavior typically blocks the calling (transaction) thread until the
 *   dialog is actually on screen, since a blocking PIN-collection call follows immediately.
 * - [showForOfflinePin] — kernel-driven. The vendor's native kernel collects and verifies the
 *   PIN itself; this dialog is fire-and-forget UI feedback only, driven by [onKeyPressed]
 *   events the kernel reports back through this same instance.
 *
 * Plain blocking functions, not `suspend`: they are called from kernel callbacks on the
 * transaction thread, which cannot suspend.
 */
interface PinPadBehavior {

    fun showForOnlinePin()

    fun showForOfflinePin()

    fun dismiss()

    fun onKeyPressed(key: PinKey)

    fun onFinished()

    fun onCancelled()

    fun onNoPinPad()

    fun onTimedOut()

    fun onError(reason: String?)
}

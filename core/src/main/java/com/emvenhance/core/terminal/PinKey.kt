package com.emvenhance.core.terminal;

/**
 * Vendor-agnostic bucket for a raw PIN-pad key event, reported to {@link PinPadBehavior}.
 *
 * <p>Only the four buckets {@link #onDigitCountChanged} logic actually cares about — a real
 * digit's value is never needed by the UI feedback (a dot-per-keystroke dialog), only whether
 * the keystroke count should go up, reset, or the entry is done. {@link #INPUT} covers every
 * key that isn't {@link #CLEAR}/{@link #ENTER}/{@link #CANCEL} — including a vendor SDK's own
 * function/alpha/star keys, not just 0-9 — matching how PAX's own key-code enum was actually
 * consumed before this contract existed.
 */
public enum PinKey {
    INPUT,
    CLEAR,
    ENTER,
    CANCEL
}

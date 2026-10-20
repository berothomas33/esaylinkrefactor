package com.emvenhance.core.terminal

/**
 * Vendor base for [PinPadBehavior]: hoists the one piece of logic every vendor needs
 * identically — turning a stream of [PinKey] events into a keystroke count — so a vendor
 * subclass only has to render that count, not re-derive it.
 *
 * Ported directly from PAX's own `PaxPinPad.keyEvent()` counting logic (the first, PAX-only
 * implementation of this contract): [PinKey.CLEAR] resets the count and still reports it (the
 * dialog needs to redraw with zero dots); [PinKey.ENTER]/[PinKey.CANCEL] reset the count but
 * report nothing (the dialog is about to close, not redraw); anything else ([PinKey.INPUT])
 * increments and reports.
 */
abstract class AbstractPinPadBehavior : PinPadBehavior {

    private var digitCount = 0

    final override fun onKeyPressed(key: PinKey) {
        when (key) {
            PinKey.CLEAR -> digitCount = 0
            PinKey.ENTER, PinKey.CANCEL -> {
                digitCount = 0
                return
            }
            PinKey.INPUT -> digitCount++
        }
        onDigitCountChanged(digitCount)
    }

    /** Render `count` dots (or whatever this vendor's dialog uses for keystroke feedback). */
    protected abstract fun onDigitCountChanged(count: Int)
}

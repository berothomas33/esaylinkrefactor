package com.emvenhance.core.terminal;

/**
 * Vendor base for {@link PinPadBehavior}: hoists the one piece of logic every vendor needs
 * identically — turning a stream of {@link PinKey} events into a keystroke count — so a vendor
 * subclass only has to render that count, not re-derive it.
 *
 * <p>Ported directly from PAX's own {@code PaxPinPad.keyEvent()} counting logic (the first,
 * PAX-only implementation of this contract): {@link PinKey#CLEAR} resets the count and still
 * reports it (the dialog needs to redraw with zero dots); {@link PinKey#ENTER}/
 * {@link PinKey#CANCEL} reset the count but report nothing (the dialog is about to close, not
 * redraw); anything else ({@link PinKey#INPUT}) increments and reports.
 */
public abstract class AbstractPinPadBehavior implements PinPadBehavior {

    private int digitCount;

    @Override
    public final void onKeyPressed(PinKey key) {
        switch (key) {
            case CLEAR:
                digitCount = 0;
                break;
            case ENTER:
            case CANCEL:
                digitCount = 0;
                return;
            case INPUT:
            default:
                digitCount++;
                break;
        }
        onDigitCountChanged(digitCount);
    }

    /** Render {@code count} dots (or whatever this vendor's dialog uses for keystroke feedback). */
    protected abstract void onDigitCountChanged(int count);
}

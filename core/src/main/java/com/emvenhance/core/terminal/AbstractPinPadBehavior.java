package com.emvenhance.core.terminal;

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

    protected abstract void onDigitCountChanged(int count);
}

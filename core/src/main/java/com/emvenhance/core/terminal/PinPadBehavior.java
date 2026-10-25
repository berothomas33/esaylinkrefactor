package com.emvenhance.core.terminal;

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

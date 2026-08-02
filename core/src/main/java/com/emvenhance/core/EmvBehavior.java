package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * EMV behavior: drives the vendor kernel and reports progress.
 *
 * <p>Card detection and PIN entry live here rather than in separate interfaces, because
 * vendor kernels already own them internally — on PAX both happen inside the kernel's
 * own callback while {@code startTransProcess} is running.
 *
 * <p>Callbacks arrive on the kernel's background thread.
 */
public interface EmvBehavior {

    interface Callback {

        void onStep(EmvStep step, @Nullable String detail);

        void onConfirmCard(String pan, String issuerName, String cardHolderName);

        void onAskPin(boolean onlinePin, boolean bypassAllowed, int leftTimes);

        /** Kernel decided the transaction must go online. */
        void onNeedOnline();

        void onRemoveCard();

        void onSecondTap();

        void onSeePhone();

        void onApproved(String result);

        void onFailed(String title, @Nullable String reason);
    }

    /**
     * Terminal initialization, before any card is read.
     *
     * @return true when the kernel is ready to search for a card
     */
    boolean prepare(String procCode, long amountMinor, boolean contact, boolean contactless);

    void startContact(Callback callback);

    void startContactless(Callback callback);
}

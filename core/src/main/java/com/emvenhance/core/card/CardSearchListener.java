package com.emvenhance.core.card;

import androidx.annotation.Nullable;

public interface CardSearchListener {

    void onSearchStarted(TransactionConfig config);

    void onChipDetected(CardPresence card);

    void onContactlessDetected(CardPresence card);

    void onMagstripeDetected(CardPresence card);

    void onManualEntrySelected(CardPresence card);

    void onSearchTimeout();

    void onSearchCancelled();

    void onReaderError(String message);

    default void onSearchRetry(String message) {
    }

}

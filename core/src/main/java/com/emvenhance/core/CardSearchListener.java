package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Listener for unified card-reader events owned by {@link PosTerminal}.
 *
 * <p>Vendor terminals fire these from their native SDK polling. The selected entry method
 * is delivered via the detection callbacks (and the returned {@link CardPresence}).
 */
public interface CardSearchListener {

    /** Readers opened / polling started. */
    void onSearchStarted(TransactionConfig config);

    /** ICC / chip card inserted and powered. */
    void onChipDetected(CardPresence card);

    /** Contactless / NFC / PICC card detected. */
    void onContactlessDetected(CardPresence card);

    /** Magnetic stripe swiped and tracks read. */
    void onMagstripeDetected(CardPresence card);

    /** Operator chose manual PAN entry (no physical card). */
    void onManualEntrySelected(CardPresence card);

    /** Card was removed during search (when the vendor SDK reports it). */
    void onCardRemoved();

    /** No card presented before the vendor timeout. */
    void onSearchTimeout();

    /** Search aborted by {@link PosTerminal#cancelTransaction()}. */
    void onSearchCancelled();

    /** Hardware / reader failure. */
    void onReaderError(String message);

    /** Optional no-op defaults for adapters that only care about a subset. */
    abstract class Adapter implements CardSearchListener {
        @Override public void onSearchStarted(TransactionConfig config) { }
        @Override public void onChipDetected(CardPresence card) { }
        @Override public void onContactlessDetected(CardPresence card) { }
        @Override public void onMagstripeDetected(CardPresence card) { }
        @Override public void onManualEntrySelected(CardPresence card) { }
        @Override public void onCardRemoved() { }
        @Override public void onSearchTimeout() { }
        @Override public void onSearchCancelled() { }
        @Override public void onReaderError(String message) { }
    }
}

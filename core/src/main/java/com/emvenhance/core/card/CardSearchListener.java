package com.emvenhance.core.card;

import com.emvenhance.core.transaction.TransactionConfig;

import androidx.annotation.Nullable;

/**
 * Listener for unified card-reader events: what a vendor's card search reports, from its native
 * SDK polling, in its {@code onSearchCard} step ({@code AbstractEmvBehavior#cardSearchListener}).
 * The selected entry method is delivered via the detection callbacks.
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

    /** No card presented before the vendor timeout. */
    void onSearchTimeout();

    /** Search aborted: its transaction was cancelled ({@code EmvBehavior#cancelCardSearch}). */
    void onSearchCancelled();

    /** Hardware / reader failure. */
    void onReaderError(String message);

    /**
     * A read attempt failed in a way the cardholder can fix (a partial tap, two cards on the
     * reader, a badly seated chip) and the search carries on — {@code message} says what to do.
     */
    default void onSearchRetry(String message) {
        // default: nothing to show
    }

    /** Optional no-op defaults for adapters that only care about a subset. */

}

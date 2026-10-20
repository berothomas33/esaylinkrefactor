package com.emvenhance.core.card

/**
 * Listener for unified card-reader events owned by
 * [com.emvenhance.core.terminal.PosTerminal].
 *
 * Vendor terminals fire these from their native SDK polling, on the transaction thread. The
 * selected entry method is delivered via the detection callbacks (and the returned
 * [CardPresence]). Plain functions, not `suspend`: a vendor's polling loop calls them.
 */
interface CardSearchListener {

    /** Readers opened / polling started. */
    fun onSearchStarted(config: TransactionConfig)

    /** ICC / chip card inserted and powered. */
    fun onChipDetected(card: CardPresence)

    /** Contactless / NFC / PICC card detected. */
    fun onContactlessDetected(card: CardPresence)

    /** Magnetic stripe swiped and tracks read. */
    fun onMagstripeDetected(card: CardPresence)

    /** Operator chose manual PAN entry (no physical card). */
    fun onManualEntrySelected(card: CardPresence)

    /** Card was removed during search (when the vendor SDK reports it). */
    fun onCardRemoved()

    /** No card presented before the vendor timeout. */
    fun onSearchTimeout()

    /**
     * Search aborted: its transaction was cancelled (see
     * [com.emvenhance.core.terminal.PosTerminal.startTransaction]).
     */
    fun onSearchCancelled()

    /** Hardware / reader failure. */
    fun onReaderError(message: String?)

    /**
     * A read attempt failed in a way the cardholder can fix (a partial tap, two cards on the
     * reader, a badly seated chip) and the search carries on — `message` says what to do.
     */
    fun onSearchRetry(message: String) {
        // default: nothing to show
    }
}

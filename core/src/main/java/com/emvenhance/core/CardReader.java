package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Hardware card-reader API owned by {@link PosTerminal}.
 *
 * <p>EmvBehavior asks the terminal to search; the engine never touches readers.
 */
public interface CardReader {

    /**
     * Blocking search for contact / contactless / magstripe.
     *
     * @return detected card, or {@code null} on timeout / cancel
     */
    @Nullable
    CardPresence searchCard(TransactionConfig config);

    /** True when {@link PosTerminal#cancelTransaction()} was requested. */
    boolean isSearchCancelled();
}

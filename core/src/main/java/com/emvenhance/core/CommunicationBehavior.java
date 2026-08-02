package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Communication behavior: online authorization against the acquirer host.
 *
 * <p>Called on the kernel's background thread, so a blocking network call is fine here.
 */
public interface CommunicationBehavior {

    /**
     * @return true when the host approved the transaction
     */
    boolean authorize(@Nullable String pan, long amountMinor);
}

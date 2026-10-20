package com.emvenhance.core.host;

import com.emvenhance.core.card.TransactionConfig;
import io.reactivex.rxjava3.core.Single;

/**
 * Acquirer / host authorize port. Owned by {@link com.emvenhance.core.terminal.PosTerminal}.
 */
public interface CommunicationBehavior {

    Single<AuthResult> authorize(TransactionConfig config);
}

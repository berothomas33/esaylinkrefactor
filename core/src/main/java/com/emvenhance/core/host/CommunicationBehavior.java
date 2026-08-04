package com.emvenhance.core.host;
import com.emvenhance.core.card.TransactionConfig;

import io.reactivex.rxjava3.core.Single;

/**
 * Communication behavior: online authorization against the acquirer host.
 *
 * <p>Returns a {@link Single} so the step handler can subscribe independently without
 * chaining the result into a large reactive orchestration pipeline.
 */
public interface CommunicationBehavior {

    /**
     * Sends the authorization request for the given transaction.
     *
     * @return host result (approved or declined)
     */
    Single<AuthResult> authorize(TransactionConfig config);
}

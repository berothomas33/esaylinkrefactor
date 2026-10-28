package com.emvenhance.core.host;

import com.emvenhance.core.transaction.TransactionConfig;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * Acquirer / host authorize port. Owned by {@link com.emvenhance.core.terminal.PosTerminal}.
 *
 * <p>After a transaction that went online ends, {@code PosTerminal} closes the host's side of it
 * with one of {@link #acknowledge} (the sale is done) or {@link #reverse} (the host must undo
 * it), named by the request it was sent ({@link TransactionConfig#getAsyncRequestId()}). A host
 * with no such calls keeps the defaults: nothing to send.
 */
public interface CommunicationBehavior {

    Single<AuthResult> authorize(TransactionConfig config);

    /**
     * The host approved and the card took it: the sale is complete on the terminal's side.
     *
     * @param sent the request as {@link #authorize} was given it (its {@code asyncRequestId})
     */
    default Completable acknowledge(TransactionConfig sent) {
        return Completable.complete();
    }

    /**
     * Undo what the host may have approved: the card refused it, the sale was cancelled, or the
     * host's answer never came back.
     *
     * @param sent the request as {@link #authorize} was given it (its {@code asyncRequestId})
     */
    default Completable reverse(TransactionConfig sent) {
        return Completable.complete();
    }
}

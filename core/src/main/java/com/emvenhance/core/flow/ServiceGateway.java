package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;

/**
 * The outbound capabilities a step behavior is allowed to use.
 *
 * <p>Behaviors never hold a {@link com.emvenhance.core.host.CommunicationBehavior} or
 * {@link com.emvenhance.core.host.PrinterBehavior}; they call through here, which forwards to
 * {@link com.emvenhance.core.engine.EmvEngine}, which forwards to the ports
 * {@link com.emvenhance.core.terminal.PosTerminal} attached. One owner, one call site.
 */
public interface ServiceGateway {

    /** Host authorization. Publishes the ONLINE_* transaction steps around the call. */
    @NonNull
    Single<AuthResult> authorize(@NonNull TransactionConfig config);

    /** Receipt printing. */
    @NonNull
    Completable print(@NonNull List<String> lines);

    /**
     * Cardholder PIN entry.
     *
     * @param onlinePin  true for online PIN, false for offline PIN verification
     * @param triesLeft  attempts remaining as reported by the card
     */
    @NonNull
    Single<PinResult> promptPin(boolean onlinePin, int triesLeft);
}

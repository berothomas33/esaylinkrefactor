package com.emvenhance.network;

import android.content.Context;
import android.util.Log;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.network.model.SaleResponse;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;

/**
 * The {@link CommunicationBehavior} that talks to our host: every transaction goes out as
 * {@code orchestration/exchange} followed by its own request, in the encrypted envelope
 * ({@link HostEnvelope}). This class only routes by {@link TransactionType} and checks what each
 * request needs; the bodies are built in {@link HostRequests} and the answers read in
 * {@link HostResponses}.
 *
 * <p>Headers come from {@link HostHeaders#buildAuthenticated}, with this terminal's serial number
 * ({@link #sn}, passed in by {@code PaxTerminal}: this module stays vendor-agnostic) and an access
 * token from {@link OnboardingClient#ensureValidAccessToken}, refreshed first if near or past
 * expiry.
 *
 * <p>Card reading is the vendor {@code EmvBehavior}'s job: {@link TransactionConfig#getEmvResult()}
 * and field 55 are attached before {@link #authorize} is called. Out of scope here, unlike the old
 * project's {@code SaleActivity}: offline pending-transaction bookkeeping, receipt printing, the
 * acknowledgement and reversal calls, and the PENDING-status retry loop. A failed or PENDING
 * envelope surfaces once as a {@link Single#error}, which {@code EmvEngine} reports as online
 * failed.
 */
public final class HostCommunicationBehavior implements CommunicationBehavior {

    private final HostApiConnection connection;
    private final HostEnvelope envelope;
    private final OnboardingClient onboarding;
    private final OnboardingState onboardingState;
    private final String sn;

    public HostCommunicationBehavior(Context context, String sn) {
        this(HostApiClient.create(), new OnboardingClient(), new OnboardingState(context),
                new HostSettings(context), sn);
    }

    public HostCommunicationBehavior(HostApiConnection connection, OnboardingClient onboarding,
            OnboardingState onboardingState, HostSettings hostSettings, String sn) {
        this.connection = connection;
        this.envelope = new HostEnvelope(connection, hostSettings);
        this.onboarding = onboarding;
        this.onboardingState = onboardingState;
        this.sn = sn;
    }

    /**
     * Sends the transaction to the host by its type: SALE → {@code orchestration/sale}, REFUND →
     * {@code orchestration/refund}, each after its own {@code orchestration/exchange}. Anything
     * that can't go online — a type with no host request yet, a refund without the original
     * reference number — is {@link AuthResult#failed online failed}: this terminal approves
     * nothing offline, and a type is never sent as another (a refund sent as a PURCHASE would
     * charge the card).
     */
    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        TransactionType type = config.getType();
        if (type == TransactionType.REFUND) {
            String reference = config.getReferenceNumber();
            if (reference == null || reference.trim().isEmpty()) {
                Log.w(HostEnvelope.TAG, "Refund without the original reference number — not sent to the host");
                return Single.just(AuthResult.failed(
                        "Refund needs the original sale's reference number"));
            }
        } else if (type != TransactionType.SALE) {
            // CASH_IN (POS-as-ATM deposit: atm/exchange + atm/deposit) has no request body yet —
            // the old app's screen that built it wasn't shared.
            Log.w(HostEnvelope.TAG, type + " has no host request yet — not sent to the host");
            return Single.just(AuthResult.failed(type.getLabel() + " isn't supported online yet"));
        }
        EmvTransactionResult emvResult = config.getEmvResult();
        if (emvResult == null) {
            return Single.error(new HostException(
                    "No EmvTransactionResult on TransactionConfig — the vendor EmvBehavior must "
                            + "attach one (see TransactionConfig#withEmvResult) before going online"));
        }
        String hostPublicKey = onboardingState.getPublicKey();
        if (hostPublicKey == null) {
            return Single.error(new HostException(
                    "No host public key on file — onboarding must complete before a TEK can be wrapped for the host"));
        }
        if (onboardingState.getAccessToken() == null) {
            return Single.error(new HostException(
                    "No access token on file — onboarding must complete before a transaction can go online"));
        }

        return onboarding.ensureValidAccessToken(HostHeaders.build(sn), onboardingState)
                .flatMap(accessToken -> goOnline(HostHeaders.buildAuthenticated(sn, accessToken),
                        config, emvResult, hostPublicKey));
    }

    private Single<AuthResult> goOnline(Map<String, String> headers, TransactionConfig config,
            EmvTransactionResult emvResult, String hostPublicKey) {
        HostEnvelope.Session session = envelope.open(headers, hostPublicKey);
        return envelope.exchange(session,
                        HostRequests.exchange(session.asyncRequestId, config, emvResult),
                        HostRequests.transactionType(config.getType()),
                        HostRequests.pinKeyToSend(config))
                .flatMap(exchanged -> request(session, config, emvResult))
                .map(response -> HostResponses.toAuthResult(response)
                        .withAsyncRequestId(session.asyncRequestId));
    }

    /** This transaction's own request, in the session's envelope. */
    private Single<SaleResponse> request(HostEnvelope.Session session, TransactionConfig config,
            EmvTransactionResult emvResult) {
        HostEnvelope.logSerialized("CHIP_DATA (field 55 sent)", String.valueOf(config.getIccData()));
        String pinBlock = HostRequests.pinBlockToSend(config);
        if (config.getType() == TransactionType.REFUND) {
            // The old RefundResponse has SaleResponse's fields (responseCode, responseMessage,
            // authCode, chipData, referenceNumber, ...), so it's decoded the same way.
            return envelope.send(session, "Refund", HostRequests.refund(config, emvResult),
                    pinBlock, connection::refund, SaleResponse.class);
        }
        return envelope.send(session, "Sale", HostRequests.sale(config, emvResult),
                pinBlock, connection::sale, SaleResponse.class);
    }
}

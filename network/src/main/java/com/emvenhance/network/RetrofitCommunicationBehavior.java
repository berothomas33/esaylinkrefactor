package com.emvenhance.network;

import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.network.model.PurchaseRequest;
import com.emvenhance.network.model.PurchaseResponse;
import com.emvenhance.network.tlv.BerTlv;
import com.emvenhance.network.tlv.IssuerResponseFields;

import io.reactivex.rxjava3.core.Single;

/**
 * Real {@link CommunicationBehavior} — calls the host's online purchase endpoint and maps the
 * response into {@link AuthResult}, following the old project's {@code EmvApiClient#purchase}
 * behavior. Replaces {@code HostDefaults.declineUntilWired()} once a vendor terminal is ready to
 * go online for real.
 *
 * <p>The response's EMV tags (ARPC / issuer scripts) arrive bundled inside one {@code chipData}
 * hex BER-TLV blob rather than as separate JSON fields — same shape the old project used. See
 * {@link BerTlv}/{@link IssuerResponseFields} for the extraction and the caveat on the exact tag
 * set an issuer bundles there.
 */
public final class RetrofitCommunicationBehavior implements CommunicationBehavior {

    private final HostApiConnection connection;

    public RetrofitCommunicationBehavior() {
        this(HostApiClient.create());
    }

    public RetrofitCommunicationBehavior(HostApiConnection connection) {
        this.connection = connection;
    }

    @Override
    public Single<AuthResult> authorize(TransactionConfig config) {
        PurchaseRequest request = new PurchaseRequest(
                config.getAmountMinor(),
                config.getProcCode(),
                PosEntryModeCodes.forEntryMethod(config.getMode()),
                config.getIccData(),
                config.getPan(),
                config.getOnlinePinBlock(),
                config.getOnlinePinKeyEncrypted());

        return connection.purchase(request).map(RetrofitCommunicationBehavior::toAuthResult);
    }

    private static AuthResult toAuthResult(PurchaseResponse response) {
        String responseCode = response.getResponseCode() != null
                ? String.format("%02d", response.getResponseCode())
                : "96";

        boolean approved = "00".equals(responseCode);
        if (!approved) {
            String message = response.getResponseMessage() != null
                    ? response.getResponseMessage() : "Declined";
            return AuthResult.declined(responseCode, message);
        }

        String chipData = response.getChipData();
        byte[] arpc = IssuerResponseFields.arpc(chipData);
        byte[] issuerScript = IssuerResponseFields.issuerScript(chipData);

        String authCode = response.getAuthCode() != null ? response.getAuthCode() : "";
        return AuthResult.approved(authCode, responseCode, arpc, issuerScript);
    }
}

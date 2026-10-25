package com.emvenhance.network;

import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.network.model.PurchaseRequest;
import com.emvenhance.network.model.PurchaseResponse;
import com.emvenhance.network.tlv.BerTlv;
import com.emvenhance.network.tlv.IssuerResponseFields;

import io.reactivex.rxjava3.core.Single;

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

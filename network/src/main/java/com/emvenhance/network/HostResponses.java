package com.emvenhance.network;

import com.emvenhance.core.host.AuthResult;
import com.emvenhance.network.model.SaleResponse;
import com.emvenhance.network.tlv.IssuerResponseFields;

/** The host's answers, turned into the {@link AuthResult} the EMV side completes the card with. */
final class HostResponses {

    private HostResponses() {
    }

    /**
     * {@code responseCode} 0 is approved, anything else declined. On approval, the issuer's EMV
     * data in {@code chipData} goes back to the card: the ARPC (tag 91) and issuer scripts (71/72).
     */
    static AuthResult toAuthResult(SaleResponse response) {
        if (response.getResponseCode() != 0) {
            String message = response.getResponseMessage() != null ? response.getResponseMessage() : "Declined";
            return AuthResult.declined(String.valueOf(response.getResponseCode()), message);
        }
        String authCode = response.getAuthCode() != null ? response.getAuthCode() : "";
        byte[] arpc = IssuerResponseFields.arpc(response.getChipData());
        byte[] issuerScript = IssuerResponseFields.issuerScript(response.getChipData());
        return AuthResult.approved(authCode, "00", arpc, issuerScript);
    }
}

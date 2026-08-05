package com.emvenhance.core.host;

import androidx.annotation.Nullable;

/**
 * Online authorization outcome used by a vendor when finishing an EMV online step.
 *
 * <p>Not injected into {@code EmvBehavior} — vendors create / receive this where they
 * handle online themselves.
 */
public final class AuthResult {

    private final boolean approved;
    private final String authCode;
    private final String responseCode;
    private final String message;
    @Nullable
    private final byte[] issuerData;

    private AuthResult(boolean approved, @Nullable String authCode,
            @Nullable String responseCode, @Nullable String message,
            @Nullable byte[] issuerData) {
        this.approved = approved;
        this.authCode = authCode;
        this.responseCode = responseCode;
        this.message = message;
        this.issuerData = issuerData;
    }

    public static AuthResult approved(String authCode, String responseCode,
            @Nullable byte[] issuerData) {
        return new AuthResult(true, authCode, responseCode, "Approved", issuerData);
    }

    public static AuthResult declined(String responseCode, String message) {
        return new AuthResult(false, null, responseCode, message, null);
    }

    public boolean isApproved() {
        return approved;
    }

    @Nullable
    public String getAuthCode() {
        return authCode;
    }

    @Nullable
    public String getResponseCode() {
        return responseCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public byte[] getIssuerData() {
        return issuerData;
    }
}

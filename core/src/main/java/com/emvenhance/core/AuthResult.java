package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Result of an online authorization against the acquirer host.
 *
 * <p>Created by the vendor {@link EmvBehavior} during online processing and delivered
 * back to the kernel so it can finish issuer authentication and the second GENERATE AC.
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

    /**
     * @param authCode      host authorization code (e.g. "123456")
     * @param responseCode  host response code (e.g. "00")
     * @param issuerData    optional Field 55 / issuer scripts for the kernel
     */
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

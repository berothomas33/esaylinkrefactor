package com.emvenhance.core.host;

import androidx.annotation.Nullable;

public final class AuthResult {

    private final boolean approved;
    private final boolean failed;
    private final String authCode;
    private final String responseCode;
    private final String message;
    @Nullable
    private final byte[] arpc;
    @Nullable
    private final byte[] issuerScript;
    @Nullable
    private final String asyncRequestId;

    private AuthResult(boolean approved, boolean failed, @Nullable String authCode,
            @Nullable String responseCode, @Nullable String message,
            @Nullable byte[] arpc, @Nullable byte[] issuerScript) {
        this(approved, failed, authCode, responseCode, message, arpc, issuerScript, null);
    }

    private AuthResult(boolean approved, boolean failed, @Nullable String authCode,
            @Nullable String responseCode, @Nullable String message,
            @Nullable byte[] arpc, @Nullable byte[] issuerScript, @Nullable String asyncRequestId) {
        this.approved = approved;
        this.failed = failed;
        this.authCode = authCode;
        this.responseCode = responseCode;
        this.message = message;
        this.arpc = arpc;
        this.issuerScript = issuerScript;
        this.asyncRequestId = asyncRequestId;
    }

    public AuthResult withAsyncRequestId(@Nullable String asyncRequestId) {
        return new AuthResult(approved, failed, authCode, responseCode, message, arpc,
                issuerScript, asyncRequestId);
    }

    public static AuthResult approved(String authCode, String responseCode,
            @Nullable byte[] arpc, @Nullable byte[] issuerScript) {
        return new AuthResult(true, false, authCode, responseCode, "Approved", arpc, issuerScript);
    }

    public static AuthResult declined(String responseCode, String message) {
        return new AuthResult(false, false, null, responseCode, message, null, null);
    }

    public static AuthResult failed(String message) {
        return new AuthResult(false, true, null, null, message, null, null);
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isFailed() {
        return failed;
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
    public byte[] getArpc() {
        return arpc;
    }

    @Nullable
    public byte[] getIssuerScript() {
        return issuerScript;
    }

    @Nullable
    public String getAsyncRequestId() {
        return asyncRequestId;
    }
}

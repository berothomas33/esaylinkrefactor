package com.emvenhance.core.host;

import androidx.annotation.Nullable;

public final class AuthResult {

    private final boolean approved;
    private final boolean failed;
    private final boolean notSent;
    private final boolean pinRequired;
    private final String authCode;
    private final String responseCode;
    private final String message;
    @Nullable
    private final byte[] arpc;
    @Nullable
    private final byte[] issuerScript;

    private AuthResult(boolean approved, boolean failed, @Nullable String authCode,
            @Nullable String responseCode, @Nullable String message,
            @Nullable byte[] arpc, @Nullable byte[] issuerScript) {
        this(approved, failed, false, false, authCode, responseCode, message, arpc, issuerScript);
    }

    private AuthResult(boolean approved, boolean failed, boolean notSent, boolean pinRequired,
            @Nullable String authCode, @Nullable String responseCode, @Nullable String message,
            @Nullable byte[] arpc, @Nullable byte[] issuerScript) {
        this.approved = approved;
        this.failed = failed;
        this.notSent = notSent;
        this.pinRequired = pinRequired;
        this.authCode = authCode;
        this.responseCode = responseCode;
        this.message = message;
        this.arpc = arpc;
        this.issuerScript = issuerScript;
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

    public static AuthResult notSent(String message) {
        return new AuthResult(false, true, true, false, null, null, message, null, null);
    }

    /**
     * Mastercard Single Tap: the host wants the PIN for a contactless sale that went online
     * without one. Not approved and not failed — nothing to reverse; the same sale goes again
     * with the PIN (no second tap).
     */
    public static AuthResult pinRequired(String responseCode, String message) {
        return new AuthResult(false, false, false, true, null, responseCode, message, null, null);
    }

    public boolean isApproved() {
        return approved;
    }

    public boolean isFailed() {
        return failed;
    }

    public boolean isNotSent() {
        return notSent;
    }

    public boolean isPinRequired() {
        return pinRequired;
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
}

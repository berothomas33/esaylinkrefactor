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
    /** The host couldn't be reached or the request couldn't complete — see {@link #failed}. */
    private final boolean failed;
    private final String authCode;
    private final String responseCode;
    private final String message;
    /** Issuer Authentication Data (EMV tag 91 / ARPC) — see {@link #getArpc}. */
    @Nullable
    private final byte[] arpc;
    /** Issuer Script Template 1/2 (EMV tags 71/72) — see {@link #getIssuerScript}. */
    @Nullable
    private final byte[] issuerScript;
    /** The host's id for this request — see {@link #getAsyncRequestId}. */
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

    /** Same result, carrying the {@code asyncRequestId} the host answered. */
    public AuthResult withAsyncRequestId(@Nullable String asyncRequestId) {
        return new AuthResult(approved, failed, authCode, responseCode, message, arpc,
                issuerScript, asyncRequestId);
    }

    /**
     * @param arpc EMV tag 91 (Issuer Authentication Data), feeds the 2nd GENERATE AC so the card
     *      can authenticate the issuer — {@code null} skips online card authentication.
     * @param issuerScript EMV tags 71/72 (Issuer Script Template 1/2), post-issuance script data
     *      — {@code null} when the issuer sent none.
     */
    public static AuthResult approved(String authCode, String responseCode,
            @Nullable byte[] arpc, @Nullable byte[] issuerScript) {
        return new AuthResult(true, false, authCode, responseCode, "Approved", arpc, issuerScript);
    }

    /** The host answered and declined. */
    public static AuthResult declined(String responseCode, String message) {
        return new AuthResult(false, false, null, responseCode, message, null, null);
    }

    /**
     * Online failed: the host couldn't be reached, didn't answer, or the request couldn't be built
     * or sent (e.g. a transaction type with no host request yet). Never an approval — this
     * terminal doesn't approve anything offline.
     */
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

    /**
     * The {@code asyncRequestId} sent with the exchange and the sale/refund this result answers:
     * what the acknowledgement API sends back to tell the host the transaction completed
     * (keep it for settlement; without it the host reverses it). {@code null} when no request
     * reached the host.
     */
    @Nullable
    public String getAsyncRequestId() {
        return asyncRequestId;
    }
}

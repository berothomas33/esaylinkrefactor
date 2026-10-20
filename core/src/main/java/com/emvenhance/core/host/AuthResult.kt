package com.emvenhance.core.host

/**
 * Online authorization outcome used by a vendor when finishing an EMV online step.
 *
 * Not injected into `EmvBehavior` — vendors create / receive this where they handle online
 * themselves.
 */
class AuthResult private constructor(
    val isApproved: Boolean,
    /** The host couldn't be reached or the request couldn't complete — see [failed]. */
    val isFailed: Boolean,
    val authCode: String?,
    val responseCode: String?,
    val message: String?,
    /** Issuer Authentication Data (EMV tag 91 / ARPC). */
    val arpc: ByteArray?,
    /** Issuer Script Template 1/2 (EMV tags 71/72). */
    val issuerScript: ByteArray?,
    /**
     * The `asyncRequestId` sent with the exchange and the sale/refund this result answers: what
     * the acknowledgement API sends back to tell the host the transaction completed (keep it for
     * settlement; without it the host reverses it). `null` when no request reached the host.
     */
    val asyncRequestId: String? = null,
) {

    /** Same result, carrying the `asyncRequestId` the host answered. */
    fun withAsyncRequestId(asyncRequestId: String?): AuthResult = AuthResult(
        isApproved, isFailed, authCode, responseCode, message, arpc, issuerScript, asyncRequestId,
    )

    companion object {
        /**
         * @param arpc EMV tag 91 (Issuer Authentication Data), feeds the 2nd GENERATE AC so the
         *      card can authenticate the issuer — `null` skips online card authentication.
         * @param issuerScript EMV tags 71/72 (Issuer Script Template 1/2), post-issuance script
         *      data — `null` when the issuer sent none.
         */
        @JvmStatic
        fun approved(
            authCode: String?,
            responseCode: String?,
            arpc: ByteArray?,
            issuerScript: ByteArray?,
        ): AuthResult = AuthResult(true, false, authCode, responseCode, "Approved", arpc, issuerScript)

        /** The host answered and declined. */
        @JvmStatic
        fun declined(responseCode: String?, message: String?): AuthResult =
            AuthResult(false, false, null, responseCode, message, null, null)

        /**
         * Online failed: the host couldn't be reached, didn't answer, or the request couldn't be
         * built or sent (e.g. a transaction type with no host request yet). Never an approval —
         * this terminal doesn't approve anything offline.
         */
        @JvmStatic
        fun failed(message: String?): AuthResult =
            AuthResult(false, true, null, null, message, null, null)
    }
}

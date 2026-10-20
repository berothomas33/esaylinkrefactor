package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the old project's `EmvPurchaseResponse`. `authCode`/`responseCode` arrive as their own
 * JSON fields; the issuer's EMV tags (ARPC / issuer scripts) come bundled inside `chipData` as a
 * hex BER-TLV blob — see [com.emvenhance.network.tlv.BerTlv] and
 * [com.emvenhance.network.RetrofitCommunicationBehavior] for how they're pulled out. This is the
 * one part of the old project's wire format that isn't fully documented from static analysis
 * alone — the exact set of tags a given issuer bundles into `chipData` may need adjusting once
 * this is exercised against a real response.
 */
class PurchaseResponse {

    @SerializedName("responseCode")
    var responseCode: Int? = null
        private set

    @SerializedName("authCode")
    var authCode: String? = null
        private set

    @SerializedName("responseMessage")
    var responseMessage: String? = null
        private set

    /** Hex-encoded BER-TLV bundling whatever EMV tags the issuer returned (91 / 71 / 72 / ...). */
    @SerializedName("chipData")
    var chipData: String? = null
        private set

    @SerializedName("referenceNumber")
    var referenceNumber: String? = null
        private set
}

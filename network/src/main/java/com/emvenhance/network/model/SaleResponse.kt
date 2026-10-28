package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Decrypted payload of the sale call's response — mirrors the old project's `SaleResponse`,
 * reconstructed from its getters referenced in `SaleActivity` (`fillMomknPayResponse` and the
 * `getResponseCode() == 0` success check; the class itself wasn't shared). Receipt-only fields
 * the old project also read here (merchant name/address, terminal id, ...) are kept since
 * `SaleCommunicationBehavior` passes them straight through — this module doesn't print receipts
 * itself.
 */
class SaleResponse {

    /**
     * `0` on success — anything else is a decline/error, see `SaleCommunicationBehavior`. `null`
     * when the host's answer has none: never read as 0, which means approved.
     */
    @SerializedName("responseCode")
    var responseCode: Int? = null
        private set

    @SerializedName("responseMessage")
    var responseMessage: String? = null
        private set

    @SerializedName("authCode")
    var authCode: String? = null
        private set

    /** Bundles issuer EMV tags (91/71/72) the same way [PurchaseResponse.chipData] does. */
    @SerializedName("chipData")
    var chipData: String? = null
        private set

    @SerializedName("authDate")
    var authDate: String? = null
        private set

    @SerializedName("authTime")
    var authTime: String? = null
        private set

    @SerializedName("nii")
    var nii: String? = null
        private set

    @SerializedName("referenceNumber")
    var referenceNumber: String? = null
        private set

    @SerializedName("batchId")
    var batchId: String? = null
        private set

    @SerializedName("trxRefNumber")
    var trxRefNumber: String? = null
        private set

    @SerializedName("merchantName")
    var merchantName: String? = null
        private set

    @SerializedName("merchantId")
    var merchantId: String? = null
        private set

    @SerializedName("merchantAddressLine1")
    var merchantAddressLine1: String? = null
        private set

    @SerializedName("merchantAddressLine2")
    var merchantAddressLine2: String? = null
        private set

    @SerializedName("maskedPan")
    var maskedPan: String? = null
        private set

    @SerializedName("amount")
    var amount: String? = null
        private set

    @SerializedName("terminalId")
    var terminalId: String? = null
        private set

    @SerializedName("trailer")
    var trailer: String? = null
        private set

    @SerializedName("targetBank")
    var targetBank: String? = null
        private set
}

package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Generic encrypted-envelope request shape — the request-side counterpart to
 * [GeneralResponse], mirrors the old project's `GeneralRequest` as used by
 * `orchestration/exchange` and `orchestration/sale` (see `SaleCommunicationBehavior`).
 *
 * Field set confirmed against the real `GeneralRequest` class, found in an uploaded
 * `model_layer.rar`:
 * - Exchange sends [transactionKeyEncrypted] (the RSA-wrapped TEK), [pinKeyEncrypted] (the
 *   RSA-wrapped PEK, if online PIN was collected), and [transactionType] once, keyed to
 *   [asyncRequestId]; sale doesn't resend any of those three — the host is expected to already
 *   have them from the exchange call for the same [asyncRequestId]. See [forExchange]/[forSale].
 * - Sale instead sends [encPinBlock] (the online PIN block itself, encrypted under the PEK in the
 *   PIN pad's hardware) — `null` for a no-online-PIN transaction.
 */
class GeneralRequest private constructor(
    @field:SerializedName("encSerializedRequest") val encSerializedRequest: String,
    @field:SerializedName("asyncRequestId") val asyncRequestId: String,
    @field:SerializedName("isRetry") val isRetry: Int,
    @field:SerializedName("transactionKey") val transactionKeyEncrypted: String?,
    @field:SerializedName("pinKey") val pinKeyEncrypted: String?,
    @field:SerializedName("encPinBlock") val encPinBlock: String?,
    /** `"PURCHASE"` for a sale — see `SaleCommunicationBehavior#TRANSACTION_TYPE_SALE`. */
    @field:SerializedName("transactionType") val transactionType: String?,
) {
    companion object {
        /** The exchange call — establishes the TEK (and PEK, if online PIN was collected) for `asyncRequestId`. */
        @JvmStatic
        fun forExchange(
            encSerializedRequest: String,
            asyncRequestId: String,
            transactionKeyEncrypted: String,
            pinKeyEncrypted: String?,
            transactionType: String,
        ) = GeneralRequest(
            encSerializedRequest, asyncRequestId, 0, transactionKeyEncrypted, pinKeyEncrypted,
            null, transactionType,
        )

        /** The sale call — reuses the TEK/PEK already established by [forExchange] for `asyncRequestId`. */
        @JvmStatic
        fun forSale(encSerializedRequest: String, asyncRequestId: String, encPinBlock: String?) =
            GeneralRequest(encSerializedRequest, asyncRequestId, 0, null, null, encPinBlock, null)
    }
}

package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Payload carried (AES-encrypted) inside [GeneralRequest.forExchange]'s `encSerializedRequest` —
 * field names confirmed against the real `ExchangeRequest` class (found in an uploaded
 * `model_layer.rar`): its `ExchangeRequest(asyncRequestId, pan, cvm, transactionType)`
 * constructor is exactly the shape `SaleActivity#prepareExchangeRequest` calls. An earlier
 * version of this class used `asyncId`/`type` for these same two fields, guessed before the real
 * source was found — wrong names, now corrected.
 */
class ExchangeRequest(
    @field:SerializedName("asyncRequestId") val asyncRequestId: String,
    @field:SerializedName("pan") val pan: String?,
    /** CVM used for this transaction — see `SaleCommunicationBehavior#cvmCode`. */
    @field:SerializedName("cvm") val cvm: Int,
    /** `"PURCHASE"` for a sale — see `SaleCommunicationBehavior#TRANSACTION_TYPE_SALE`. */
    @field:SerializedName("transactionType") val transactionType: String,
)

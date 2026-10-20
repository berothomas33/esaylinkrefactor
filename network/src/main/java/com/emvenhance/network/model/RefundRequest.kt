package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Payload carried (AES-encrypted) inside `orchestration/refund`'s [GeneralRequest] — same field
 * names as the old project's `model_layer.data.refund.RefundRequest`. Mostly [SaleRequest]'s card
 * fields, plus [referenceNumber] (the original sale's reference number) and `transactionId`, and
 * without `netAmount`/`singleTap`.
 *
 * `transactionId` is sent as `null`: the old refund screen that filled it wasn't shared, so what
 * it should carry (the original sale's `trxRefNumber`?) isn't confirmed. `cardType`/`dcc` carry
 * the same placeholders as [SaleRequest]'s.
 */
class RefundRequest(
    referenceNumber: String,
    amount: Double,
    cvm: Int,
    pan: String?,
    pinBlock: String?,
    chipData: String?,
    expirationMonth: Int,
    expirationYear: Int,
    posEntryMode: String,
    cardType: String?,
    trailer: String,
    dcc: String,
    transactionId: String?,
    extraData: String,
) {
    // Declared one by one, in the wire order the old app's JSON had.
    @SerializedName("referenceNumber")
    val referenceNumber: String = referenceNumber

    /** Major currency units (e.g. `10.50`), like [SaleRequest]'s amount. */
    @SerializedName("amount")
    val amount: Double = Math.round(amount * 100) / 100.0

    @SerializedName("cvm")
    private val cvm: Int = cvm

    @SerializedName("pan")
    private val pan: String? = pan

    @SerializedName("pinBlock")
    private val pinBlock: String? = pinBlock

    @SerializedName("chipData")
    private val chipData: String? = chipData

    @SerializedName("expirationMonth")
    private val expirationMonth: Int = expirationMonth

    @SerializedName("expirationYear")
    private val expirationYear: Int = expirationYear

    @SerializedName("posEntryMode")
    private val posEntryMode: String = posEntryMode

    @SerializedName("cardType")
    private val cardType: String? = cardType

    @SerializedName("trailer")
    private val trailer: String = trailer

    @SerializedName("dcc")
    private val dcc: String = dcc

    @SerializedName("transactionId")
    private val transactionId: String? = transactionId

    @SerializedName("extraData")
    private val extraData: String = extraData
}

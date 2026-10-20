package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Payload carried (AES-encrypted) inside [GeneralRequest.forSale]'s `encSerializedRequest` —
 * field set confirmed against the real `SaleRequest` class (found in an uploaded
 * `model_layer.rar`), which also revealed two things an earlier, reconstructed-from-setter-calls
 * version of this class had wrong:
 * - [amount]/[netAmount] are `double` major-currency-unit values (e.g. `10.50`), not minor-unit
 *   integers — the real class even rounds `amount` to 2 decimal places on the way out. An earlier
 *   version sent `TransactionConfig#amountMinor` (cents) directly as a `long`.
 * - [netAmount] and [singleTap] weren't in the reconstructed version at all.
 *
 * [netAmount] is `0.0`, as in the old app's approved sale. [singleTap] is the Mastercard-CLSS
 * single-tap retry marker (old project: `"ST"`) — out of scope here (see
 * `SaleCommunicationBehavior`'s doc), always `null`.
 *
 * Two fields are still placeholders pending the real per-issuer tables, same disclosure as
 * `RsaPublicKeyEncryptor`'s key-encoding assumption before it was confirmed:
 * - [cardType] — the old project derived this from a `getIssueName(issuerName)` lookup
 *   (issuer/network → numeric or short code) that wasn't shared; this carries the raw issuer name
 *   instead of guessing at that table.
 * - [dcc] — the old project derived this from a `getDCC(issuerName)` lookup (which issuers are
 *   DCC-eligible) that also wasn't shared. Its approved Mastercard sale sent `"true"`, so
 *   Mastercard is `"true"`; every other scheme `"false"`.
 *
 * Confirm both against the server team before relying on this for a live transaction.
 */
class SaleRequest(
    @field:SerializedName("amount") val amount: Double,
    @field:SerializedName("netAmount") val netAmount: Double,
    @field:SerializedName("cvm") val cvm: Int,
    @field:SerializedName("pan") val pan: String?,
    @field:SerializedName("pinBlock") val pinBlock: String?,
    /** Field 55 (ICC System Related Data), hex-encoded BER-TLV — see `TransactionConfig#iccData`. */
    @field:SerializedName("chipData") val chipData: String?,
    @field:SerializedName("expirationMonth") val expirationMonth: Int,
    @field:SerializedName("expirationYear") val expirationYear: Int,
    /** Track 2 discretionary data + separators, past the PAN/expiry — `""` when unavailable. */
    @field:SerializedName("trailer") val trailer: String,
    @field:SerializedName("posEntryMode") val posEntryMode: String,
    /** See the class doc's DCC/cardType caveat — issuer name, not a numeric code. */
    @field:SerializedName("cardType") val cardType: String?,
    @field:SerializedName("dcc") val dcc: String,
    extraData: String,
) {
    /** Mastercard-CLSS single-tap retry marker — see the class doc; always `null` here. */
    @SerializedName("singleTap")
    val singleTap: String? = null

    /** [SaleExtraData], JSON-serialized — see `SaleCommunicationBehavior`. */
    @SerializedName("extraData")
    val extraData: String = extraData
}

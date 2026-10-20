package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Mirrors the old project's `EmvPurchaseRequest` (crypto/purchase), trimmed to the fields this
 * app actually has available at `START_ONLINE_PROCESS` — see `TransactionConfig`. The old
 * project's onboarding/token/report/void/refund/reverse endpoints are out of scope here; this
 * module only covers online purchase authorization.
 */
class PurchaseRequest(
    @field:SerializedName("amount") val amount: Long,
    @field:SerializedName("procCode") val procCode: String,
    @field:SerializedName("posEntryMode") val posEntryMode: String,
    /** Field 55 (ICC System Related Data), hex-encoded BER-TLV — see `TransactionConfig#iccData`. */
    @field:SerializedName("chipData") val chipData: String?,
    @field:SerializedName("pan") val pan: String?,
    /** Online PIN block, hex-encoded — `null` when no online PIN was collected. */
    @field:SerializedName("pinBlock") val pinBlock: String?,
    /**
     * The PIN key that encrypted [pinBlock], RSA-wrapped with the host's public key (base64) —
     * see `RsaPublicKeyEncryptor`. `null` whenever [pinBlock] is.
     */
    @field:SerializedName("pinKey") val pinKeyEncrypted: String?,
)

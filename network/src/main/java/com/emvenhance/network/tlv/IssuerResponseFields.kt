package com.emvenhance.network.tlv

/**
 * Pulls the issuer-authentication tags a host bundles into one `chipData` hex BER-TLV blob —
 * shared by every `CommunicationBehavior` that maps a host response into
 * [com.emvenhance.core.host.AuthResult] (tag 91 → feeds the 2nd GENERATE AC, tags 71/72 →
 * post-issuance script data), so the tag set and concatenation order can't drift between them.
 */
object IssuerResponseFields {

    private const val TAG_ARPC = "91"
    private const val TAG_ISSUER_SCRIPT_1 = "71"
    private const val TAG_ISSUER_SCRIPT_2 = "72"

    /** EMV tag 91 (Issuer Authentication Data / ARPC) — `null` if absent. */
    @JvmStatic
    fun arpc(chipData: String?): ByteArray? = BerTlv.findTag(chipData, TAG_ARPC)

    /** EMV tags 71 + 72 (Issuer Script Template 1/2), concatenated — `null` if neither is present. */
    @JvmStatic
    fun issuerScript(chipData: String?): ByteArray? = concat(
        BerTlv.findTag(chipData, TAG_ISSUER_SCRIPT_1),
        BerTlv.findTag(chipData, TAG_ISSUER_SCRIPT_2),
    )

    private fun concat(a: ByteArray?, b: ByteArray?): ByteArray? = when {
        a == null -> b
        b == null -> a
        else -> a + b
    }
}

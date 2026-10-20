package com.emvenhance.core.card

import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Field 55 (ICC System Related Data) for the host: the tag list, the BER-TLV encoding and the
 * present/missing summary, the same for every vendor. A vendor only reads one tag's value from
 * its kernel (`AbstractEmvBehavior#readIccTag`); [read] does the rest.
 */
class Field55 private constructor(
    private val hex: String,
    private val length: Int,
    private val present: String,
    private val missing: String,
) {

    /** One field 55 tag and its EMV name, for the log. */
    private class Tag(val tag: Int, val name: String)

    /** Hex-encoded BER-TLV, as sent in `chipData`; empty when no tag was present. */
    fun hex(): String = hex

    val isEmpty: Boolean get() = length == 0

    /**
     * The log/trace text: every tag sent with its name and value, the tags the kernel had no
     * value for, and the hex.
     */
    fun describe(source: String?): String =
        "FIELD 55 (" + (if (source != null) "$source, " else "") + length + " bytes)" + present +
            "\nmissing: " + missing +
            "\nhex: " + hex

    companion object {

        /**
         * Field 55 tags, in order: the old app's list (`PaxUtils` → EasyLink
         * `TransRequest#setTagList`, sent as `chipData`), exactly as it sent it. The old app's
         * field 55 for a Mastercard offline-PIN chip sale, which the host approved, ends with
         * 9F09, 9F33, 9F34, 9F35 and 9F41 a second time, so they are sent twice here too. The
         * card's cryptogram data (9F26, 9F27, 9F10, 9F36, 95, 9F37, 82) is what the host needs
         * to validate the ARQC and compute the ARPC (tag 91). 9F66 (TTQ) only exists for
         * Visa-type kernels; Mastercard has none.
         */
        private val TAGS = arrayOf(
            Tag(0x5F2A, "Transaction Currency Code"),
            Tag(0x5F34, "PAN Sequence Number"),
            Tag(0x82, "Application Interchange Profile"),
            Tag(0x84, "Dedicated File Name (AID)"),
            Tag(0x95, "Terminal Verification Results"),
            Tag(0x9A, "Transaction Date"),
            Tag(0x9C, "Transaction Type"),
            Tag(0x9F02, "Amount, Authorized"),
            Tag(0x9F03, "Amount, Other"),
            Tag(0x9F09, "Application Version Number"),
            Tag(0x9F10, "Issuer Application Data"),
            Tag(0x9F21, "Transaction Time"),
            Tag(0x9F1A, "Terminal Country Code"),
            Tag(0x9F26, "Application Cryptogram"),
            Tag(0x9F27, "Cryptogram Information Data"),
            Tag(0x9F33, "Terminal Capabilities"),
            Tag(0x9F34, "CVM Results"),
            Tag(0x9F35, "Terminal Type"),
            Tag(0x9F36, "Application Transaction Counter"),
            Tag(0x9F37, "Unpredictable Number"),
            Tag(0x9F41, "Transaction Sequence Counter"),
            Tag(0x9F42, "Application Currency Code"),
            Tag(0x9F53, "Transaction Category Code"),
            Tag(0x9F1E, "Interface Device Serial Number"),
            Tag(0x9F63, "Scheme-proprietary 9F63"),
            Tag(0x9F66, "Terminal Transaction Qualifiers"),
            Tag(0x9F09, "Application Version Number"),
            Tag(0x9F33, "Terminal Capabilities"),
            Tag(0x9F34, "CVM Results"),
            Tag(0x9F35, "Terminal Type"),
            Tag(0x9F41, "Transaction Sequence Counter"),
//            Tag(0x9F40, "Additional Terminal Capabilities"),
//            Tag(0x9F1B, "Terminal Floor Limit"),
//            Tag(0x9F15, "Merchant Category Code"),
//            Tag(0x9F1C, "Terminal Identification"),
//            Tag(0x9F49, "Default DDOL"),
//            Tag(0x97, "Default TDOL"),
        )

        /**
         * Reads every field 55 tag through `reader` (value bytes only; `null` or empty means the
         * kernel has no value) and encodes the ones present as BER-TLV. A missing tag is left
         * out, not padded.
         */
        @JvmStatic
        fun read(reader: (Int) -> ByteArray?): Field55 {
            val out = ByteArrayOutputStream()
            val present = StringBuilder()
            val missing = StringBuilder()
            for (t in TAGS) {
                val value = reader(t.tag)
                val tagHex = Integer.toHexString(t.tag).uppercase(Locale.US)
                if (value == null || value.isEmpty()) {
                    missing.append(if (missing.isEmpty()) "" else " ").append(tagHex)
                    continue
                }
                writeTlv(out, t.tag, value)
                present.append('\n').append(tagHex).append(' ').append(t.name)
                    .append(" = ").append(toHex(value))
            }
            val field55 = out.toByteArray()
            return Field55(
                toHex(field55), field55.size, present.toString(),
                if (missing.isEmpty()) "none" else missing.toString(),
            )
        }

        /** Appends one BER-TLV entry (tag + length + value). */
        private fun writeTlv(out: ByteArrayOutputStream, tag: Int, value: ByteArray) {
            val tagBytes = tagToBytes(tag)
            out.write(tagBytes, 0, tagBytes.size)
            writeBerLength(out, value.size)
            out.write(value, 0, value.size)
        }

        /**
         * BER-TLV tag bytes: one for a plain tag (`0x9A`), two for a `0x9F`/`0x5F` tag
         * (`0x9F02`), three for a `0xDF`-class tag (`0xDF8120`).
         */
        private fun tagToBytes(tag: Int): ByteArray = when {
            tag <= 0xFF -> byteArrayOf(tag.toByte())
            tag <= 0xFFFF -> byteArrayOf((tag shr 8).toByte(), tag.toByte())
            else -> byteArrayOf((tag shr 16).toByte(), (tag shr 8).toByte(), tag.toByte())
        }

        /** BER-TLV length: short form under 128 bytes, one length-of-length byte above it. */
        private fun writeBerLength(out: ByteArrayOutputStream, length: Int) {
            if (length < 0x80) {
                out.write(length)
            } else if (length <= 0xFF) {
                out.write(0x81)
                out.write(length)
            } else {
                out.write(0x82)
                out.write((length shr 8) and 0xFF)
                out.write(length and 0xFF)
            }
        }

        private fun toHex(bytes: ByteArray): String {
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format(Locale.US, "%02X", b.toInt() and 0xFF))
            }
            return sb.toString()
        }
    }
}

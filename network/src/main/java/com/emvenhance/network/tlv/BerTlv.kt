package com.emvenhance.network.tlv

/**
 * Minimal BER-TLV reader for pulling individual EMV tag values out of a hex-encoded TLV blob —
 * e.g. the issuer response's `chipData` field, which (matching the old project's behavior)
 * bundles whatever EMV tags the issuer returned (91 / 71 / 72 / 8A / 89 ...) into one string
 * instead of exposing them as separate JSON fields.
 *
 * No general BER-TLV utility existed anywhere in this codebase (PAX's own kernel handles TLV
 * natively in C, never exposed at this layer), so this is a small, deliberately narrow reader:
 * two-byte tag numbers only (91, 71, 72, 8A, 89 are all single-byte tags, but this stays correct
 * for two-byte tags too, e.g. 9F02), one-byte definite-form length (values here are always well
 * under 128 bytes) — not the general multi-byte-length BER-TLV case.
 */
object BerTlv {

    /**
     * Finds `tag` (as a hex string, e.g. `"91"` or `"9F26"`) inside a hex-encoded BER-TLV blob
     * and returns its value bytes. `null` if the tag isn't present or the blob is malformed.
     */
    @JvmStatic
    fun findTag(hexBlob: String?, tag: String): ByteArray? {
        if (hexBlob.isNullOrEmpty()) {
            return null
        }
        val blob = hexToBytes(hexBlob) ?: return null
        val wantTag = hexToBytes(tag) ?: return null
        var i = 0
        while (i < blob.size) {
            val tagStart = i
            val tagLen = if ((blob[i].toInt() and 0x1F) == 0x1F) 2 else 1
            if (i + tagLen > blob.size) {
                return null
            }
            i += tagLen
            if (i >= blob.size) {
                return null
            }
            val valueLen = blob[i].toInt() and 0xFF
            i += 1
            if (valueLen > 0x7F) {
                // Multi-byte length — not needed for the tags this blob carries; bail rather
                // than risk misreading the rest of the buffer.
                return null
            }
            if (i + valueLen > blob.size) {
                return null
            }
            if (regionMatches(blob, tagStart, wantTag)) {
                return blob.copyOfRange(i, i + valueLen)
            }
            i += valueLen
        }
        return null
    }

    private fun regionMatches(blob: ByteArray, offset: Int, tag: ByteArray): Boolean {
        if (offset + tag.size > blob.size) {
            return false
        }
        for (j in tag.indices) {
            if (blob[offset + j] != tag[j]) {
                return false
            }
        }
        return true
    }

    private fun hexToBytes(hex: String): ByteArray? {
        val len = hex.length
        if (len % 2 != 0) {
            return null
        }
        val result = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val hi = Character.digit(hex[i], 16)
            val lo = Character.digit(hex[i + 1], 16)
            if (hi < 0 || lo < 0) {
                return null
            }
            result[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return result
    }
}

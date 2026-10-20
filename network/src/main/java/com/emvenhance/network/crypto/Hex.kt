package com.emvenhance.network.crypto

/**
 * Uppercase hex encode/decode — the wire format `orchestration/exchange`'s
 * `encSerializedRequest`/`pinKey`/`transactionKey` fields actually use, confirmed against a real
 * captured request. [AesEnvelopeCrypto]/[RsaPublicKeyEncryptor] both used base64 for these
 * before that was known — base64 output is still valid Cipher/Mac output, so nothing failed
 * loudly; the host's HSM just couldn't make sense of it.
 */
internal object Hex {

    private val DIGITS = "0123456789ABCDEF".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = DIGITS[v ushr 4]
            out[i * 2 + 1] = DIGITS[v and 0x0F]
        }
        return String(out)
    }

    fun decode(hex: String): ByteArray {
        val len = hex.length
        val out = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            val hi = Character.digit(hex[i], 16)
            val lo = Character.digit(hex[i + 1], 16)
            out[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return out
    }
}

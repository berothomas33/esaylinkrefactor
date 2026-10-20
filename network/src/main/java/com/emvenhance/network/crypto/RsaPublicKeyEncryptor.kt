package com.emvenhance.network.crypto

import android.util.Base64
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import javax.crypto.Cipher

/**
 * RSA-encrypts a symmetric session key with the host's public key — mirrors the old project's
 * `Encryptor#encryptByPublicKeyRSA`.
 *
 * RSA here transports a session key, never bulk payload data: the terminal generates a random
 * symmetric key locally, uses it directly wherever it's needed (e.g. loaded straight into the PIN
 * pad's secure hardware — see `PaxEmvBehavior#provisionOnlinePinKey` — to encrypt a PIN block),
 * and sends this RSA-wrapped copy of the same key so the host can recover it and decrypt/verify
 * independently. The old project reused this same pattern for a second key (its "TEK") to
 * encrypt whole request/response bodies — that's wired up too, in `SaleCommunicationBehavior`.
 *
 * The old project's `Encryptor` class wasn't shared, so this was reconstructed from usage, not a
 * verified server response — status per field:
 * - **Padding — confirmed.** `RSA/ECB/PKCS1Padding`, as implemented below.
 * - **Key encoding — confirmed, and different from the first assumption.** A real captured
 *   onboarding public key decodes to a bare PKCS#1 `RSAPublicKey` DER structure
 *   (`SEQUENCE { modulus INTEGER, publicExponent INTEGER }`, 4096-bit, exponent 65537) — **not**
 *   an X.509 `SubjectPublicKeyInfo` (no `rsaEncryption` OID/AlgorithmIdentifier wrapper at all).
 *   [java.security.spec.X509EncodedKeySpec] — the first guess here — expects that wrapper and
 *   threw `InvalidKeySpecException` on this real key; [parsePkcs1RsaPublicKey] parses the bare
 *   PKCS#1 structure directly instead, since the JDK has no built-in `KeySpec` for it.
 * - **Output encoding — confirmed, and different from the first assumption.** A real captured
 *   `orchestration/exchange` request's `pinKey`/`transactionKey` fields are uppercase **hex**,
 *   not base64 — each decodes to exactly 512 bytes, the ciphertext length a 4096-bit RSA key
 *   always produces. Base64 (the first guess, hence this method's old name `encryptToBase64`) is
 *   still valid `Cipher` output, so nothing failed loudly at encrypt time — the host's HSM just
 *   couldn't make sense of it, producing an opaque `"HSM command error"` 500 rather than a clear
 *   encoding error.
 */
object RsaPublicKeyEncryptor {

    private const val KEY_ALGORITHM = "RSA"
    private const val CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_INTEGER = 0x02

    /**
     * @param base64PublicKey the host's RSA public key, base64-encoded PKCS#1 `RSAPublicKey` DER
     * @param plainKeyBytes the symmetric session key to wrap
     * @return the RSA-encrypted key, hex-encoded (uppercase)
     */
    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encryptToHex(base64PublicKey: String, plainKeyBytes: ByteArray): String {
        val publicKeyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
        val publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
            .generatePublic(parsePkcs1RsaPublicKey(publicKeyBytes))
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return Hex.encode(cipher.doFinal(plainKeyBytes))
    }

    /**
     * Reads a PKCS#1 `RSAPublicKey` DER structure — two DER `INTEGER`s inside one `SEQUENCE`,
     * nothing more — see this class's doc for how that was confirmed against a real key.
     * Deliberately narrow (definite-form lengths only, up to 2-byte long form — plenty for a
     * 4096-bit modulus) rather than a general ASN.1/DER parser, same scope discipline as this
     * module's `BerTlv` reader.
     */
    @Throws(InvalidKeyException::class)
    private fun parsePkcs1RsaPublicKey(der: ByteArray): RSAPublicKeySpec {
        try {
            val offset = intArrayOf(0)
            readTag(der, offset, TAG_SEQUENCE)
            readLength(der, offset)
            readTag(der, offset, TAG_INTEGER)
            val modulus = readInteger(der, offset)
            readTag(der, offset, TAG_INTEGER)
            val exponent = readInteger(der, offset)
            return RSAPublicKeySpec(modulus, exponent)
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw InvalidKeyException("Malformed PKCS#1 RSAPublicKey DER", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidKeyException("Malformed PKCS#1 RSAPublicKey DER", e)
        }
    }

    private fun readInteger(der: ByteArray, offset: IntArray): BigInteger {
        val length = readLength(der, offset)
        val value = BigInteger(1, der.copyOfRange(offset[0], offset[0] + length))
        offset[0] += length
        return value
    }

    private fun readTag(der: ByteArray, offset: IntArray, expectedTag: Int) {
        val tag = der[offset[0]++].toInt() and 0xFF
        require(tag == expectedTag) {
            "Expected DER tag 0x" + Integer.toHexString(expectedTag) + ", got 0x" + Integer.toHexString(tag)
        }
    }

    private fun readLength(der: ByteArray, offset: IntArray): Int {
        val first = der[offset[0]++].toInt() and 0xFF
        if ((first and 0x80) == 0) {
            return first
        }
        val numBytes = first and 0x7F
        var length = 0
        for (i in 0 until numBytes) {
            length = (length shl 8) or (der[offset[0]++].toInt() and 0xFF)
        }
        return length
    }
}

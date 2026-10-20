package com.emvenhance.network.crypto

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-encrypts/decrypts a request/response body with a locally-generated transaction key (TEK) —
 * mirrors the old project's `EncryptorHelper#encryptByAESKey`/`#decryptByAESKey`, the software
 * counterpart to `RsaPublicKeyEncryptor` (which transports the same key to the host) — see
 * `SaleCommunicationBehavior` for where both come together.
 *
 * The old project's `EncryptorHelper` wasn't shared, so the exact scheme here is a
 * reconstruction — status per field:
 * - **Key length**: 16 bytes (AES-128) — `TekGenerator` generates this length.
 * - **Mode/padding**: AES/CBC/PKCS5Padding.
 * - **IV**: random per call, prepended to the ciphertext, rather than a fixed IV, so the same
 *   plaintext never produces the same ciphertext twice — still unconfirmed by itself, but the
 *   overall `IV || ciphertext` byte layout is confirmed (see next point).
 * - **Envelope encoding — confirmed, and different from the first assumption.** A real captured
 *   `orchestration/exchange` request's `encSerializedRequest` is uppercase **hex**, not base64 —
 *   decodes to exactly `16 + N` bytes with `N` a multiple of 16, matching
 *   `IV || AES/CBC/PKCS5Padding ciphertext` exactly. Base64 was the first guess here (and in
 *   `RsaPublicKeyEncryptor`) — still valid `Cipher` output, so nothing failed loudly at encrypt
 *   time, but the host's HSM couldn't make sense of it, producing an opaque `"HSM command error"`
 *   500 rather than a clear encoding error.
 */
object AesEnvelopeCrypto {

    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val KEY_ALGORITHM = "AES"
    private const val IV_LENGTH_BYTES = 16

    /** @return `hex(IV || ciphertext)` of `plainText`, UTF-8 encoded before encryption. */
    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun encrypt(key: ByteArray, plainText: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), IvParameterSpec(iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Hex.encode(iv + cipherText)
    }

    /** Inverse of [encrypt] — expects `hex(IV || ciphertext)`. */
    @JvmStatic
    @Throws(GeneralSecurityException::class)
    fun decrypt(key: ByteArray, hexEnvelopeCipherText: String): String {
        val combined = Hex.decode(hexEnvelopeCipherText)
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), IvParameterSpec(iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}

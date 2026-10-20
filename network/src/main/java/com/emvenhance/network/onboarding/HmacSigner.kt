package com.emvenhance.network.onboarding

import android.util.Base64
import java.security.GeneralSecurityException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 sign/verify for the offline onboarding handshake — mirrors the old project's
 * `Encryptor.generateHmacSHA256` / `Encryptor.isSignatureVerifiedWithHmac`, as used by
 * `ConfigurationActivity`:
 * - step 1's response is authenticated by HMAC-verifying `publicKey + challenge + serviceAccount`
 *   against the response's `signature`, keyed by the SACS the handshake step returned;
 * - step 3's request body is `HMAC-SHA256(challenge, SACS)` — despite the request field being
 *   named `challenge`, it carries this signature, not the raw challenge.
 *
 * Key/message bytes are UTF-8, digest output is base64 (not hex — confirmed against a real
 * captured onboarding run: computing this exact HMAC over that run's real
 * publicKey/challenge/serviceAccount/SACS values and base64-encoding it reproduces that run's
 * real `signature` field byte-for-byte; a hex encoding of the same digest does not).
 */
internal object HmacSigner {

    private const val ALGORITHM = "HmacSHA256"

    fun sign(message: String, key: String): String {
        try {
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), ALGORITHM))
            val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(digest, Base64.NO_WRAP)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("HMAC-SHA256 unavailable", e)
        }
    }

    fun verify(message: String, key: String, expectedSignatureBase64: String?): Boolean {
        if (expectedSignatureBase64 == null) {
            return false
        }
        return constantTimeEquals(sign(message, key), expectedSignatureBase64)
    }

    /**
     * Signature comparison over untrusted server input — not a timing-sensitive secret compare,
     * but matching constant-time practice costs nothing here.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}

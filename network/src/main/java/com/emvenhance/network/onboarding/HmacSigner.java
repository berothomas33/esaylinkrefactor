package com.emvenhance.network.onboarding;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import androidx.annotation.Nullable;

/**
 * HMAC-SHA256 sign/verify for the offline onboarding handshake — mirrors the old project's
 * {@code Encryptor.generateHmacSHA256} / {@code Encryptor.isSignatureVerifiedWithHmac}, as used
 * by {@code ConfigurationActivity}:
 * <ul>
 *   <li>step 1's response is authenticated by HMAC-verifying
 *       {@code publicKey + challenge + serviceAccount} against the response's {@code signature},
 *       keyed by the SACS the handshake step returned;
 *   <li>step 3's request body is {@code HMAC-SHA256(challenge, SACS)} — despite the request
 *       field being named {@code challenge}, it carries this signature, not the raw challenge.
 * </ul>
 *
 * <p>Key/message bytes are UTF-8, digest output is base64 (not hex — confirmed against a real
 * captured onboarding run: computing this exact HMAC over that run's real
 * publicKey/challenge/serviceAccount/SACS values and base64-encoding it reproduces that run's
 * real {@code signature} field byte-for-byte; a hex encoding of the same digest does not).
 */
final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    static String sign(String message, String key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static boolean verify(String message, String key, @Nullable String expectedSignatureBase64) {
        if (expectedSignatureBase64 == null) {
            return false;
        }
        return constantTimeEquals(sign(message, key), expectedSignatureBase64);
    }

    /** Signature comparison over untrusted server input — not a timing-sensitive secret compare,
     * but matching constant-time practice costs nothing here. */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}

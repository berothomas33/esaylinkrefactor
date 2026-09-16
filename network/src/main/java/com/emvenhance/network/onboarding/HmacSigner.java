package com.emvenhance.network.onboarding;

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
 * <p>{@code ConfigurationActivity} was shared without its {@code Encryptor} class, so the exact
 * key/message byte encoding here (UTF-8 for both, hex for the digest) is reconstructed from the
 * call sites, not verified against {@code Encryptor} itself — confirm this against a real
 * handshake response before relying on it for a live onboarding run.
 */
final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSigner() {
    }

    static String sign(String message, String key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return toHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    static boolean verify(String message, String key, @Nullable String expectedSignatureHex) {
        if (expectedSignatureHex == null) {
            return false;
        }
        return constantTimeEquals(sign(message, key), expectedSignatureHex);
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

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

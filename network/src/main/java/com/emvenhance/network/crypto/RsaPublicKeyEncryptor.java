package com.emvenhance.network.crypto;

import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * RSA-encrypts a symmetric session key with the host's public key — mirrors the old project's
 * {@code Encryptor#encryptByPublicKeyRSA}.
 *
 * <p>RSA here transports a session key, never bulk payload data: the terminal generates a random
 * symmetric key locally, uses it directly wherever it's needed (e.g. loaded straight into the PIN
 * pad's secure hardware — see {@code PaxEmvBehavior#provisionOnlinePinKey} — to encrypt a PIN
 * block), and sends this RSA-wrapped copy of the same key so the host can recover it and
 * decrypt/verify independently. The old project reused this same pattern for a second key (its
 * "TEK") to encrypt whole request/response bodies — that's wired up too, in
 * {@code SaleCommunicationBehavior}.
 *
 * <p>The old project's {@code Encryptor} class wasn't shared, so this was reconstructed from
 * usage, not a verified server response — status per field, confirmed with the server team:
 * <ul>
 *   <li><b>Padding — confirmed.</b> {@code RSA/ECB/PKCS1Padding}, as implemented below.
 *   <li><b>Key encoding — working assumption, pending confirmation.</b> Used as base64 X.509
 *       {@code SubjectPublicKeyInfo} DER (the standard Java RSA public-key interchange format,
 *       and what {@code OnboardingResult#getPublicKey} is expected to carry) until the server
 *       team confirms it one way or the other — keep this as-is unless/until that changes.
 * </ul>
 */
public final class RsaPublicKeyEncryptor {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private RsaPublicKeyEncryptor() {
    }

    /**
     * @param base64PublicKey the host's RSA public key, base64-encoded X.509 DER
     * @param plainKeyBytes the symmetric session key to wrap
     * @return the RSA-encrypted key, base64-encoded
     */
    public static String encryptToBase64(String base64PublicKey, byte[] plainKeyBytes)
            throws GeneralSecurityException {
        byte[] publicKeyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP);
        PublicKey publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(publicKeyBytes));

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(plainKeyBytes);

        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
}

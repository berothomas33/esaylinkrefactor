package com.emvenhance.network.crypto;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-encrypts/decrypts a request/response body with a locally-generated transaction key (TEK) —
 * mirrors the old project's {@code EncryptorHelper#encryptByAESKey}/{@code #decryptByAESKey},
 * the software counterpart to {@code RsaPublicKeyEncryptor} (which transports the same key to the
 * host) — see {@code SaleCommunicationBehavior} for where both come together.
 *
 * <p>The old project's {@code EncryptorHelper} wasn't shared, so the exact scheme here is a
 * reconstruction, not a verified match — flagged the same way {@code RsaPublicKeyEncryptor}'s
 * encoding/padding assumptions are:
 * <ul>
 *   <li><b>Key length</b>: 16 bytes (AES-128) — {@code TekGenerator} generates this length.
 *   <li><b>Mode/padding</b>: AES/CBC/PKCS5Padding.
 *   <li><b>IV</b>: random per call, prepended to the ciphertext ({@code base64(IV || ciphertext)})
 *       rather than a fixed IV, so the same plaintext never produces the same ciphertext twice.
 * </ul>
 * Confirm these against the server team before relying on this for a live transaction, same as
 * the public-key encoding.
 */
public final class AesEnvelopeCrypto {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 16;

    private AesEnvelopeCrypto() {
    }

    /** @return {@code base64(IV || ciphertext)} of {@code plainText}, UTF-8 encoded before encryption. */
    public static String encrypt(byte[] key, String plainText) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return Base64.encodeToString(combined, Base64.NO_WRAP);
    }

    /** Inverse of {@link #encrypt} — expects {@code base64(IV || ciphertext)}. */
    public static String decrypt(byte[] key, String base64EnvelopeCipherText) throws GeneralSecurityException {
        byte[] combined = Base64.decode(base64EnvelopeCipherText, Base64.NO_WRAP);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}

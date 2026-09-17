package com.emvenhance.network.crypto;

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
 * reconstruction — status per field:
 * <ul>
 *   <li><b>Key length</b>: 16 bytes (AES-128) — {@code TekGenerator} generates this length.
 *   <li><b>Mode/padding</b>: AES/CBC/PKCS5Padding.
 *   <li><b>IV</b>: random per call, prepended to the ciphertext, rather than a fixed IV, so the
 *       same plaintext never produces the same ciphertext twice — still unconfirmed by itself,
 *       but the overall {@code IV || ciphertext} byte layout is confirmed (see next point).
 *   <li><b>Envelope encoding — confirmed, and different from the first assumption.</b> A real
 *       captured {@code orchestration/exchange} request's {@code encSerializedRequest} is
 *       uppercase <b>hex</b>, not base64 — decodes to exactly {@code 16 + N} bytes with
 *       {@code N} a multiple of 16, matching {@code IV || AES/CBC/PKCS5Padding ciphertext}
 *       exactly. Base64 was the first guess here (and in {@code RsaPublicKeyEncryptor}) — still
 *       valid {@code Cipher} output, so nothing failed loudly at encrypt time, but the host's HSM
 *       couldn't make sense of it, producing an opaque {@code "HSM command error"} 500 rather
 *       than a clear encoding error.
 * </ul>
 */
public final class AesEnvelopeCrypto {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int IV_LENGTH_BYTES = 16;

    private AesEnvelopeCrypto() {
    }

    /** @return {@code hex(IV || ciphertext)} of {@code plainText}, UTF-8 encoded before encryption. */
    public static String encrypt(byte[] key, String plainText) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
        return Hex.encode(combined);
    }

    /** Inverse of {@link #encrypt} — expects {@code hex(IV || ciphertext)}. */
    public static String decrypt(byte[] key, String hexEnvelopeCipherText) throws GeneralSecurityException {
        byte[] combined = Hex.decode(hexEnvelopeCipherText);
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] cipherText = new byte[combined.length - IV_LENGTH_BYTES];
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.length);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, KEY_ALGORITHM), new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}

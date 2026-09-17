package com.emvenhance.network.crypto;

import android.util.Base64;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

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
 * usage, not a verified server response — status per field:
 * <ul>
 *   <li><b>Padding — confirmed.</b> {@code RSA/ECB/PKCS1Padding}, as implemented below.
 *   <li><b>Key encoding — confirmed, and different from the first assumption.</b> A real captured
 *       onboarding public key decodes to a bare PKCS#1 {@code RSAPublicKey} DER structure
 *       ({@code SEQUENCE { modulus INTEGER, publicExponent INTEGER }}, 4096-bit, exponent 65537)
 *       — <b>not</b> an X.509 {@code SubjectPublicKeyInfo} (no {@code rsaEncryption} OID/
 *       AlgorithmIdentifier wrapper at all). {@link java.security.spec.X509EncodedKeySpec} — the
 *       first guess here — expects that wrapper and threw {@code InvalidKeySpecException} on this
 *       real key; {@link #parsePkcs1RsaPublicKey} parses the bare PKCS#1 structure directly
 *       instead, since the JDK has no built-in {@code KeySpec} for it.
 * </ul>
 */
public final class RsaPublicKeyEncryptor {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int TAG_SEQUENCE = 0x30;
    private static final int TAG_INTEGER = 0x02;

    private RsaPublicKeyEncryptor() {
    }

    /**
     * @param base64PublicKey the host's RSA public key, base64-encoded PKCS#1 {@code RSAPublicKey} DER
     * @param plainKeyBytes the symmetric session key to wrap
     * @return the RSA-encrypted key, base64-encoded
     */
    public static String encryptToBase64(String base64PublicKey, byte[] plainKeyBytes)
            throws GeneralSecurityException {
        byte[] publicKeyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP);
        PublicKey publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(parsePkcs1RsaPublicKey(publicKeyBytes));

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(plainKeyBytes);

        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    /**
     * Reads a PKCS#1 {@code RSAPublicKey} DER structure — two DER {@code INTEGER}s inside one
     * {@code SEQUENCE}, nothing more — see this class's javadoc for how that was confirmed against
     * a real key. Deliberately narrow (definite-form lengths only, up to 2-byte long form — plenty
     * for a 4096-bit modulus) rather than a general ASN.1/DER parser, same scope discipline as
     * this module's {@code BerTlv} reader.
     */
    private static RSAPublicKeySpec parsePkcs1RsaPublicKey(byte[] der) throws InvalidKeyException {
        try {
            int[] offset = {0};
            readTag(der, offset, TAG_SEQUENCE);
            readLength(der, offset);
            readTag(der, offset, TAG_INTEGER);
            BigInteger modulus = readInteger(der, offset);
            readTag(der, offset, TAG_INTEGER);
            BigInteger exponent = readInteger(der, offset);
            return new RSAPublicKeySpec(modulus, exponent);
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException e) {
            throw new InvalidKeyException("Malformed PKCS#1 RSAPublicKey DER", e);
        }
    }

    private static BigInteger readInteger(byte[] der, int[] offset) {
        int length = readLength(der, offset);
        BigInteger value = new BigInteger(1, Arrays.copyOfRange(der, offset[0], offset[0] + length));
        offset[0] += length;
        return value;
    }

    private static void readTag(byte[] der, int[] offset, int expectedTag) {
        int tag = der[offset[0]++] & 0xFF;
        if (tag != expectedTag) {
            throw new IllegalArgumentException(
                    "Expected DER tag 0x" + Integer.toHexString(expectedTag) + ", got 0x" + Integer.toHexString(tag));
        }
    }

    private static int readLength(byte[] der, int[] offset) {
        int first = der[offset[0]++] & 0xFF;
        if ((first & 0x80) == 0) {
            return first;
        }
        int numBytes = first & 0x7F;
        int length = 0;
        for (int i = 0; i < numBytes; i++) {
            length = (length << 8) | (der[offset[0]++] & 0xFF);
        }
        return length;
    }
}

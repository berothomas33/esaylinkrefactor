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

public final class RsaPublicKeyEncryptor {

    private static final String KEY_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int TAG_SEQUENCE = 0x30;
    private static final int TAG_INTEGER = 0x02;

    private RsaPublicKeyEncryptor() {
    }

    public static String encryptToHex(String base64PublicKey, byte[] plainKeyBytes)
            throws GeneralSecurityException {
        byte[] publicKeyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP);
        PublicKey publicKey = KeyFactory.getInstance(KEY_ALGORITHM)
                .generatePublic(parsePkcs1RsaPublicKey(publicKeyBytes));

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(plainKeyBytes);

        return Hex.encode(encrypted);
    }

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

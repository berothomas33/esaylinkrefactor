package com.emvenhance.network.crypto;

/**
 * Uppercase hex encode/decode — the wire format {@code orchestration/exchange}'s
 * {@code encSerializedRequest}/{@code pinKey}/{@code transactionKey} fields actually use,
 * confirmed against a real captured request. {@link AesEnvelopeCrypto}/{@link RsaPublicKeyEncryptor}
 * both used base64 for these before that was known — base64 output is still valid Cipher/Mac
 * output, so nothing failed loudly; the host's HSM just couldn't make sense of it.
 */
final class Hex {

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    private Hex() {
    }

    static String encode(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = DIGITS[v >>> 4];
            out[i * 2 + 1] = DIGITS[v & 0x0F];
        }
        return new String(out);
    }

    static byte[] decode(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}

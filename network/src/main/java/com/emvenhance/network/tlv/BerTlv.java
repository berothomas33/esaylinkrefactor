package com.emvenhance.network.tlv;

import androidx.annotation.Nullable;

/**
 * Minimal BER-TLV reader for pulling individual EMV tag values out of a hex-encoded TLV blob —
 * e.g. the issuer response's {@code chipData} field, which (matching the old project's behavior)
 * bundles whatever EMV tags the issuer returned (91 / 71 / 72 / 8A / 89 ...) into one string
 * instead of exposing them as separate JSON fields.
 *
 * <p>No general BER-TLV utility existed anywhere in this codebase (PAX's own kernel handles TLV
 * natively in C, never exposed at this layer), so this is a small, deliberately narrow reader:
 * two-byte tag numbers only (91, 71, 72, 8A, 89 are all single-byte tags, but this stays correct
 * for two-byte tags too, e.g. 9F02), one-byte definite-form length (values here are always well
 * under 128 bytes) — not the general multi-byte-length BER-TLV case.
 */
public final class BerTlv {

    private BerTlv() {
    }

    /**
     * Finds {@code tag} (as a hex string, e.g. {@code "91"} or {@code "9F26"}) inside a
     * hex-encoded BER-TLV blob and returns its value, hex-encoded. {@code null} if the tag isn't
     * present or the blob is malformed.
     */
    @Nullable
    public static byte[] findTag(@Nullable String hexBlob, String tag) {
        if (hexBlob == null || hexBlob.isEmpty()) {
            return null;
        }
        byte[] blob = hexToBytes(hexBlob);
        byte[] wantTag = hexToBytes(tag);
        if (blob == null || wantTag == null) {
            return null;
        }

        int i = 0;
        while (i < blob.length) {
            int tagStart = i;
            int tagLen = (blob[i] & 0x1F) == 0x1F ? 2 : 1;
            if (i + tagLen > blob.length) {
                return null;
            }
            i += tagLen;
            if (i >= blob.length) {
                return null;
            }
            int valueLen = blob[i] & 0xFF;
            i += 1;
            if (valueLen > 0x7F) {
                // Multi-byte length — not needed for the tags this blob carries; bail rather
                // than risk misreading the rest of the buffer.
                return null;
            }
            if (i + valueLen > blob.length) {
                return null;
            }
            if (regionMatches(blob, tagStart, wantTag)) {
                byte[] value = new byte[valueLen];
                System.arraycopy(blob, i, value, 0, valueLen);
                return value;
            }
            i += valueLen;
        }
        return null;
    }

    private static boolean regionMatches(byte[] blob, int offset, byte[] tag) {
        if (offset + tag.length > blob.length) {
            return false;
        }
        for (int j = 0; j < tag.length; j++) {
            if (blob[offset + j] != tag[j]) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if (len % 2 != 0) {
            return null;
        }
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            result[i / 2] = (byte) ((hi << 4) + lo);
        }
        return result;
    }
}

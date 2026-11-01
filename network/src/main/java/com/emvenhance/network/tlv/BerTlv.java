package com.emvenhance.network.tlv;

import androidx.annotation.Nullable;

public final class BerTlv {

    private BerTlv() {
    }

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

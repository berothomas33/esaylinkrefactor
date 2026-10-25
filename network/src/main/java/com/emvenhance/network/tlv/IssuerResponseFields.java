package com.emvenhance.network.tlv;

import androidx.annotation.Nullable;

public final class IssuerResponseFields {

    private static final String TAG_ARPC = "91";
    private static final String TAG_ISSUER_SCRIPT_1 = "71";
    private static final String TAG_ISSUER_SCRIPT_2 = "72";

    private IssuerResponseFields() {
    }

    @Nullable
    public static byte[] arpc(@Nullable String chipData) {
        return BerTlv.findTag(chipData, TAG_ARPC);
    }

    @Nullable
    public static byte[] issuerScript(@Nullable String chipData) {
        return concat(BerTlv.findTag(chipData, TAG_ISSUER_SCRIPT_1),
                BerTlv.findTag(chipData, TAG_ISSUER_SCRIPT_2));
    }

    @Nullable
    private static byte[] concat(@Nullable byte[] a, @Nullable byte[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}

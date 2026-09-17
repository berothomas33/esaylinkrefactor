package com.emvenhance.network.tlv;

import androidx.annotation.Nullable;

/**
 * Pulls the issuer-authentication tags a host bundles into one {@code chipData} hex BER-TLV blob
 * — shared by every {@code CommunicationBehavior} that maps a host response into
 * {@link com.emvenhance.core.host.AuthResult} (tag 91 → feeds the 2nd GENERATE AC, tags 71/72 →
 * post-issuance script data), so the tag set and concatenation order can't drift between them.
 */
public final class IssuerResponseFields {

    private static final String TAG_ARPC = "91";
    private static final String TAG_ISSUER_SCRIPT_1 = "71";
    private static final String TAG_ISSUER_SCRIPT_2 = "72";

    private IssuerResponseFields() {
    }

    /** EMV tag 91 (Issuer Authentication Data / ARPC) — {@code null} if absent. */
    @Nullable
    public static byte[] arpc(@Nullable String chipData) {
        return BerTlv.findTag(chipData, TAG_ARPC);
    }

    /** EMV tags 71 + 72 (Issuer Script Template 1/2), concatenated — {@code null} if neither is present. */
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

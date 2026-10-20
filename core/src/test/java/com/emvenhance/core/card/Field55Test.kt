package com.emvenhance.core.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The tag list's order, BER-TLV encoding and the present/missing summary. */
public class Field55Test {

    @Test
    public void presentTagsAreEncodedInListOrderAndMissingOnesLeftOut() {
        Field55 field55 = Field55.read(tag -> {
            switch (tag) {
                case 0x9F26:
                    return new byte[] {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88};
                case 0x5F2A:
                    return new byte[] {0x08, 0x18};
                case 0x82:
                    return new byte[] {0x39, 0x00};
                case 0x95:
                    return new byte[0];
                default:
                    return null;
            }
        });

        assertEquals("5F2A020818" + "82023900" + "9F26081122334455667788", field55.hex());
        String summary = field55.describe("contact");
        assertTrue(summary.startsWith("FIELD 55 (contact, 20 bytes)"));
        assertTrue(summary.contains("\n9F26 Application Cryptogram = 1122334455667788"));
        assertTrue(summary.contains("\nmissing: 5F34 84 95 "));
    }

    /** The old app's field 55 for a Mastercard offline-PIN chip sale, which the host approved. */
    private static final String OLD_APP_MASTERCARD_OFFLINE_PIN =
            "5F2A0208185F340103820230008407A0000000041010950500000080009A032610139C0100"
            + "9F02060000000120009F03060000000000009F090200029F10120110A5000F04000000000000"
            + "0000000000FF9F21031237209F1A0208189F2608A8E613452342E9A59F2701809F3303E0F0C8"
            + "9F34034103029F3501229F360200279F37045D8B42529F4104000000009F420209789F1E0831"
            + "383530323234379F090200029F3303E0F0C89F34034103029F3501229F410400000000";

    @Test
    public void mastercardOfflinePinMatchesTheOldAppByteForByte() {
        Field55 field55 = Field55.read(tag -> {
            switch (tag) {
                case 0x5F2A: return hex("0818");
                case 0x5F34: return hex("03");
                case 0x82: return hex("3000");
                case 0x84: return hex("A0000000041010");
                case 0x95: return hex("0000008000");
                case 0x9A: return hex("261013");
                case 0x9C: return hex("00");
                case 0x9F02: return hex("000000012000");
                case 0x9F03: return hex("000000000000");
                case 0x9F09: return hex("0002");
                case 0x9F10: return hex("0110A5000F040000000000000000000000FF");
                case 0x9F21: return hex("123720");
                case 0x9F1A: return hex("0818");
                case 0x9F26: return hex("A8E613452342E9A5");
                case 0x9F27: return hex("80");
                case 0x9F33: return hex("E0F0C8");
                case 0x9F34: return hex("410302");
                case 0x9F35: return hex("22");
                case 0x9F36: return hex("0027");
                case 0x9F37: return hex("5D8B4252");
                case 0x9F41: return hex("00000000");
                case 0x9F42: return hex("0978");
                case 0x9F1E: return hex("3138353032323437");
                default: return null; // 9F53, 9F63, 9F66: none for a Mastercard chip card
            }
        });

        assertEquals(OLD_APP_MASTERCARD_OFFLINE_PIN, field55.hex());
    }

    private static byte[] hex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    @Test
    public void longValueUsesTheTwoByteLengthForm() {
        byte[] iad = new byte[130];
        Field55 field55 = Field55.read(tag -> tag == 0x9F10 ? iad : null);

        assertTrue(field55.hex().startsWith("9F108182"));
    }

    @Test
    public void noTagsMeansEmpty() {
        Field55 field55 = Field55.read(tag -> null);

        assertTrue(field55.isEmpty());
        assertEquals("", field55.hex());
    }
}

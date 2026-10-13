package com.emvenhance.core.card;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
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

    /**
     * A Mastercard offline-PIN chip sale the host approved: field 55 is that sale's chipData, tag
     * for tag, even though the contact kernel also has values for the terminal tags the host
     * declined with (9F40, 9F1B, 9F15, 9F1C, 9F49, 97).
     */
    @Test
    public void mastercardContactSaleSendsTheTagsTheHostApproved() {
        Map<Integer, String> kernel = new HashMap<>();
        kernel.put(0x5F2A, "0818");
        kernel.put(0x5F34, "03");
        kernel.put(0x82, "3000");
        kernel.put(0x84, "A0000000041010");
        kernel.put(0x95, "0000008000");
        kernel.put(0x9A, "261013");
        kernel.put(0x9C, "00");
        kernel.put(0x9F02, "000000012000");
        kernel.put(0x9F03, "000000000000");
        kernel.put(0x9F09, "0002");
        kernel.put(0x9F10, "0110A5000F040000000000000000000000FF");
        kernel.put(0x9F21, "123720");
        kernel.put(0x9F1A, "0818");
        kernel.put(0x9F26, "A8E613452342E9A5");
        kernel.put(0x9F27, "80");
        kernel.put(0x9F33, "E0F0C8");
        kernel.put(0x9F34, "410302");
        kernel.put(0x9F35, "22");
        kernel.put(0x9F36, "0027");
        kernel.put(0x9F37, "5D8B4252");
        kernel.put(0x9F41, "00000000");
        kernel.put(0x9F42, "0978");
        kernel.put(0x9F1E, "3138353032323437");
        // Terminal tags the kernel also holds — never sent.
        kernel.put(0x9F40, "6000F0A001");
        kernel.put(0x9F1B, "00000000");
        kernel.put(0x9F15, "5999");
        kernel.put(0x9F1C, "3132333435363738");
        kernel.put(0x9F49, "9F3704");
        kernel.put(0x97, "9F0206");

        Field55 field55 = Field55.read(tag -> {
            String value = kernel.get(tag);
            return value == null ? null : hexToBytes(value);
        });

        assertEquals("5F2A020818" + "5F340103" + "82023000" + "8407A0000000041010"
                + "95050000008000" + "9A03261013" + "9C0100" + "9F0206000000012000"
                + "9F0306000000000000" + "9F09020002"
                + "9F10120110A5000F040000000000000000000000FF" + "9F2103123720" + "9F1A020818"
                + "9F2608A8E613452342E9A5" + "9F270180" + "9F3303E0F0C8" + "9F3403410302"
                + "9F350122" + "9F36020027" + "9F37045D8B4252" + "9F410400000000"
                + "9F42020978" + "9F1E083138353032323437", field55.hex());
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
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

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

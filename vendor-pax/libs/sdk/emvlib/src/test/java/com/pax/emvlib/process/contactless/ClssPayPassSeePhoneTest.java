package com.pax.emvlib.process.contactless;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The card's own See Phone signal: AAC + POS Cardholder Interaction Information (DF4B). */
public class ClssPayPassSeePhoneTest {

    @Test
    public void aacWithPciiAskingForPhoneVerificationIsSeePhone() {
        // From a real tap: GENERATE AC → 9F27 = 00 (AAC), DF4B = 000100.
        assertTrue(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x01, 0x00}));
        assertTrue(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x02, 0x00}));
        assertTrue(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x00, 0x08}));
    }

    @Test
    public void cryptogramOtherThanAacIsNotSeePhone() {
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x80, new byte[] {0x00, 0x01, 0x00})); // ARQC
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x40, new byte[] {0x00, 0x01, 0x00})); // TC
    }

    @Test
    public void pciiWithoutThoseBitsIsAPlainDecline() {
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x00, 0x00}));
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x10, 0x00})); // outside 00030F
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x00, 0x10}));
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x00, null));
        assertFalse(ClssPayPassProcess.isSeePhone((byte) 0x00, new byte[] {0x00, 0x01}));
    }
}

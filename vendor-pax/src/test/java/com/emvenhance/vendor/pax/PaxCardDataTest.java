package com.emvenhance.vendor.pax;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** The card's data as text ({@link PaxCardData}), on public test-card numbers. */
public class PaxCardDataTest {

    @Test
    public void panFromTrack2StopsAtTheSeparator() {
        assertEquals("4761739001010010",
                PaxCardData.panFromTrack2("4761739001010010=25122011143804400000"));
        assertEquals("tag 57 writes D", "4761739001010010",
                PaxCardData.panFromTrack2("4761739001010010D25122011143804400000"));
    }

    @Test
    public void panFromTrack2WithNoSeparatorIsTheWholeTrack() {
        assertEquals("4761739001010010", PaxCardData.panFromTrack2("4761739001010010"));
    }

    @Test
    public void panFromTagReadsTheBcd() {
        byte[] pan = {0x47, 0x61, 0x73, (byte) 0x90, 0x01, 0x01, 0x00, 0x10};
        assertEquals("4761739001010010", PaxCardData.panFromTag(pan));
    }

    @Test
    public void panFromTagDropsTheFPadding() {
        byte[] pan = {0x37, 0x42, 0x45, 0x45, 0x54, 0x00, 0x12, 0x6F}; // 15 digits + F
        assertEquals("374245455400126", PaxCardData.panFromTag(pan));
    }

    @Test
    public void panFromNoTagIsEmpty() {
        assertEquals("", PaxCardData.panFromTag(null));
        assertEquals("", PaxCardData.panFromTag(new byte[0]));
    }

    @Test
    public void maskPanShowsTheFirstSixAndTheLastFour() {
        assertEquals("476173******0010", PaxCardData.maskPan("4761739001010010"));
        assertEquals("374245*****0126", PaxCardData.maskPan("374245455400126"));
    }

    @Test
    public void maskPanLeavesAShortOrMissingPanAsItIs() {
        assertEquals("4761739001", PaxCardData.maskPan("4761739001"));
        assertNull(PaxCardData.maskPan(null));
    }

    @Test
    public void asciiTextIsTrimmed() {
        assertEquals("MTIP06 MCD 25A",
                PaxCardData.asciiText("MTIP06 MCD 25A  ".getBytes(StandardCharsets.US_ASCII)));
    }
}

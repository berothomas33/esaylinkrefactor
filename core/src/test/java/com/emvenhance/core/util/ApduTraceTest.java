package com.emvenhance.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

public class ApduTraceTest {

    private ApduTrace trace;

    @Before
    public void setUp() {
        trace = new ApduTrace(true);
        trace.beginTransaction();
    }

    @Test
    public void beginTransactionClearsThePreviousTransaction() {
        trace.note("ICC", "first transaction");
        trace.beginTransaction();

        assertFalse(trace.text().contains("first transaction"));
        assertTrue(trace.text().startsWith("===== EMV TRANSACTION ====="));
    }

    @Test
    public void flagsEachEmvLevelOnceWhenItChanges() {
        command("00A40400", ascii("2PAY.SYS.DDF01"), 256);
        response("9000", "6F00");
        command("00A40400", hex("A0000000041010"), 256);
        response("9000", "6F00");
        command("80A80000", hex("8300"), 256);
        response("9000", "7700");

        String text = trace.text();
        assertEquals(1, count(text, "=== EMV LEVEL: APPLICATION SELECTION ==="));
        assertEquals(1, count(text, "=== EMV LEVEL: INITIATE APPLICATION PROCESSING ==="));
        assertTrue(text.contains("PICC >> SELECT PPSE 2PAY.SYS.DDF01"));
        assertTrue(text.contains("PICC >> SELECT AID A0000000041010"));
        assertTrue(text.contains("PICC >> GET PROCESSING OPTIONS (GPO)"));
    }

    @Test
    public void readRecordBeforeGpoIsApplicationSelectionAfterGpoIsReadData() {
        command("00B2010C", new byte[0], 256);
        response("9000", "7000");
        command("80A80000", hex("8300"), 256);
        response("9000", "7700");
        command("00B20114", new byte[0], 256);
        response("9000", "7000");

        String text = trace.text();
        assertTrue(text.contains("READ RECORD SFI 1 REC 1 (directory)"));
        assertTrue(text.contains("=== EMV LEVEL: READ APPLICATION DATA ==="));
        assertTrue(text.contains("READ RECORD SFI 2 REC 1"));
    }

    @Test
    public void recordsOnlyTheLcBytesOfTheKernelsFixedBuffer() {
        byte[] buffer = new byte[512];
        System.arraycopy(hex("8300"), 0, buffer, 0, 2);
        buffer[100] = 0x55; // garbage beyond Lc must not appear

        trace.command("ICC", hex("80A80000"), 2, buffer, 256);

        assertTrue(trace.text().contains("C-APDU: 80A80000 02 8300 00"));
        assertFalse(trace.text().contains("55"));
    }

    @Test
    public void neverRecordsTheOfflinePinBlock() {
        trace.command("ICC", hex("00200080"), 8, hex("241234FFFFFFFFFF"), 0);

        assertTrue(trace.text().contains("VERIFY (plaintext offline PIN)"));
        assertTrue(trace.text().contains("<PIN block not logged>"));
        assertFalse(trace.text().contains("241234"));
    }

    @Test
    public void decodesStatusWordsAndCryptogramTypes() {
        trace.command("ICC", hex("00200080"), 8, hex("241234FFFFFFFFFF"), 0);
        response("63C2", "");
        command("80AE8000", hex("0000000010000000"), 256);
        response("9000", "77299F2701809F360200019F2608AABBCCDDEEFF0011");
        command("80AE4000", hex("3030"), 256);
        response("9000", "8012400001");

        String text = trace.text();
        assertTrue(text.contains("SW=63C2 wrong PIN, 2 tries left"));
        assertTrue(text.contains("=== EMV LEVEL: TERMINAL/CARD ACTION ANALYSIS (1st GENERATE AC) ==="));
        assertTrue(text.contains("CID=80 ARQC (go online)"));
        assertTrue(text.contains("=== EMV LEVEL: COMPLETION (2nd GENERATE AC) ==="));
        assertTrue(text.contains("CID=40 TC (approved offline)"));
    }

    @Test
    public void recordsNothingWhileDisabled() {
        AtomicInteger changes = new AtomicInteger();
        trace.changes().skip(1).subscribe(v -> changes.incrementAndGet());
        String before = trace.text();

        trace.setEnabled(false);
        trace.note("ICC", "ignored");
        command("00A40400", ascii("1PAY.SYS.DDF01"), 256);
        trace.beginTransaction();

        assertFalse(trace.isEnabled());
        assertEquals(before, trace.text());
        assertEquals(0, changes.get());
    }

    @Test
    public void releaseBuildNeverRecordsEvenWhenSwitchedOn() {
        ApduTrace release = new ApduTrace(false);
        release.setEnabled(true);
        release.beginTransaction();
        release.note("ICC", "card data");

        assertFalse(release.isEnabled());
        assertEquals("", release.text());
    }

    @Test
    public void emitsAChangeForEveryRecordedLine() {
        AtomicInteger changes = new AtomicInteger();
        trace.changes().skip(1).subscribe(v -> changes.incrementAndGet());

        trace.note("PICC", "one");
        trace.transactionEvent("two");

        assertEquals(2, changes.get());
    }

    @Test
    public void wrapsEveryLineToThePrinterWidth() {
        command("00A40400", ascii("2PAY.SYS.DDF01"), 256);
        response("9000", "6F1A840E325041592E5359532E4444463031A5088801015F2D02656E");

        List<String> lines = trace.wrapped(32);

        assertFalse(lines.isEmpty());
        for (String line : lines) {
            assertTrue("too wide: " + line, line.length() <= 32);
        }
    }

    @Test
    public void stopsGrowingAtTheLineCap() {
        for (int i = 0; i < 6000; i++) {
            trace.note("ICC", "line " + i);
        }

        String text = trace.text();
        assertTrue(text.contains("trace truncated"));
        assertFalse(text.contains("line 5999"));
    }

    @Test
    public void separateInstancesDoNotShareState() {
        ApduTrace other = new ApduTrace(true);
        other.beginTransaction();

        trace.note("ICC", "only in the first");

        assertFalse(other.text().contains("only in the first"));
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private void command(String header, byte[] data, int le) {
        trace.command("PICC", hex(header), data.length, data, le);
    }

    private void response(String sw, String data) {
        byte[] s = hex(sw);
        byte[] d = hex(data);
        trace.response("PICC", s[0], s[1], d, d.length);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] hex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}

package com.emvenhance.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApduTraceTest {

    private lateinit var trace: ApduTrace

    @Before
    fun setUp() {
        trace = ApduTrace(true)
        trace.beginTransaction()
    }

    @Test
    fun beginTransactionClearsThePreviousTransaction() {
        trace.note("ICC", "first transaction")
        trace.beginTransaction()

        assertFalse(trace.text().contains("first transaction"))
        assertTrue(trace.text().startsWith("===== EMV TRANSACTION ====="))
    }

    @Test
    fun flagsEachEmvLevelOnceWhenItChanges() {
        command("00A40400", ascii("2PAY.SYS.DDF01"), 256)
        response("9000", "6F00")
        command("00A40400", hex("A0000000041010"), 256)
        response("9000", "6F00")
        command("80A80000", hex("8300"), 256)
        response("9000", "7700")

        val text = trace.text()
        assertEquals(1, count(text, "=== EMV LEVEL: APPLICATION SELECTION ==="))
        assertEquals(1, count(text, "=== EMV LEVEL: INITIATE APPLICATION PROCESSING ==="))
        assertTrue(text.contains("PICC >> SELECT PPSE 2PAY.SYS.DDF01"))
        assertTrue(text.contains("PICC >> SELECT AID A0000000041010"))
        assertTrue(text.contains("PICC >> GET PROCESSING OPTIONS (GPO)"))
    }

    @Test
    fun readRecordBeforeGpoIsApplicationSelectionAfterGpoIsReadData() {
        command("00B2010C", ByteArray(0), 256)
        response("9000", "7000")
        command("80A80000", hex("8300"), 256)
        response("9000", "7700")
        command("00B20114", ByteArray(0), 256)
        response("9000", "7000")

        val text = trace.text()
        assertTrue(text.contains("READ RECORD SFI 1 REC 1 (directory)"))
        assertTrue(text.contains("=== EMV LEVEL: READ APPLICATION DATA ==="))
        assertTrue(text.contains("READ RECORD SFI 2 REC 1"))
    }

    @Test
    fun recordsOnlyTheLcBytesOfTheKernelsFixedBuffer() {
        val buffer = ByteArray(512)
        System.arraycopy(hex("8300"), 0, buffer, 0, 2)
        buffer[100] = 0x55 // garbage beyond Lc must not appear

        trace.command("ICC", hex("80A80000"), 2, buffer, 256)

        // Only the APDU line: the header's clock and the elapsed times can contain "55" too.
        assertEquals("C-APDU: 80A80000 02 8300 00", lineWith(trace.text(), "C-APDU:"))
    }

    @Test
    fun neverRecordsTheOfflinePinBlock() {
        trace.command("ICC", hex("00200080"), 8, hex("241234FFFFFFFFFF"), 0)

        assertTrue(trace.text().contains("VERIFY (plaintext offline PIN)"))
        assertTrue(trace.text().contains("<PIN block not logged>"))
        assertFalse(trace.text().contains("241234"))
    }

    @Test
    fun decodesStatusWordsAndCryptogramTypes() {
        trace.command("ICC", hex("00200080"), 8, hex("241234FFFFFFFFFF"), 0)
        response("63C2", "")
        command("80AE8000", hex("0000000010000000"), 256)
        response("9000", "77299F2701809F360200019F2608AABBCCDDEEFF0011")
        command("80AE4000", hex("3030"), 256)
        response("9000", "8012400001")

        val text = trace.text()
        assertTrue(text.contains("SW=63C2 wrong PIN, 2 tries left"))
        assertTrue(text.contains("=== EMV LEVEL: TERMINAL/CARD ACTION ANALYSIS (1st GENERATE AC) ==="))
        assertTrue(text.contains("CID=80 ARQC (go online)"))
        assertTrue(text.contains("=== EMV LEVEL: ISSUER AUTHENTICATION (2nd GENERATE AC) ==="))
        assertTrue(text.contains("CID=40 TC (approved offline)"))
    }

    @Test
    fun recordsNothingWhileDisabled() {
        val version = trace.changes.value
        val before = trace.text()

        trace.setEnabled(false)
        trace.note("ICC", "ignored")
        command("00A40400", ascii("1PAY.SYS.DDF01"), 256)
        trace.beginTransaction()

        assertFalse(trace.isEnabled())
        assertEquals(before, trace.text())
        assertEquals("no change", version, trace.changes.value)
    }

    @Test
    fun releaseBuildNeverRecordsEvenWhenSwitchedOn() {
        val release = ApduTrace(false)
        release.setEnabled(true)
        release.beginTransaction()
        release.note("ICC", "card data")

        assertFalse(release.isEnabled())
        assertEquals("", release.text())
    }

    @Test
    fun emitsAChangeForEveryRecordedLine() {
        val version = trace.changes.value

        trace.note("PICC", "one")
        trace.transactionEvent("two")

        assertEquals(version + 2, trace.changes.value)
    }

    @Test
    fun wrapsEveryLineToThePrinterWidth() {
        command("00A40400", ascii("2PAY.SYS.DDF01"), 256)
        response("9000", "6F1A840E325041592E5359532E4444463031A5088801015F2D02656E")

        val lines = trace.wrapped(32)

        assertFalse(lines.isEmpty())
        for (line in lines) {
            assertTrue("too wide: $line", line.length <= 32)
        }
    }

    @Test
    fun stopsGrowingAtTheLineCap() {
        for (i in 0 until 6000) {
            trace.note("ICC", "line $i")
        }

        val text = trace.text()
        assertTrue(text.contains("trace truncated"))
        assertFalse(text.contains("line 5999"))
    }

    @Test
    fun separateInstancesDoNotShareState() {
        val other = ApduTrace(true)
        other.beginTransaction()

        trace.note("ICC", "only in the first")

        assertFalse(other.text().contains("only in the first"))
    }

    @Test
    fun aRetryStartsTheCardSessionOverButKeepsTheTrace() {
        command("80A80000", hex("8300"), 256)
        response("9000", "7700")
        command("80AE8000", hex("0000000010000000"), 256) // 1st GAC, then the tap was too short
        response("6F00", "")

        trace.transactionEvent("RETRY #1")
        trace.beginAttempt()
        command("00B2010C", ByteArray(0), 256) // directory, before this attempt's GPO
        response("9000", "7000")
        command("80AE8000", hex("0000000010000000"), 256) // this attempt's 1st GAC
        response("9000", "77299F2701809F360200019F2608AABBCCDDEEFF0011")

        val text = trace.text()
        assertEquals(2, count(text, "1st GENERATE AC request ARQC"))
        assertFalse(text.contains("2nd GENERATE AC"))
        assertTrue(text.contains("READ RECORD SFI 1 REC 1 (directory)"))
        assertEquals(2, count(text, "=== EMV LEVEL: TERMINAL/CARD ACTION ANALYSIS (1st GENERATE AC) ==="))
        assertTrue("the first attempt stays in the trace", text.indexOf("RETRY #1") > 0)
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private fun command(header: String, data: ByteArray, le: Int) {
        trace.command("PICC", hex(header), data.size, data, le)
    }

    private fun response(sw: String, data: String) {
        val s = hex(sw)
        val d = hex(data)
        trace.response("PICC", s[0], s[1], d, d.size)
    }

    private companion object {
        fun ascii(s: String): ByteArray = s.toByteArray(Charsets.US_ASCII)

        fun hex(s: String): ByteArray =
            ByteArray(s.length / 2) { i -> s.substring(2 * i, 2 * i + 2).toInt(16).toByte() }

        fun count(text: String, needle: String): Int {
            var n = 0
            var i = text.indexOf(needle)
            while (i >= 0) {
                n++
                i = text.indexOf(needle, i + 1)
            }
            return n
        }

        fun lineWith(text: String, marker: String): String =
            text.split("\n").firstOrNull { it.contains(marker) }?.trim()
                ?: throw AssertionError("no line with $marker in:\n$text")
    }
}

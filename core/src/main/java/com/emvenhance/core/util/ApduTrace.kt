package com.emvenhance.core.util

import android.util.Log
import com.emvenhance.core.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Per-transaction APDU trace: every command the terminal sends to the card and every response,
 * each flagged with the EMV level it belongs to, plus the kernel's own step changes.
 *
 * Owned by `EmvEngine` (one instance, `EmvEngine#apduTrace()`): the engine starts it fresh in
 * `begin()` and records every step it publishes; the vendor device layer (PAX: `EmvDeviceImpl`,
 * handed this instance at transaction start) records the APDUs; the UI reads it through
 * `PosTerminal#apduTrace()` to show, copy and print. This class only records and decodes — it
 * holds no transaction logic of its own.
 *
 * Every line also goes to Logcat under the [TAG] tag.
 *
 * Debug builds only, like [EmvLog]: the trace holds card data (PAN, track 2 in READ RECORD
 * responses), so [isEnabled] is always `false` in a release build. The VERIFY command's data
 * (the offline PIN block) is never recorded.
 */
class ApduTrace internal constructor(
    /** Recording is possible at all — `BuildConfig.DEBUG`: never in a release build. */
    private val allowed: Boolean,
) {

    constructor() : this(BuildConfig.DEBUG)

    @Volatile
    private var enabled = true

    private val lines = ArrayList<String>()
    private val _changes = MutableStateFlow(0L)
    private var version = 0L

    private var startMillis = System.currentTimeMillis()
    private var apduCount = 0
    private var gpoSent = false
    private var generateAcCount = 0
    private var currentLevel: String? = null
    private var lastCommandLevel: String? = null

    /**
     * The trace's version: goes up by one for every recorded line. Collect it to redraw; read
     * the content with [text]. A `StateFlow`: a slow collector skips versions and sees the
     * latest, which is all a redraw needs.
     */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    /** Runtime on/off (the UI checkbox). Has no effect in a release build. */
    fun setEnabled(on: Boolean) {
        enabled = on
    }

    fun isEnabled(): Boolean = allowed && enabled

    /** Starts a fresh trace for a new transaction. */
    fun beginTransaction() {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            lines.clear()
            startMillis = System.currentTimeMillis()
            apduCount = 0
            gpoSent = false
            generateAcCount = 0
            currentLevel = null
            lastCommandLevel = null
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            add("===== EMV TRANSACTION =====")
            add(now)
        }
    }

    /**
     * A new attempt in the same transaction (a retry: tap again, See Phone, fallback, try another
     * interface): the trace carries on, but the card session starts over — its next GENERATE AC
     * is a 1st one again, and a READ RECORD before its GPO is application selection again.
     */
    fun beginAttempt() {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            gpoSent = false
            generateAcCount = 0
            currentLevel = null
            lastCommandLevel = null
        }
    }

    /** A kernel/engine EMV step change, e.g. "5. Read application data". */
    fun kernelStep(step: String) {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            add(elapsed() + "### KERNEL STEP " + step)
        }
    }

    /** A transaction-level event (card detected, online, approved, error...). */
    fun transactionEvent(event: String) {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            add(elapsed() + "--- " + event)
        }
    }

    /** Free-form note, e.g. a reader error. */
    fun note(channel: String, text: String) {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            add(elapsed() + "!!! " + channel + " " + text)
        }
    }

    /** Card answer-to-reset after power-on. */
    fun atr(channel: String, atr: ByteArray?) {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            add(elapsed() + channel + " ATR " + (if (atr == null) "(none)" else hex(atr, 0, atr.size)))
        }
    }

    /**
     * An outgoing C-APDU. `dataIn` may be a larger fixed buffer (PAX's is 512 bytes); only its
     * first `lc` bytes are sent and recorded.
     */
    fun command(channel: String, header: ByteArray?, lc: Int, dataIn: ByteArray?, le: Int) {
        if (!isEnabled() || header == null || header.size < 4) {
            return
        }
        synchronized(this) {
            val cla = header[0].toInt() and 0xFF
            val ins = header[1].toInt() and 0xFF
            val p1 = header[2].toInt() and 0xFF
            val p2 = header[3].toInt() and 0xFF
            val dataLen = if (dataIn == null) 0 else maxOf(0, minOf(lc, dataIn.size))
            val data = if (dataIn == null) ByteArray(0) else dataIn.copyOf(dataLen)

            val decoded = decode(cla, ins, p1, p2, data)
            val level = decoded[0]
            val name = decoded[1]
            if (level != currentLevel) {
                currentLevel = level
                add("")
                add("=== EMV LEVEL: $level ===")
            }
            lastCommandLevel = level
            apduCount++

            add(String.format(Locale.US, "%s#%02d %s >> %s", elapsed(), apduCount, channel, name))
            val raw = StringBuilder(hex(header, 0, 4))
            if (dataLen > 0) {
                raw.append(' ').append(String.format("%02X", dataLen)).append(' ')
                raw.append(if (ins == 0x20) "<PIN block not logged>" else hex(data, 0, dataLen))
            }
            if (le > 0) {
                // ISO 7816-4: Le of 256 is encoded as 00.
                raw.append(' ').append(String.format("%02X", le and 0xFF))
            }
            add("    C-APDU: $raw")
        }
    }

    /** The R-APDU for the last [command]. */
    fun response(channel: String, sw1: Byte, sw2: Byte, dataOut: ByteArray?, length: Int) {
        if (!isEnabled()) {
            return
        }
        synchronized(this) {
            val len = if (dataOut == null) 0 else maxOf(0, minOf(length, dataOut.size))
            val sw = ((sw1.toInt() and 0xFF) shl 8) or (sw2.toInt() and 0xFF)
            val head = StringBuilder(
                String.format(
                    Locale.US, "#%02d %s << SW=%04X %s, %d bytes",
                    apduCount, channel, sw, describeSw(sw), len,
                ),
            )
            val lastLevel = lastCommandLevel
            if (dataOut != null && len > 0 && lastLevel != null && lastLevel.contains("GENERATE AC")) {
                val cid = cryptogramType(dataOut, len)
                if (cid != null) {
                    head.append(", ").append(cid)
                }
            }
            add(head.toString())
            if (dataOut != null && len > 0) {
                add("    R-APDU: " + hex(dataOut, 0, len))
            }
        }
    }

    /** The whole trace, one entry per line. */
    fun text(): String = synchronized(this) { lines.joinToString("\n") }

    fun isEmpty(): Boolean = synchronized(this) { lines.isEmpty() }

    /** The trace wrapped to `width` characters, for a receipt printer. */
    fun wrapped(width: Int): List<String> {
        val out = ArrayList<String>()
        synchronized(this) {
            for (line in lines) {
                if (line.length <= width) {
                    out.add(line)
                    continue
                }
                out.add(line.substring(0, width))
                var i = width
                while (i < line.length) {
                    out.add("    " + line.substring(i, minOf(line.length, i + width - 4)))
                    i += width - 4
                }
            }
        }
        return out
    }

    // ─── internals (callers hold this trace's lock) ─────────────────────────

    private fun add(line: String) {
        Log.d(TAG, line)
        if (lines.size >= MAX_LINES) {
            if (lines.size == MAX_LINES) {
                lines.add("... trace truncated at $MAX_LINES lines ...")
                _changes.value = ++version
            }
            return
        }
        lines.add(line)
        _changes.value = ++version
    }

    /** Seconds since the transaction started, as a line prefix. */
    private fun elapsed(): String {
        val ms = System.currentTimeMillis() - startMillis
        return String.format(Locale.US, "[%d.%03d] ", ms / 1000, ms % 1000)
    }

    /** Returns {EMV level, command name} for a C-APDU. */
    private fun decode(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray): Array<String> {
        val scriptCla = (cla and 0xF0) == 0x80 && (cla and 0x0C) != 0 // 84 / 8C: secure messaging
        when (ins) {
            0xA4 -> {
                if (data.contentEquals(PSE)) {
                    return of("APPLICATION SELECTION", "SELECT PSE 1PAY.SYS.DDF01")
                }
                if (data.contentEquals(PPSE)) {
                    return of("APPLICATION SELECTION", "SELECT PPSE 2PAY.SYS.DDF01")
                }
                val next = if (p2 == 0x02) " (next occurrence)" else ""
                return of("APPLICATION SELECTION", "SELECT AID " + hex(data, 0, data.size) + next)
            }
            0xA8 -> {
                gpoSent = true
                return of("INITIATE APPLICATION PROCESSING", "GET PROCESSING OPTIONS (GPO)")
            }
            0xB2 -> {
                val name = String.format(Locale.US, "READ RECORD SFI %d REC %d", p2 shr 3, p1)
                return if (gpoSent) {
                    of("READ APPLICATION DATA", name)
                } else {
                    of("APPLICATION SELECTION", "$name (directory)")
                }
            }
            0x88 -> return of("OFFLINE DATA AUTHENTICATION", "INTERNAL AUTHENTICATE (DDA)")
            0xCA, 0xCB -> {
                val tag = (p1 shl 8) or p2
                val name = String.format(Locale.US, "GET DATA %04X", tag)
                return when (tag) {
                    0x9F17 -> of("CARDHOLDER VERIFICATION", "$name (PIN try counter)")
                    0x9F36 -> of("TERMINAL RISK MANAGEMENT", "$name (ATC)")
                    0x9F13 -> of("TERMINAL RISK MANAGEMENT", "$name (last online ATC)")
                    0x9F4F -> of("TRANSACTION LOG", "$name (log format)")
                    else -> of(currentLevel ?: "GET DATA", name)
                }
            }
            0x84 -> return of("CARDHOLDER VERIFICATION", "GET CHALLENGE (enciphered offline PIN)")
            0x20 -> return of(
                "CARDHOLDER VERIFICATION",
                if (p2 == 0x88) "VERIFY (enciphered offline PIN)" else "VERIFY (plaintext offline PIN)",
            )
            0xAE -> {
                generateAcCount++
                val type = when (p1 and 0xC0) {
                    0x80 -> "ARQC"
                    0x40 -> "TC"
                    else -> "AAC"
                }
                val cda = if ((p1 and 0x10) != 0) " +CDA" else ""
                return if (generateAcCount == 1) {
                    of(
                        "TERMINAL/CARD ACTION ANALYSIS (1st GENERATE AC)",
                        "1st GENERATE AC request $type$cda",
                    )
                } else {
                    // Sent inside EMVCompleteTrans, the ISSUER_AUTHENTICATION step.
                    of("ISSUER AUTHENTICATION (2nd GENERATE AC)", "2nd GENERATE AC request $type$cda")
                }
            }
            0x82 -> return of("ISSUER AUTHENTICATION", "EXTERNAL AUTHENTICATE")
            0x2A -> return of("MAG-STRIPE MODE", "COMPUTE CRYPTOGRAPHIC CHECKSUM")
            0xEA -> return of("RELAY RESISTANCE", "EXCHANGE RELAY RESISTANCE DATA")
            0xC0 -> return of(currentLevel ?: "GET RESPONSE", "GET RESPONSE")
            0x1E -> return of("ISSUER SCRIPT PROCESSING", "APPLICATION BLOCK")
            0x18 -> return of("ISSUER SCRIPT PROCESSING", "APPLICATION UNBLOCK")
            0x16 -> return of("ISSUER SCRIPT PROCESSING", "CARD BLOCK")
            0x24 -> return of("ISSUER SCRIPT PROCESSING", "PIN CHANGE/UNBLOCK")
            0xDA -> return of("ISSUER SCRIPT PROCESSING", "PUT DATA")
            0xDC -> return of("ISSUER SCRIPT PROCESSING", "UPDATE RECORD")
            else -> {
                val name = String.format(Locale.US, "CLA %02X INS %02X", cla, ins)
                return if (scriptCla) {
                    of("ISSUER SCRIPT PROCESSING", name)
                } else {
                    of(currentLevel ?: "OTHER", name)
                }
            }
        }
    }

    private companion object {
        const val TAG = "ApduTrace"

        /** Keeps a card that loops on a command from growing the trace without bound. */
        const val MAX_LINES = 5000

        val PSE: ByteArray = "1PAY.SYS.DDF01".toByteArray()
        val PPSE: ByteArray = "2PAY.SYS.DDF01".toByteArray()

        fun of(level: String, name: String): Array<String> = arrayOf(level, name)

        fun describeSw(sw: Int): String {
            when (sw) {
                0x9000 -> return "OK"
                0x6283 -> return "selected file invalidated (app blocked)"
                0x6700 -> return "wrong length"
                0x6983 -> return "PIN blocked"
                0x6984 -> return "reference data invalidated"
                0x6985 -> return "conditions of use not satisfied"
                0x6A81 -> return "function not supported (card/app blocked)"
                0x6A82 -> return "file/application not found"
                0x6A83 -> return "record not found"
                0x6A88 -> return "referenced data not found"
                0x6D00 -> return "INS not supported"
                0x6E00 -> return "CLA not supported"
            }
            val sw1 = sw shr 8
            if (sw1 == 0x61) {
                return (sw and 0xFF).toString() + " more bytes available"
            }
            if (sw1 == 0x6C) {
                return "wrong Le, use " + (sw and 0xFF)
            }
            if ((sw and 0xFFF0) == 0x63C0) {
                return "wrong PIN, " + (sw and 0x0F) + " tries left"
            }
            return ""
        }

        /** Reads the CID (9F27) from a GENERATE AC response, format 1 (80) or format 2 (77). */
        fun cryptogramType(resp: ByteArray, len: Int): String? {
            var cid = -1
            if ((resp[0].toInt() and 0xFF) == 0x80 && len > 2) {
                cid = resp[2].toInt() and 0xFF
            } else if ((resp[0].toInt() and 0xFF) == 0x77) {
                var i = 0
                while (i + 3 < len) {
                    if ((resp[i].toInt() and 0xFF) == 0x9F && (resp[i + 1].toInt() and 0xFF) == 0x27 &&
                        (resp[i + 2].toInt() and 0xFF) == 0x01
                    ) {
                        cid = resp[i + 3].toInt() and 0xFF
                        break
                    }
                    i++
                }
            }
            if (cid < 0) {
                return null
            }
            val type = when (cid and 0xC0) {
                0x80 -> "ARQC (go online)"
                0x40 -> "TC (approved offline)"
                0x00 -> "AAC (declined)"
                else -> "RFU"
            }
            return String.format(Locale.US, "CID=%02X %s", cid, type)
        }

        fun hex(bytes: ByteArray, from: Int, to: Int): String {
            val sb = StringBuilder((to - from) * 2)
            for (i in from until to) {
                sb.append(String.format("%02X", bytes[i].toInt() and 0xFF))
            }
            return sb.toString()
        }
    }
}

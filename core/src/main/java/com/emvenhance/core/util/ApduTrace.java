package com.emvenhance.core.util;

import android.util.Log;
import androidx.annotation.Nullable;
import com.emvenhance.core.BuildConfig;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Per-transaction APDU trace: every command the terminal sends to the card and every response,
 * each flagged with the EMV level it belongs to, plus the kernel's own step changes.
 *
 * <p>Owned by {@code EmvEngine} (one instance, {@code EmvEngine#apduTrace()}): the engine starts
 * it fresh in {@code begin()} and records every step it publishes; the vendor device layer (PAX:
 * {@code EmvDeviceImpl}, handed this instance at transaction start) records the APDUs; the UI
 * reads it through {@code PosTerminal#apduTrace()} to show, copy and print. This class only
 * records and decodes — it holds no transaction logic of its own.
 *
 * <p>Every line also goes to Logcat under the {@value #TAG} tag.
 *
 * <p>Debug builds only, like {@link EmvLog}: the trace holds card data (PAN, track 2 in READ
 * RECORD responses), so {@link #isEnabled()} is always {@code false} in a release build. The
 * VERIFY command's data (the offline PIN block) is never recorded.
 */
public final class ApduTrace {

    private static final String TAG = "ApduTrace";

    /** Keeps a card that loops on a command from growing the trace without bound. */
    private static final int MAX_LINES = 5000;

    private static final byte[] PSE = "1PAY.SYS.DDF01".getBytes();
    private static final byte[] PPSE = "2PAY.SYS.DDF01".getBytes();

    /** Recording is possible at all — {@code BuildConfig.DEBUG}: never in a release build. */
    private final boolean allowed;
    private volatile boolean enabled = true;

    private final List<String> lines = new ArrayList<>();
    private final BehaviorSubject<Long> changes = BehaviorSubject.createDefault(0L);
    private long version;

    private long startMillis = System.currentTimeMillis();
    private int apduCount;
    private boolean gpoSent;
    private int generateAcCount;
    @Nullable
    private String currentLevel;
    @Nullable
    private String lastCommandLevel;

    public ApduTrace() {
        this(BuildConfig.DEBUG);
    }

    /** {@code allowed = false} is a release build: nothing is ever recorded. For tests. */
    ApduTrace(boolean allowed) {
        this.allowed = allowed;
    }

    /** Runtime on/off (the UI checkbox). Has no effect in a release build. */
    public void setEnabled(boolean on) {
        enabled = on;
    }

    public boolean isEnabled() {
        return allowed && enabled;
    }

    /** Emits whenever the trace changes; read the content with {@link #text()}. */
    public Observable<Long> changes() {
        return changes.hide();
    }

    /** Starts a fresh trace for a new transaction. */
    public void beginTransaction() {
        if (!isEnabled()) {
            return;
        }
        synchronized (this) {
            lines.clear();
            startMillis = System.currentTimeMillis();
            apduCount = 0;
            gpoSent = false;
            generateAcCount = 0;
            currentLevel = null;
            lastCommandLevel = null;
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            add("===== EMV TRANSACTION =====");
            add(now);
        }
    }

    /** A kernel/engine EMV step change, e.g. "5. Read application data". */
    public void kernelStep(String step) {
        if (!isEnabled()) {
            return;
        }
        synchronized (this) {
            add(elapsed() + "### KERNEL STEP " + step);
        }
    }

    /** A transaction-level event (card detected, online, approved, error...). */
    public void transactionEvent(String event) {
        if (!isEnabled()) {
            return;
        }
        synchronized (this) {
            add(elapsed() + "--- " + event);
        }
    }

    /** Free-form note, e.g. a reader error. */
    public void note(String channel, String text) {
        if (!isEnabled()) {
            return;
        }
        synchronized (this) {
            add(elapsed() + "!!! " + channel + " " + text);
        }
    }

    /** Card answer-to-reset after power-on. */
    public void atr(String channel, @Nullable byte[] atr) {
        if (!isEnabled()) {
            return;
        }
        synchronized (this) {
            add(elapsed() + channel + " ATR " + (atr == null ? "(none)" : hex(atr, 0, atr.length)));
        }
    }

    /**
     * An outgoing C-APDU. {@code dataIn} may be a larger fixed buffer (PAX's is 512 bytes); only
     * its first {@code lc} bytes are sent and recorded.
     */
    public void command(String channel, byte[] header, int lc, @Nullable byte[] dataIn,
            int le) {
        if (!isEnabled() || header == null || header.length < 4) {
            return;
        }
        synchronized (this) {
            int cla = header[0] & 0xFF;
            int ins = header[1] & 0xFF;
            int p1 = header[2] & 0xFF;
            int p2 = header[3] & 0xFF;
            int dataLen = dataIn == null ? 0 : Math.max(0, Math.min(lc, dataIn.length));
            byte[] data = dataIn == null ? new byte[0] : Arrays.copyOf(dataIn, dataLen);

            String[] decoded = decode(cla, ins, p1, p2, data);
            String level = decoded[0];
            String name = decoded[1];
            if (!level.equals(currentLevel)) {
                currentLevel = level;
                add("");
                add("=== EMV LEVEL: " + level + " ===");
            }
            lastCommandLevel = level;
            apduCount++;

            add(String.format(Locale.US, "%s#%02d %s >> %s", elapsed(), apduCount, channel, name));
            StringBuilder raw = new StringBuilder(hex(header, 0, 4));
            if (dataLen > 0) {
                raw.append(' ').append(String.format("%02X", dataLen)).append(' ');
                raw.append(ins == 0x20 ? "<PIN block not logged>" : hex(data, 0, dataLen));
            }
            if (le > 0) {
                // ISO 7816-4: Le of 256 is encoded as 00.
                raw.append(' ').append(String.format("%02X", le & 0xFF));
            }
            add("    C-APDU: " + raw);
        }
    }

    /** The R-APDU for the last {@link #command}. */
    public void response(String channel, byte sw1, byte sw2, @Nullable byte[] dataOut,
            int length) {
        if (!isEnabled()) {
            return;
        }
        synchronized (this) {
            int len = dataOut == null ? 0 : Math.max(0, Math.min(length, dataOut.length));
            int sw = ((sw1 & 0xFF) << 8) | (sw2 & 0xFF);
            StringBuilder head = new StringBuilder(String.format(Locale.US,
                    "#%02d %s << SW=%04X %s, %d bytes", apduCount, channel, sw, describeSw(sw), len));
            if (len > 0 && lastCommandLevel != null && lastCommandLevel.contains("GENERATE AC")) {
                String cid = cryptogramType(dataOut, len);
                if (cid != null) {
                    head.append(", ").append(cid);
                }
            }
            add(head.toString());
            if (len > 0) {
                add("    R-APDU: " + hex(dataOut, 0, len));
            }
        }
    }

    /** The whole trace, one entry per line. */
    public String text() {
        synchronized (this) {
            return String.join("\n", lines);
        }
    }

    public boolean isEmpty() {
        synchronized (this) {
            return lines.isEmpty();
        }
    }

    /** The trace wrapped to {@code width} characters, for a receipt printer. */
    public List<String> wrapped(int width) {
        List<String> out = new ArrayList<>();
        synchronized (this) {
            for (String line : lines) {
                if (line.length() <= width) {
                    out.add(line);
                    continue;
                }
                out.add(line.substring(0, width));
                for (int i = width; i < line.length(); i += width - 4) {
                    out.add("    " + line.substring(i, Math.min(line.length(), i + width - 4)));
                }
            }
        }
        return out;
    }

    // ─── internals (callers hold this trace's lock) ─────────────────────────

    private void add(String line) {
        Log.d(TAG, line);
        if (lines.size() >= MAX_LINES) {
            if (lines.size() == MAX_LINES) {
                lines.add("... trace truncated at " + MAX_LINES + " lines ...");
                changes.onNext(++version);
            }
            return;
        }
        lines.add(line);
        changes.onNext(++version);
    }

    /** Seconds since the transaction started, as a line prefix. */
    private String elapsed() {
        long ms = System.currentTimeMillis() - startMillis;
        return String.format(Locale.US, "[%d.%03d] ", ms / 1000, ms % 1000);
    }

    /** Returns {EMV level, command name} for a C-APDU. */
    private String[] decode(int cla, int ins, int p1, int p2, byte[] data) {
        boolean scriptCla = (cla & 0xF0) == 0x80 && (cla & 0x0C) != 0; // 84 / 8C: secure messaging
        switch (ins) {
            case 0xA4: {
                if (Arrays.equals(data, PSE)) {
                    return of("APPLICATION SELECTION", "SELECT PSE 1PAY.SYS.DDF01");
                }
                if (Arrays.equals(data, PPSE)) {
                    return of("APPLICATION SELECTION", "SELECT PPSE 2PAY.SYS.DDF01");
                }
                String next = p2 == 0x02 ? " (next occurrence)" : "";
                return of("APPLICATION SELECTION", "SELECT AID " + hex(data, 0, data.length) + next);
            }
            case 0xA8:
                gpoSent = true;
                return of("INITIATE APPLICATION PROCESSING", "GET PROCESSING OPTIONS (GPO)");
            case 0xB2: {
                String name = String.format(Locale.US, "READ RECORD SFI %d REC %d", p2 >> 3, p1);
                return gpoSent ? of("READ APPLICATION DATA", name)
                        : of("APPLICATION SELECTION", name + " (directory)");
            }
            case 0x88:
                return of("OFFLINE DATA AUTHENTICATION", "INTERNAL AUTHENTICATE (DDA)");
            case 0xCA:
            case 0xCB: {
                int tag = (p1 << 8) | p2;
                String name = String.format(Locale.US, "GET DATA %04X", tag);
                switch (tag) {
                    case 0x9F17:
                        return of("CARDHOLDER VERIFICATION", name + " (PIN try counter)");
                    case 0x9F36:
                        return of("TERMINAL RISK MANAGEMENT", name + " (ATC)");
                    case 0x9F13:
                        return of("TERMINAL RISK MANAGEMENT", name + " (last online ATC)");
                    case 0x9F4F:
                        return of("TRANSACTION LOG", name + " (log format)");
                    default:
                        return of(currentLevel != null ? currentLevel : "GET DATA", name);
                }
            }
            case 0x84:
                return of("CARDHOLDER VERIFICATION", "GET CHALLENGE (enciphered offline PIN)");
            case 0x20:
                return of("CARDHOLDER VERIFICATION", p2 == 0x88
                        ? "VERIFY (enciphered offline PIN)" : "VERIFY (plaintext offline PIN)");
            case 0xAE: {
                generateAcCount++;
                String type;
                switch (p1 & 0xC0) {
                    case 0x80:
                        type = "ARQC";
                        break;
                    case 0x40:
                        type = "TC";
                        break;
                    default:
                        type = "AAC";
                        break;
                }
                String cda = (p1 & 0x10) != 0 ? " +CDA" : "";
                return generateAcCount == 1
                        ? of("TERMINAL/CARD ACTION ANALYSIS (1st GENERATE AC)",
                                "1st GENERATE AC request " + type + cda)
                        : of("COMPLETION (2nd GENERATE AC)", "2nd GENERATE AC request " + type + cda);
            }
            case 0x82:
                return of("ISSUER AUTHENTICATION", "EXTERNAL AUTHENTICATE");
            case 0x2A:
                return of("MAG-STRIPE MODE", "COMPUTE CRYPTOGRAPHIC CHECKSUM");
            case 0xEA:
                return of("RELAY RESISTANCE", "EXCHANGE RELAY RESISTANCE DATA");
            case 0xC0:
                return of(currentLevel != null ? currentLevel : "GET RESPONSE", "GET RESPONSE");
            case 0x1E:
                return of("ISSUER SCRIPT PROCESSING", "APPLICATION BLOCK");
            case 0x18:
                return of("ISSUER SCRIPT PROCESSING", "APPLICATION UNBLOCK");
            case 0x16:
                return of("ISSUER SCRIPT PROCESSING", "CARD BLOCK");
            case 0x24:
                return of("ISSUER SCRIPT PROCESSING", "PIN CHANGE/UNBLOCK");
            case 0xDA:
                return of("ISSUER SCRIPT PROCESSING", "PUT DATA");
            case 0xDC:
                return of("ISSUER SCRIPT PROCESSING", "UPDATE RECORD");
            default:
                String name = String.format(Locale.US, "CLA %02X INS %02X", cla, ins);
                return scriptCla ? of("ISSUER SCRIPT PROCESSING", name)
                        : of(currentLevel != null ? currentLevel : "OTHER", name);
        }
    }

    private static String[] of(String level, String name) {
        return new String[] {level, name};
    }

    private static String describeSw(int sw) {
        switch (sw) {
            case 0x9000:
                return "OK";
            case 0x6283:
                return "selected file invalidated (app blocked)";
            case 0x6700:
                return "wrong length";
            case 0x6983:
                return "PIN blocked";
            case 0x6984:
                return "reference data invalidated";
            case 0x6985:
                return "conditions of use not satisfied";
            case 0x6A81:
                return "function not supported (card/app blocked)";
            case 0x6A82:
                return "file/application not found";
            case 0x6A83:
                return "record not found";
            case 0x6A88:
                return "referenced data not found";
            case 0x6D00:
                return "INS not supported";
            case 0x6E00:
                return "CLA not supported";
            default:
                break;
        }
        int sw1 = sw >> 8;
        if (sw1 == 0x61) {
            return (sw & 0xFF) + " more bytes available";
        }
        if (sw1 == 0x6C) {
            return "wrong Le, use " + (sw & 0xFF);
        }
        if ((sw & 0xFFF0) == 0x63C0) {
            return "wrong PIN, " + (sw & 0x0F) + " tries left";
        }
        return "";
    }

    /** Reads the CID (9F27) from a GENERATE AC response, format 1 (80) or format 2 (77). */
    @Nullable
    private static String cryptogramType(byte[] resp, int len) {
        int cid = -1;
        if ((resp[0] & 0xFF) == 0x80 && len > 2) {
            cid = resp[2] & 0xFF;
        } else if ((resp[0] & 0xFF) == 0x77) {
            for (int i = 0; i + 3 < len; i++) {
                if ((resp[i] & 0xFF) == 0x9F && (resp[i + 1] & 0xFF) == 0x27
                        && (resp[i + 2] & 0xFF) == 0x01) {
                    cid = resp[i + 3] & 0xFF;
                    break;
                }
            }
        }
        if (cid < 0) {
            return null;
        }
        String type;
        switch (cid & 0xC0) {
            case 0x80:
                type = "ARQC (go online)";
                break;
            case 0x40:
                type = "TC (approved offline)";
                break;
            case 0x00:
                type = "AAC (declined)";
                break;
            default:
                type = "RFU";
                break;
        }
        return String.format(Locale.US, "CID=%02X %s", cid, type);
    }

    private static String hex(byte[] bytes, int from, int to) {
        StringBuilder sb = new StringBuilder((to - from) * 2);
        for (int i = from; i < to; i++) {
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}

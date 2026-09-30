package com.emvenhance.vendor.pax;

import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.dal.IPrinter;
import com.pax.dal.entity.EFontTypeAscii;
import com.pax.dal.entity.EFontTypeExtCode;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;

/**
 * Real thermal-printer output via {@link IPrinter} ({@code EmvFlowRuntime.getDal().getPrinter()}).
 * Replaces {@link com.emvenhance.core.host.HostDefaults#logPrinter}, which only ever wrote to
 * Logcat — nothing in this project called the actual PAX printer API before this.
 */
final class PaxPrinter implements PrinterBehavior {

    private static final String TAG = "PaxPrinter";

    /**
     * {@code printStr}'s encoding param — GBK per PAX's own demo/reference usage; it's a
     * superset of ASCII, so plain English/Latin receipt lines print correctly under it too.
     */
    private static final String ENCODING = "GBK";

    /** Blank feed (in dot-lines) before the tear-off edge, after the last printed line. */
    private static final int FEED_DOTS = 80;

    /**
     * Lines per print job. A full APDU trace runs to hundreds of lines — more than the printer
     * buffer holds in one job — so long output is printed in consecutive jobs.
     */
    private static final int LINES_PER_JOB = 40;

    /**
     * {@code IPrinter#setGray} darkness: 1 = default, 3 = 150%, 4 = 200% of default. The default
     * came out too faint to read the APDU hex; 4 prints solid black (drop to 3 if the head runs
     * hot on long traces).
     */
    private static final int PRINT_GRAY = 4;

    @Override
    public Completable print(List<String> lines) {
        return Completable.fromAction(() -> {
            IDAL dal = EmvFlowRuntime.getDal();
            if (dal == null) {
                throw new IllegalStateException("Neptune DAL not ready — cannot print");
            }
            IPrinter printer = dal.getPrinter();
            for (int from = 0; from < lines.size() || from == 0; from += LINES_PER_JOB) {
                int to = Math.min(lines.size(), from + LINES_PER_JOB);
                printer.init();
                // 12x24 ASCII font: 32 characters per line on the 384-dot head. The 8x16 font
                // fit 48 but its thin strokes were too faint to read.
                printer.fontSet(EFontTypeAscii.FONT_12_24, EFontTypeExtCode.FONT_24_24);
                printer.setGray(PRINT_GRAY);
                for (String line : lines.subList(from, to)) {
                    printer.printStr(line + "\n", ENCODING);
                }
                if (to >= lines.size()) {
                    printer.step(FEED_DOTS);
                }
                int ret = printer.start();
                LogUtils.i(TAG, "print start() ret=" + ret + " (lines " + from + "-" + to
                        + " of " + lines.size() + ")");
                if (ret != 0) {
                    // 2 = out of paper, 8 = overheated, etc. — stop instead of losing lines.
                    throw new IllegalStateException("Printer error " + ret + " at line " + from);
                }
                if (to >= lines.size()) {
                    break;
                }
            }
        });
    }
}

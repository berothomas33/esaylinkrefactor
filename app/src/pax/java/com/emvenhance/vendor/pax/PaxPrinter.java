package com.emvenhance.vendor.pax;

import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.pax.commonlib.utils.LogUtils;
import com.pax.dal.IDAL;
import com.pax.dal.IPrinter;
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

    @Override
    public Completable print(List<String> lines) {
        return Completable.fromAction(() -> {
            IDAL dal = EmvFlowRuntime.getDal();
            if (dal == null) {
                throw new IllegalStateException("Neptune DAL not ready — cannot print");
            }
            IPrinter printer = dal.getPrinter();
            printer.init();
            for (String line : lines) {
                printer.printStr(line + "\n", ENCODING);
            }
            printer.step(FEED_DOTS);
            int ret = printer.start();
            LogUtils.i(TAG, "print start() ret=" + ret + " (" + lines.size() + " lines)");
        });
    }
}

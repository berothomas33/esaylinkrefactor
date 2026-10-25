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

final class PaxPrinter implements PrinterBehavior {

    private static final String TAG = "PaxPrinter";

    private static final String ENCODING = "GBK";

    private static final int FEED_DOTS = 80;

    private static final int LINES_PER_JOB = 40;

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
                    throw new IllegalStateException("Printer error " + ret + " at line " + from);
                }
                if (to >= lines.size()) {
                    break;
                }
            }
        });
    }
}

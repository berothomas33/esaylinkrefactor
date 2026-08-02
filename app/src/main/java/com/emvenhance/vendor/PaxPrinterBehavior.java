package com.emvenhance.vendor;

import com.emvenhance.core.PrinterBehavior;
import com.pax.commonlib.utils.LogUtils;
import java.util.List;

public class PaxPrinterBehavior implements PrinterBehavior {

    private static final String TAG = "PaxPrinter";

    @Override
    public boolean print(List<String> lines) {
        // TODO drive the terminal printer via EmvFlowRuntime.getDal().getPrinter()
        LogUtils.i(TAG, String.join("\n", lines));
        return true;
    }
}

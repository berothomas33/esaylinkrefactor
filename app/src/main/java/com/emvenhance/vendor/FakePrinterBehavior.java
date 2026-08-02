package com.emvenhance.vendor;

import com.emvenhance.core.PrinterBehavior;
import com.pax.commonlib.utils.LogUtils;
import java.util.List;

public class FakePrinterBehavior implements PrinterBehavior {

    private static final String TAG = "FakePrinter";

    @Override
    public boolean print(List<String> lines) {
        LogUtils.i(TAG, String.join("\n", lines));
        return true;
    }
}

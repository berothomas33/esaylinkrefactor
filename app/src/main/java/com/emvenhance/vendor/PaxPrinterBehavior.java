package com.emvenhance.vendor;

import com.emvenhance.core.PrinterBehavior;
import com.pax.commonlib.utils.LogUtils;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;

public class PaxPrinterBehavior implements PrinterBehavior {

    private static final String TAG = "PaxPrinter";

    @Override
    public Completable print(List<String> lines) {
        return Completable.fromAction(() -> {
            // TODO drive the terminal printer via EmvFlowRuntime.getDal().getPrinter()
            LogUtils.i(TAG, String.join("\n", lines));
        });
    }
}

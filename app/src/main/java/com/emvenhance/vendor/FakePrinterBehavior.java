package com.emvenhance.vendor;

import android.util.Log;
import com.emvenhance.core.PrinterBehavior;
import io.reactivex.rxjava3.core.Completable;
import java.util.List;

public class FakePrinterBehavior implements PrinterBehavior {

    private static final String TAG = "FakePrinter";

    @Override
    public Completable print(List<String> lines) {
        return Completable.fromAction(() -> {
            for (String line : lines) {
                Log.i(TAG, line);
            }
        });
    }
}

package com.emvenhance;

import com.emvenhance.core.PosTerminal;
import com.emvenhance.vendor.FakeTerminal;
import com.emvenhance.vendor.IngenicoTerminal;
import com.emvenhance.vendor.PaxTerminal;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Picks the vendor terminal based on build config. Each terminal owns card search
 * and creates its own EmvBehavior — no other wiring needed.
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";
    private PosTerminal terminal;

    @Override
    public void onCreate() {
        super.onCreate();
        String vendor = BuildConfig.VENDOR;
        LogUtils.i(TAG, "vendor=" + vendor);

        if ("PAX".equalsIgnoreCase(vendor)) {
            PaxRuntime.init(this);
            terminal = new PaxTerminal();
        } else if ("INGENICO".equalsIgnoreCase(vendor)) {
            terminal = new IngenicoTerminal();
        } else {
            terminal = new FakeTerminal();
        }
    }

    public PosTerminal getTerminal() { return terminal; }
}

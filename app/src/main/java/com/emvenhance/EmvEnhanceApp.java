package com.emvenhance;

import com.emvenhance.core.PosTerminal;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.vendor.FakeTerminal;
import com.emvenhance.vendor.IngenicoTerminal;
import com.emvenhance.vendor.PaxTerminal;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Picks the vendor terminal. Each terminal owns card search and creates its EmvBehavior.
 *
 * <pre>
 *   PAX      → PaxTerminal      → PaxEmvBehavior
 *   INGENICO → IngenicoTerminal → IngenicoEmvBehavior  (stub until SDK attached)
 *   FAKE     → FakeTerminal     → FakeEmvBehavior
 * </pre>
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";

    private PosTerminal terminal;

    @Override
    public void onCreate() {
        super.onCreate();
        EmvFlowRuntime.init(this);

        String vendor = BuildConfig.VENDOR;
        LogUtils.i(TAG, "vendor=" + vendor);

        if ("PAX".equalsIgnoreCase(vendor)) {
            terminal = new PaxTerminal();
        } else if ("INGENICO".equalsIgnoreCase(vendor)) {
            terminal = new IngenicoTerminal();
        } else {
            terminal = new FakeTerminal();
        }
    }

    public PosTerminal getTerminal() {
        return terminal;
    }
}

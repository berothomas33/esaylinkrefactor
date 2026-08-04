package com.emvenhance;

import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.emvflow.runtime.EmvFlowRuntime;
import com.emvenhance.vendor.TerminalFactory;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Boots one vendor-agnostic {@link PosTerminal}. UI never sees Pax/Ingenico/Fake types.
 *
 * <pre>
 *   TerminalFactory.create(VENDOR) → PosTerminal
 *     PAX      → vendor.pax.PaxTerminal
 *     INGENICO → vendor.ingenico.IngenicoTerminal
 *     FAKE     → vendor.fake.FakeTerminal
 * </pre>
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";

    private PosTerminal terminal;

    @Override
    public void onCreate() {
        super.onCreate();

        String vendor = BuildConfig.VENDOR;
        LogUtils.i(TAG, "vendor=" + vendor);

        if (TerminalFactory.needsPaxRuntime(vendor)) {
            EmvFlowRuntime.init(this);
        }

        terminal = TerminalFactory.create(vendor);
    }

    public PosTerminal getTerminal() {
        return terminal;
    }
}

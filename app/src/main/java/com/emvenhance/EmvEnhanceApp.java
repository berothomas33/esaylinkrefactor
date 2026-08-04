package com.emvenhance;

import com.emvenhance.core.PosTerminal;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.vendor.FakeTerminal;
import com.emvenhance.vendor.PaxTerminal;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Picks the vendor terminal. Each terminal creates its own EMV behavior.
 *
 * <pre>
 *   PAX  → PaxTerminal  → PaxEmvBehavior
 *   FAKE → FakeTerminal → FakeEmvBehavior
 * </pre>
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";

    private PosTerminal terminal;

    @Override
    public void onCreate() {
        super.onCreate();
        EmvFlowRuntime.init(this);

        boolean pax = "PAX".equalsIgnoreCase(BuildConfig.VENDOR);
        LogUtils.i(TAG, "vendor=" + BuildConfig.VENDOR);

        terminal = pax ? new PaxTerminal() : new FakeTerminal();
    }

    public PosTerminal getTerminal() {
        return terminal;
    }
}

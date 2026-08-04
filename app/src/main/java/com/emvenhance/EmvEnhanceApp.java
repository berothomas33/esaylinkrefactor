package com.emvenhance;

import com.emvenhance.core.PosTerminal;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.vendor.FakeCommunicationBehavior;
import com.emvenhance.vendor.FakeEmvEngine;
import com.emvenhance.vendor.FakePrinterBehavior;
import com.emvenhance.vendor.PaxCommunicationBehavior;
import com.emvenhance.vendor.PaxEmvEngine;
import com.emvenhance.vendor.PaxPrinterBehavior;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Picks the vendor and builds the single {@link PosTerminal} the UI talks to.
 *
 * <p>This is the only place that knows which vendor is active. Adding a vendor means
 * writing its engine + communication + printer classes and extending the branch below.
 *
 * <p>For PAX, {@link PaxEmvEngine} owns the transaction lifecycle and delegates all PAX SDK
 * I/O to {@link com.emvenhance.vendor.PaxEmvBehavior} — the sole bridge to the PAX layer.
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

        terminal = pax
                ? new PosTerminal(new PaxEmvEngine(), new PaxCommunicationBehavior(),
                        new PaxPrinterBehavior())
                : new PosTerminal(new FakeEmvEngine(), new FakeCommunicationBehavior(),
                        new FakePrinterBehavior());
    }

    public PosTerminal getTerminal() {
        return terminal;
    }
}

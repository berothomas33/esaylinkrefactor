package com.emvenhance;

import com.emvenhance.core.EmvTransaction;
import com.emvenhance.emvflow.EmvFlowRuntime;
import com.emvenhance.vendor.FakeCommunicationBehavior;
import com.emvenhance.vendor.FakeEmvBehavior;
import com.emvenhance.vendor.FakePrinterBehavior;
import com.emvenhance.vendor.PaxCommunicationBehavior;
import com.emvenhance.vendor.PaxEmvBehavior;
import com.emvenhance.vendor.PaxPrinterBehavior;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Picks the vendor and builds the single {@link EmvTransaction} the UI talks to.
 *
 * <p>This is the only place that knows which vendor is active. Adding a vendor means
 * writing its three behaviour classes and extending the branch below.
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";

    private EmvTransaction transaction;

    @Override
    public void onCreate() {
        super.onCreate();
        EmvFlowRuntime.init(this);

        boolean pax = "PAX".equalsIgnoreCase(BuildConfig.VENDOR);
        LogUtils.i(TAG, "vendor=" + BuildConfig.VENDOR);

        transaction = pax
                ? new EmvTransaction(new PaxEmvBehavior(), new PaxPrinterBehavior(),
                        new PaxCommunicationBehavior())
                : new EmvTransaction(new FakeEmvBehavior(), new FakePrinterBehavior(),
                        new FakeCommunicationBehavior());
    }

    public EmvTransaction getTransaction() {
        return transaction;
    }
}

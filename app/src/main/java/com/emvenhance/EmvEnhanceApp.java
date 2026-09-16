package com.emvenhance;

import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.network.env.EnvironmentProvider;
import com.emvenhance.vendor.TerminalFactory;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.utils.LogUtils;

/**
 * Boots one vendor-agnostic {@link PosTerminal}. UI never sees Pax/Ingenico/Fake types.
 *
 * <p>Which vendor gets built is a Gradle product flavor (pax/ingenico/fake), not a runtime
 * switch — each flavor compiles only its own {@code vendor.*} sources and provides its own
 * {@link TerminalFactory}. Which host {@code EnvironmentProvider} points at is a second,
 * independent flavor dimension ("environment" — prod/envtest/uat/uatr3/bank/pos_as_atm/dss);
 * {@link EnvironmentProvider#setBaseUrl} must run before {@link TerminalFactory#create}, since
 * building a vendor terminal is what constructs its {@code RetrofitCommunicationBehavior}.
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";

    private PosTerminal terminal;

    @Override
    public void onCreate() {
        super.onCreate();

        LogUtils.i(TAG, "vendor=" + BuildConfig.VENDOR + " baseUrl=" + BuildConfig.baseUrl);
        EnvironmentProvider.setBaseUrl(BuildConfig.baseUrl);
        terminal = TerminalFactory.create(this);
    }

    public PosTerminal getTerminal() {
        return terminal;
    }
}

package com.emvenhance;

import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.network.env.EnvironmentProvider;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
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
 *
 * <p>{@link #getOnboarding} / {@link #getOnboardingState} are exposed for a setup screen to run
 * the onboarding cycle and check its status — onboarding isn't auto-run at startup or gated on
 * here, since real onboarding is a one-time provisioning action a technician drives, not
 * something that should fire network calls unconditionally on every launch.
 */
public class EmvEnhanceApp extends BaseApplication {

    private static final String TAG = "EmvEnhanceApp";

    private PosTerminal terminal;
    private OnboardingClient onboarding;
    private OnboardingState onboardingState;

    @Override
    public void onCreate() {
        super.onCreate();

        LogUtils.i(TAG, "vendor=" + BuildConfig.VENDOR + " baseUrl=" + BuildConfig.baseUrl);
        EnvironmentProvider.setBaseUrl(BuildConfig.baseUrl);
        onboarding = new OnboardingClient();
        onboardingState = new OnboardingState(this);
        terminal = TerminalFactory.create(this);
    }

    public PosTerminal getTerminal() {
        return terminal;
    }

    public OnboardingClient getOnboarding() {
        return onboarding;
    }

    public OnboardingState getOnboardingState() {
        return onboardingState;
    }
}

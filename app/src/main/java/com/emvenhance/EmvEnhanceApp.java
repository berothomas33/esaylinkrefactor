package com.emvenhance;

import android.util.Log;
import com.emvenhance.core.terminal.PosTerminal;
import com.emvenhance.network.env.EnvironmentProvider;
import com.emvenhance.network.onboarding.OnboardingClient;
import com.emvenhance.network.onboarding.OnboardingState;
import com.emvenhance.vendor.TerminalFactory;
import com.emvenhance.vendor.VendorApplication;

public class EmvEnhanceApp extends VendorApplication {

    private static final String TAG = "EmvEnhanceApp";

    private PosTerminal terminal;
    private OnboardingClient onboarding;
    private OnboardingState onboardingState;

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "vendor=" + BuildConfig.VENDOR + " baseUrl=" + BuildConfig.baseUrl);
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

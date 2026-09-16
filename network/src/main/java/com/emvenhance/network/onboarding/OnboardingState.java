package com.emvenhance.network.onboarding;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks whether this terminal has completed each onboarding cycle — the same "run once and
 * remember it" shape {@code PaxTerminal} already uses for its own
 * {@code KEY_EMV_CONFIG_INITIALIZED} flag, generalized here since onboarding is vendor-agnostic
 * (plain HTTP against {@link com.emvenhance.network.HostApiClient}), not PAX-specific.
 */
public final class OnboardingState {

    private static final String PREFS_NAME = "onboarding_state";
    private static final String KEY_OFFLINE_ONBOARDED = "offline_onboarded";
    private static final String KEY_ONLINE_ONBOARDED = "online_onboarded";

    private final SharedPreferences prefs;

    public OnboardingState(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOfflineOnboarded() {
        return prefs.getBoolean(KEY_OFFLINE_ONBOARDED, false);
    }

    public void markOfflineOnboarded() {
        prefs.edit().putBoolean(KEY_OFFLINE_ONBOARDED, true).apply();
    }

    public boolean isOnlineOnboarded() {
        return prefs.getBoolean(KEY_ONLINE_ONBOARDED, false);
    }

    public void markOnlineOnboarded() {
        prefs.edit().putBoolean(KEY_ONLINE_ONBOARDED, true).apply();
    }

    /** Both cycles done — see {@code EmvEnhanceApp} for where this should gate transactions. */
    public boolean isFullyOnboarded() {
        return isOfflineOnboarded() && isOnlineOnboarded();
    }
}

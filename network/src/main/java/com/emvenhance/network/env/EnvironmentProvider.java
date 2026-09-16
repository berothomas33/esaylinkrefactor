package com.emvenhance.network.env;

import androidx.annotation.Nullable;

/**
 * Holds the base URL the active build resolved to. Environment selection is a compile-time
 * Gradle product flavor on {@code :app} (the "environment" dimension — prod/envtest/uat/uatr3/
 * bank/pos_as_atm/dss), same as the old project's per-build {@code Constants.EndPoint} — not a
 * runtime switch. {@code EmvEnhanceApp.onCreate()} calls {@link #setBaseUrl} with
 * {@code BuildConfig.baseUrl} before any vendor terminal (and so any
 * {@link com.emvenhance.network.RetrofitCommunicationBehavior}) is constructed.
 */
public final class EnvironmentProvider {

    @Nullable
    private static volatile String baseUrl;

    private EnvironmentProvider() {
    }

    public static void setBaseUrl(String baseUrl) {
        EnvironmentProvider.baseUrl = baseUrl;
    }

    public static String getBaseUrl() {
        if (baseUrl == null) {
            throw new IllegalStateException("EnvironmentProvider.setBaseUrl() was never called "
                    + "— set it from BuildConfig.baseUrl at app startup before building a "
                    + "HostApiClient.");
        }
        return baseUrl;
    }
}

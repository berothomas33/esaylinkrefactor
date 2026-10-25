package com.emvenhance.network.env;

import androidx.annotation.Nullable;

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

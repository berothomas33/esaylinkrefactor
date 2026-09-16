package com.emvenhance.network.env;

import androidx.annotation.NonNull;

/**
 * Holds the {@link ApiEnvironment} the app is currently pointed at. A single process-wide
 * holder is enough here — same lifetime assumption {@code EmvFlowRuntime} already makes for the
 * PAX DAL — and lets onboarding/settings screens switch environments without threading the
 * choice through every call site that builds a {@link com.emvenhance.network.HostApiClient}.
 */
public final class EnvironmentProvider {

    private static volatile ApiEnvironment current = ApiEnvironment.PRODUCTION;

    private EnvironmentProvider() {
    }

    @NonNull
    public static ApiEnvironment get() {
        return current;
    }

    public static void set(@NonNull ApiEnvironment environment) {
        current = environment;
    }
}

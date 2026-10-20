package com.emvenhance.network.env

/**
 * Holds the base URL the active build resolved to. Environment selection is a compile-time Gradle
 * product flavor on `:app` (the "environment" dimension — prod/envtest/uat/uatr3/bank/
 * pos_as_atm/dss), same as the old project's per-build `Constants.EndPoint` — not a runtime
 * switch. `EmvEnhanceApp.onCreate()` calls [setBaseUrl] with `BuildConfig.baseUrl` before any
 * vendor terminal (and so any [com.emvenhance.network.RetrofitCommunicationBehavior]) is
 * constructed.
 */
object EnvironmentProvider {

    @Volatile
    private var baseUrl: String? = null

    @JvmStatic
    fun setBaseUrl(baseUrl: String) {
        this.baseUrl = baseUrl
    }

    @JvmStatic
    fun getBaseUrl(): String = baseUrl ?: throw IllegalStateException(
        "EnvironmentProvider.setBaseUrl() was never called " +
            "— set it from BuildConfig.baseUrl at app startup before building a " +
            "HostApiClient.",
    )
}

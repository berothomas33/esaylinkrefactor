package com.emvenhance.network

import android.util.Log
import java.util.TreeMap

/**
 * The real header contract — confirmed against a full, successful captured offline onboarding
 * run (handshake → onboard → confirm → createToken → an authenticated `tmsFileDownload` call)
 * against the `envtest` environment, superseding the `Account-Id`/bearer-`Authorization`
 * placeholder this module used everywhere before this was known. Mirrors the old project's
 * `NetworkUtils#getHeaders`, whose own source was never shared — these header names/values were
 * read directly off that captured run, not reconstructed from a call site's positional args like
 * the rest of this module's placeholders were.
 *
 * Two mutually exclusive modes, matching what the captured run actually sent:
 * - [build] — pre-token, used by handshake/onboard/confirm/createToken (nothing to authenticate
 *   with yet): `aggregator-app-key`/`system-app-key`/`apiKey`/`lang` (all fixed — see
 *   [HostAppKeys]) + `sn`.
 * - [buildAuthenticated] — used once a token exists (`tmsFileDownload` in the captured run;
 *   presumed also for `orchestration/exchange`/`orchestration/sale`, not independently
 *   confirmed): same four, but `apiKey` is dropped and `Authorization: Bearer <accessToken>`
 *   takes its place — plus `accountId` (also fixed, see [HostAppKeys.ACCOUNT_ID]), which
 *   `tmsFileDownload` didn't need but `orchestration/exchange` 400'd without
 *   (`MissingRequestHeaderException`).
 */
object HostHeaders {

    private const val TAG = "HostHeaders"

    /** @param sn this terminal's own serial number (PAX: `ModelInfo#getSN()`) — the one non-fixed value. */
    @JvmStatic
    fun build(sn: String): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        headers["aggregator-app-key"] = HostAppKeys.AGGREGATOR_APP_KEY
        headers["system-app-key"] = HostAppKeys.SYSTEM_APP_KEY
        headers["sn"] = sn
        headers["apiKey"] = HostAppKeys.API_KEY
        headers["lang"] = HostAppKeys.DEFAULT_LANG
        logHeaders("build", headers)
        return headers
    }

    /**
     * @param sn this terminal's own serial number
     * @param accessToken from `OnboardingState#getAccessToken()` — see `OnboardingClient#createToken`
     */
    @JvmStatic
    fun buildAuthenticated(sn: String, accessToken: String): MutableMap<String, String> {
        val headers = HashMap<String, String>()
        headers["aggregator-app-key"] = HostAppKeys.AGGREGATOR_APP_KEY
        headers["system-app-key"] = HostAppKeys.SYSTEM_APP_KEY
        headers["sn"] = sn
        headers["Authorization"] = "Bearer $accessToken"
        headers["accountId"] = HostAppKeys.ACCOUNT_ID
        headers["lang"] = HostAppKeys.DEFAULT_LANG
        logHeaders("buildAuthenticated", headers)
        return headers
    }

    /**
     * Every header this terminal sends, for display (onboarding screen): [build]'s values, plus
     * `Authorization`/`accountId` once onboarding has produced an access token, plus the
     * `orchestration/sale`-only `mToken`. Sorted by name.
     *
     * @param accessToken current access token, or `null` before onboarding
     * @param mToken the `mToken` value in use ([HostSettings.getMToken])
     */
    @JvmStatic
    fun describeAll(sn: String, accessToken: String?, mToken: String): Map<String, String> {
        val headers = TreeMap<String, String>()
        headers["aggregator-app-key"] = HostAppKeys.AGGREGATOR_APP_KEY
        headers["system-app-key"] = HostAppKeys.SYSTEM_APP_KEY
        headers["sn"] = sn
        headers["apiKey"] = HostAppKeys.API_KEY
        headers["lang"] = HostAppKeys.DEFAULT_LANG
        headers["accountId"] = HostAppKeys.ACCOUNT_ID
        headers["Authorization"] =
            if (accessToken != null) "Bearer $accessToken" else "(none yet — run onboarding)"
        headers["mToken"] = mToken
        return headers
    }

    /**
     * One clearly-tagged, easy-to-copy log line per call — separate from OkHttp's own
     * `HttpLoggingInterceptor` dump (already on by default, see `HostApiClient`, but mixed in with
     * the rest of that request/response noise) so these can be pulled straight out of logcat
     * (e.g. `adb logcat -s HostHeaders`) and handed to the backend team as-is.
     *
     * Prints the real `Authorization` bearer token and `apiKey` in plaintext — fine for this
     * debug/bring-up phase talking to a test environment, but pull this call (or gate it behind a
     * debug build check) before this ships against production traffic.
     */
    private fun logHeaders(source: String, headers: Map<String, String>) {
        val sb = StringBuilder("[$source]")
        for ((key, value) in TreeMap(headers)) {
            sb.append("\n  ").append(key).append(": ").append(value)
        }
        Log.i(TAG, sb.toString())
    }
}

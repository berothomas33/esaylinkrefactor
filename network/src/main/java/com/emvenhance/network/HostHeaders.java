package com.emvenhance.network;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The real header contract — confirmed against a full, successful captured offline onboarding run
 * (handshake → onboard → confirm → createToken → an authenticated {@code tmsFileDownload} call)
 * against the {@code envtest} environment, superseding the {@code Account-Id}/bearer-
 * {@code Authorization} placeholder this module used everywhere before this was known. Mirrors
 * the old project's {@code NetworkUtils#getHeaders}, whose own source was never shared — these
 * header names/values were read directly off that captured run, not reconstructed from a call
 * site's positional args like the rest of this module's placeholders were.
 *
 * <p>Two mutually exclusive modes, matching what the captured run actually sent:
 * <ul>
 *   <li>{@link #build} — pre-token, used by handshake/onboard/confirm/createToken (nothing to
 *       authenticate with yet): {@code aggregator-app-key}/{@code system-app-key}/{@code apiKey}/
 *       {@code lang} (all fixed — see {@link HostAppKeys}) + {@code sn}.
 *   <li>{@link #buildAuthenticated} — used once a token exists ({@code tmsFileDownload} in the
 *       captured run; presumed also for {@code orchestration/exchange}/{@code orchestration/sale},
 *       not independently confirmed): same four, but {@code apiKey} is dropped and
 *       {@code Authorization: Bearer <accessToken>} takes its place — plus {@code accountId}
 *       (also fixed, see {@link HostAppKeys#ACCOUNT_ID}), which {@code tmsFileDownload} didn't
 *       need but {@code orchestration/exchange} 400'd without ({@code MissingRequestHeaderException}).
 * </ul>
 */
public final class HostHeaders {

    private static final String TAG = "HostHeaders";

    private HostHeaders() {
    }

    /** @param sn this terminal's own serial number (PAX: {@code ModelInfo#getSN()}) — the one non-fixed value. */
    public static Map<String, String> build(String sn) {
        Map<String, String> headers = new HashMap<>();
        headers.put("aggregator-app-key", HostAppKeys.AGGREGATOR_APP_KEY);
        headers.put("system-app-key", HostAppKeys.SYSTEM_APP_KEY);
        headers.put("sn", sn);
        headers.put("apiKey", HostAppKeys.API_KEY);
        headers.put("lang", HostAppKeys.DEFAULT_LANG);
        logHeaders("build", headers);
        return headers;
    }

    /**
     * @param sn this terminal's own serial number
     * @param accessToken from {@code OnboardingState#getAccessToken()} — see
     *      {@code OnboardingClient#createToken}
     */
    public static Map<String, String> buildAuthenticated(String sn, String accessToken) {
        Map<String, String> headers = new HashMap<>();
        headers.put("aggregator-app-key", HostAppKeys.AGGREGATOR_APP_KEY);
        headers.put("system-app-key", HostAppKeys.SYSTEM_APP_KEY);
        headers.put("sn", sn);
        headers.put("Authorization", "Bearer " + accessToken);
        headers.put("accountId", HostAppKeys.ACCOUNT_ID);
        headers.put("lang", HostAppKeys.DEFAULT_LANG);
        logHeaders("buildAuthenticated", headers);
        return headers;
    }

    /**
     * One clearly-tagged, easy-to-copy log line per call — separate from OkHttp's own
     * {@code HttpLoggingInterceptor} dump (already on by default, see {@code HostApiClient}, but
     * mixed in with the rest of that request/response noise) so these can be pulled straight out
     * of logcat (e.g. {@code adb logcat -s HostHeaders}) and handed to the backend team as-is.
     *
     * <p>Prints the real {@code Authorization} bearer token and {@code apiKey} in plaintext —
     * fine for this debug/bring-up phase talking to a test environment, but pull this call (or
     * gate it behind a debug build check) before this ships against production traffic.
     */
    private static void logHeaders(String source, Map<String, String> headers) {
        StringBuilder sb = new StringBuilder("[" + source + "]");
        for (Map.Entry<String, String> entry : new TreeMap<>(headers).entrySet()) {
            sb.append("\n  ").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        Log.i(TAG, sb.toString());
    }
}

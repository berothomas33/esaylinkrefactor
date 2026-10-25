package com.emvenhance.network;

import android.util.Log;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class HostHeaders {

    private static final String TAG = "HostHeaders";

    private HostHeaders() {
    }

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

    public static Map<String, String> describeAll(String sn, @Nullable String accessToken,
            String mToken) {
        Map<String, String> headers = new TreeMap<>();
        headers.put("aggregator-app-key", HostAppKeys.AGGREGATOR_APP_KEY);
        headers.put("system-app-key", HostAppKeys.SYSTEM_APP_KEY);
        headers.put("sn", sn);
        headers.put("apiKey", HostAppKeys.API_KEY);
        headers.put("lang", HostAppKeys.DEFAULT_LANG);
        headers.put("accountId", HostAppKeys.ACCOUNT_ID);
        headers.put("Authorization", accessToken != null
                ? "Bearer " + accessToken : "(none yet — run onboarding)");
        headers.put("mToken", mToken);
        return headers;
    }

    private static void logHeaders(String source, Map<String, String> headers) {
        StringBuilder sb = new StringBuilder("[" + source + "]");
        for (Map.Entry<String, String> entry : new TreeMap<>(headers).entrySet()) {
            sb.append("\n  ").append(entry.getKey()).append(": ").append(entry.getValue());
        }
        Log.i(TAG, sb.toString());
    }
}

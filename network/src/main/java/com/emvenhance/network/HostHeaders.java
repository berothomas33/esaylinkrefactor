package com.emvenhance.network;

import java.util.HashMap;
import java.util.Map;

/**
 * The real header contract — confirmed against a captured {@code foundation/onboarding/handshake}
 * request against the {@code envtest} environment, superseding the {@code Account-Id}/bearer-
 * {@code Authorization} placeholder this module used everywhere before this was known. Mirrors
 * the old project's {@code NetworkUtils#getHeaders}, whose own source was never shared — these
 * five header names were read directly off that captured request, not reconstructed from a call
 * site's positional args like the rest of this module's placeholders were.
 *
 * <p>Four of the five are fixed — {@link HostAppKeys#AGGREGATOR_APP_KEY}/
 * {@link HostAppKeys#SYSTEM_APP_KEY}/{@link HostAppKeys#API_KEY}/{@link HostAppKeys#DEFAULT_LANG}
 * — confirmed per direction, including {@code apiKey}: despite the old project's
 * {@code EncryptorHelper#getApiKey(deviceSerial)} naming suggesting a per-device derivation, it's
 * a fixed constant here too, not computed from {@link #build}'s {@code sn}.
 *
 * <p>No token/{@code Authorization} header appears in the captured handshake request — expected,
 * since no token exists before the old project's {@code createToken} step runs (not built here
 * yet, see {@code OnboardingClient}'s javadoc gap). Revisit this once that step exists.
 */
public final class HostHeaders {

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
        return headers;
    }
}

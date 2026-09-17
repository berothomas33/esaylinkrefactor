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
 * <p><b>{@link #build}'s {@code apiKey} param is still an open question</b> — the captured
 * request's {@code apiKey} value doesn't match a plain MD5/SHA-1/SHA-256 hash of that same
 * request's {@code sn} (checked), so whatever derives it — a keyed hash, an embedded salt, actual
 * encryption — isn't guessable from the old project's {@code EncryptorHelper#getApiKey(deviceSerial)}
 * call site alone; every caller of {@link #build} currently passes an empty string for it, which
 * will authenticate as the wrong device rather than not authenticate at all, so treat any 401/403
 * as "apiKey still needs its real derivation," not "headers are wrong."
 *
 * <p>No token/{@code Authorization} header appears in the captured handshake request — expected,
 * since no token exists before the old project's {@code createToken} step runs (not built here
 * yet, see {@code OnboardingClient}'s javadoc gap). Revisit this once that step exists.
 */
public final class HostHeaders {

    private HostHeaders() {
    }

    /**
     * @param sn this terminal's own serial number (PAX: {@code ModelInfo#getSN()}) — not fixed,
     *      unlike {@link HostAppKeys#AGGREGATOR_APP_KEY}/{@link HostAppKeys#SYSTEM_APP_KEY}
     * @param apiKey see this class's javadoc — pass {@code ""} until the real derivation is known
     */
    public static Map<String, String> build(String sn, String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("aggregator-app-key", HostAppKeys.AGGREGATOR_APP_KEY);
        headers.put("system-app-key", HostAppKeys.SYSTEM_APP_KEY);
        headers.put("sn", sn);
        headers.put("apiKey", apiKey);
        headers.put("lang", HostAppKeys.DEFAULT_LANG);
        return headers;
    }
}

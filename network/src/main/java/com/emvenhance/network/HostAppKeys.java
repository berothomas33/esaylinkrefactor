package com.emvenhance.network;

/**
 * Fixed, non-technician-entered identifiers the host expects on every authenticated request —
 * mirrors the old project's {@code ConfigurationActivity#aggregatorAppKey}/{@code SYSTEM_KEY},
 * which there came from an external aggregator app's launch {@code Intent} and an embedded
 * encrypted secret respectively; here both are fixed constants instead, confirmed against a real
 * captured {@code foundation/onboarding/handshake} request in the {@code envtest} environment:
 * <pre>
 *   aggregator-app-key: LKlEN/bz2UBkdefn3fGczJwyH3/69lS8
 *   system-app-key:     EsrflzqTWI4/QhENXPCoSx0HJ/hTTGsZ
 *   sn:                 1850040898   (that test device's own serial — NOT fixed, see HostHeaders)
 *   apiKey:              70D9411520B6EFA746D3E46B020280CD (that device's own apiKey — see HostHeaders)
 *   lang:               en
 * </pre>
 * See {@link HostHeaders} for where these get assembled into an actual header map.
 */
public final class HostAppKeys {

    public static final String AGGREGATOR_APP_KEY = "LKlEN/bz2UBkdefn3fGczJwyH3/69lS8";
    public static final String SYSTEM_APP_KEY = "EsrflzqTWI4/QhENXPCoSx0HJ/hTTGsZ";
    public static final String DEFAULT_LANG = "en";

    private HostAppKeys() {
    }
}

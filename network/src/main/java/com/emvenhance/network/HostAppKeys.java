package com.emvenhance.network;

/**
 * Fixed, non-technician-entered identifiers the host expects on every authenticated request —
 * mirrors the old project's {@code ConfigurationActivity#aggregatorAppKey}, which there came from
 * an external aggregator app's launch {@code Intent} (this app had no aggregator caller of its
 * own); here it's a fixed constant instead, per direction.
 *
 * <p>Not wired into any header-building code yet — the real {@code NetworkUtils#getHeaders} field
 * names/order aren't known (old call sites show 8 positional args: aggregatorAppKey, a
 * {@code SYSTEM_KEY} embedded secret, device language, device serial number, token, "",
 * an {@code EncryptorHelper#getApiKey(deviceSerial)}-derived key, "" — none confirmed by name).
 * {@code OnboardingActivity}/{@code EmvParamActivity}/{@code SaleCommunicationBehavior} still use
 * the {@code Account-Id}/bearer-{@code Authorization} placeholder convention until the rest of
 * that contract is confirmed.
 */
public final class HostAppKeys {

    public static final String AGGREGATOR_APP_KEY = "LKlEN/bz2UBkdefn3fGczJwyH3/69lS8";

    private HostAppKeys() {
    }
}

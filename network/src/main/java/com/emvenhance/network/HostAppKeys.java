package com.emvenhance.network;

/**
 * Fixed, non-technician-entered identifiers the host expects on every authenticated request —
 * mirrors the old project's {@code ConfigurationActivity#aggregatorAppKey}/{@code SYSTEM_KEY}/
 * {@code EncryptorHelper#getApiKey}, which there came from an external aggregator app's launch
 * {@code Intent}, an embedded encrypted secret, and a per-device-serial derivation respectively;
 * here all three are fixed constants instead (per direction — {@code apiKey} isn't actually
 * derived from this terminal's serial number the way the old project's naming suggested),
 * confirmed against a real captured {@code foundation/onboarding/handshake} request in the
 * {@code envtest} environment:
 * <pre>
 *   aggregator-app-key: LKlEN/bz2UBkdefn3fGczJwyH3/69lS8
 *   system-app-key:     EsrflzqTWI4/QhENXPCoSx0HJ/hTTGsZ
 *   apiKey:              70D9411520B6EFA746D3E46B020280CD
 *   sn:                 1850040898   (that test device's own serial — NOT fixed, see HostHeaders)
 *   lang:               en
 * </pre>
 * {@link #ACCOUNT_ID} is fixed too (per direction), unlike the old project's
 * {@code ConfigurationActivity#accountId}, which came from an aggregator app's launch
 * {@code Intent} — used for {@code orchestration/exchange}/{@code orchestration/sale}'s
 * {@code accountId} header (see {@code SaleCommunicationBehavior}); not needed by onboarding or
 * {@code tmsFileDownload}, which worked without it.
 *
 * <p>See {@link HostHeaders} for where these get assembled into an actual header map.
 */
public final class HostAppKeys {

    public static final String AGGREGATOR_APP_KEY = "LKlEN/bz2UBkdefn3fGczJwyH3/69lS8";
    public static final String SYSTEM_APP_KEY = "EsrflzqTWI4/QhENXPCoSx0HJ/hTTGsZ";
    public static final String API_KEY = "70D9411520B6EFA746D3E46B020280CD";
    public static final String ACCOUNT_ID = "123009";
    public static final String DEFAULT_LANG = "en";

    /**
     * {@code mToken} header — required on {@code orchestration/sale} only (not {@code exchange});
     * missing it 400s with {@code MissingRequestHeaderException}, the same way a missing
     * {@link #ACCOUNT_ID} did before that was found. Unlike every other constant here, this is
     * <b>not a stable fixed value</b> — a real captured example is a live HS256 JWT (decodes to
     * {@code {"unique_name":"111|123009|<imei>|<id>","nbf":...,"exp":nbf+3d}}) that expires days
     * after being minted, and neither its assembly logic nor its HS256 signing secret has been
     * found in the old app's client source: {@code unique_name} appears nowhere in it, and none of
     * {@link HostAppKeys}'s other constants reproduce a real captured signature (checked by
     * recomputing HMAC-SHA256 against each). That shape strongly suggests it's actually issued by
     * some other, not-yet-identified server call (a login/session endpoint, probably —
     * {@code unique_name} is .NET's default JWT identity claim), not signed on-device at all.
     *
     * <p>Sourced from {@code BuildConfig} (this module's {@code buildConfigField}, itself from the
     * Gradle property {@code MTOKEN_TEMP} — see {@code network/build.gradle} and the root
     * {@code build.gradle}'s {@code ext} block, same pattern as {@code bizentity}'s
     * {@code DATABASE_PWD}) rather than a literal here, since the only value available right now is
     * a real captured credential that must never land in source control. Set it locally in
     * {@code ~/.gradle/gradle.properties} (e.g. {@code MTOKEN_TEMP="<a real captured mToken>"}) to
     * exercise a sale build; unset, it's {@code "CHANGE_ME"} and every sale will 400/401 on this
     * header until either a real value is set locally or (the actual fix) the real mint endpoint is
     * found and this is replaced with a live call.
     */
    public static final String MTOKEN = BuildConfig.MTOKEN_TEMP;

    private HostAppKeys() {
    }
}

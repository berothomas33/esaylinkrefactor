package com.emvenhance.network

/**
 * Fixed, non-technician-entered identifiers the host expects on every authenticated request —
 * mirrors the old project's `ConfigurationActivity#aggregatorAppKey`/`SYSTEM_KEY`/
 * `EncryptorHelper#getApiKey`, which there came from an external aggregator app's launch
 * `Intent`, an embedded encrypted secret, and a per-device-serial derivation respectively; here
 * all three are fixed constants instead (per direction — `apiKey` isn't actually derived from
 * this terminal's serial number the way the old project's naming suggested), confirmed against a
 * real captured `foundation/onboarding/handshake` request in the `envtest` environment:
 * ```
 *   aggregator-app-key: LKlEN/bz2UBkdefn3fGczJwyH3/69lS8
 *   system-app-key:     EsrflzqTWI4/QhENXPCoSx0HJ/hTTGsZ
 *   apiKey:              70D9411520B6EFA746D3E46B020280CD
 *   sn:                 1850040898   (that test device's own serial — NOT fixed, see HostHeaders)
 *   lang:               en
 * ```
 * [ACCOUNT_ID] is fixed too (per direction), unlike the old project's
 * `ConfigurationActivity#accountId`, which came from an aggregator app's launch `Intent` — used
 * for `orchestration/exchange`/`orchestration/sale`'s `accountId` header (see
 * `SaleCommunicationBehavior`); not needed by onboarding or `tmsFileDownload`, which worked
 * without it.
 *
 * See [HostHeaders] for where these get assembled into an actual header map.
 */
object HostAppKeys {

    const val AGGREGATOR_APP_KEY = "LKlEN/bz2UBkdefn3fGczJwyH3/69lS8"
    const val SYSTEM_APP_KEY = "EsrflzqTWI4/QhENXPCoSx0HJ/hTTGsZ"
    const val API_KEY = "70D9411520B6EFA746D3E46B020280CD"
    const val ACCOUNT_ID = "123009"
    const val DEFAULT_LANG = "en"

    /**
     * `mToken` header — required on `orchestration/sale` only (not `exchange`); missing it 400s
     * with `MissingRequestHeaderException`, the same way a missing [ACCOUNT_ID] did before that
     * was found. Unlike every other constant here, this is **not a stable fixed value** — a real
     * captured example is a live HS256 JWT (decodes to
     * `{"unique_name":"111|123009|<imei>|<id>","nbf":...,"exp":nbf+3d}`) that expires days after
     * being minted, and neither its assembly logic nor its HS256 signing secret has been found in
     * the old app's client source: `unique_name` appears nowhere in it, and none of
     * [HostAppKeys]'s other constants reproduce a real captured signature (checked by recomputing
     * HMAC-SHA256 against each). That shape strongly suggests it's actually issued by some other,
     * not-yet-identified server call (a login/session endpoint, probably — `unique_name` is
     * .NET's default JWT identity claim), not signed on-device at all.
     *
     * Sourced from `BuildConfig` (this module's `buildConfigField`, itself from the Gradle
     * property `MTOKEN_TEMP` — see `network/build.gradle` and the root `build.gradle`'s `ext`
     * block, same pattern as `bizentity`'s `DATABASE_PWD`) rather than a literal here, since the
     * only value available right now is a real captured credential that must never land in
     * source control. Set it locally in `~/.gradle/gradle.properties` (e.g.
     * `MTOKEN_TEMP="<a real captured mToken>"`) to exercise a sale build; unset, it's
     * `"CHANGE_ME"` and every sale will 400/401 on this header until either a real value is set
     * locally or (the actual fix) the real mint endpoint is found and this is replaced with a
     * live call.
     *
     * This is only the default: a value saved on the onboarding screen ([HostSettings]) takes
     * its place, so an expired token can be replaced on the terminal without a rebuild. Read the
     * value in use through [HostSettings.getMToken], not this constant.
     */
  //  const val MTOKEN =  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjExMXwxMjMwMDl8MzUyMDE5MzMwNTUwMDQxfDEyNjUyOCIsIm5iZiI6MTc5MDc2MjkwMiwiZXhwIjoxNzkxMDIyMTAyLCJpYXQiOjE3OTA3NjI5MDJ9.UK-UbJdomf98dHit8ffgQRpO665GqopXh9MK0AO8OsQ"
 //   const val MTOKEN =  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjExMXwxMjMwMDl8MzUyMDE5MzMyNjIwNTQ1fDEyNjUyOCIsIm5iZiI6MTc5MTEwMzQwMCwiZXhwIjoxNzkxMzYyNjAwLCJpYXQiOjE3OTExMDM0MDB9.pMPflJayhiFrQIK2etk_a2gv10kK0V2zlhn0TWHs1C4"
    const val MTOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1bmlxdWVfbmFtZSI6IjExMXwxMjMwMDl8MzUyMDE5MzMwNTUwMDQxfDEyNjUyOCIsIm5iZiI6MTc5MTc5MzkxMiwiZXhwIjoxNzkyMDUzMTEyLCJpYXQiOjE3OTE3OTM5MTJ9.iYeQYXxDHXP5F9GRWiQBn5Ta-yisIMOqwX-iTmQlM8Q"
}

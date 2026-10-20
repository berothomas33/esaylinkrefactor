package com.emvenhance.network.onboarding

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Tracks whether this terminal has completed each onboarding cycle — the same "run once and
 * remember it" shape `PaxTerminal` already uses for its own `KEY_EMV_CONFIG_INITIALIZED` flag,
 * generalized here since onboarding is vendor-agnostic (plain HTTP against
 * [com.emvenhance.network.HostApiClient]), not PAX-specific.
 *
 * Also persists the offline cycle's identity material (SACS / challenge / public key / service
 * account) — `ConfigurationActivity` reuses the saved challenge again in its later
 * `createToken()` step (signing `accessToken + challenge`), so this needs to survive past the
 * onboarding cycle itself, not just live in [OnboardingClient]'s call chain.
 */
class OnboardingState(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isOfflineOnboarded(): Boolean = prefs.getBoolean(KEY_OFFLINE_ONBOARDED, false)

    fun markOfflineOnboarded() {
        prefs.edit()
            .putBoolean(KEY_OFFLINE_ONBOARDED, true)
            .putBoolean(KEY_TERMINAL_INITIALIZED, false)
            .apply()
    }

    /**
     * Whether the vendor's one-time terminal initialization (EMV parameters loaded from the
     * database) has run since the last onboarding or parameter download. Cleared by
     * [markOfflineOnboarded] and [requireTerminalInitialization]; the next transaction then
     * initializes the terminal and calls [markTerminalInitialized].
     */
    fun isTerminalInitialized(): Boolean = prefs.getBoolean(KEY_TERMINAL_INITIALIZED, false)

    fun markTerminalInitialized() {
        prefs.edit().putBoolean(KEY_TERMINAL_INITIALIZED, true).apply()
    }

    /** The terminal's parameters changed (e.g. a parameter download): initialize it again. */
    fun requireTerminalInitialization() {
        prefs.edit().putBoolean(KEY_TERMINAL_INITIALIZED, false).apply()
    }

    fun isOnlineOnboarded(): Boolean = prefs.getBoolean(KEY_ONLINE_ONBOARDED, false)

    fun markOnlineOnboarded() {
        prefs.edit().putBoolean(KEY_ONLINE_ONBOARDED, true).apply()
    }

    /** Both cycles done — see `EmvEnhanceApp` for where this should gate transactions. */
    fun isFullyOnboarded(): Boolean = isOfflineOnboarded() && isOnlineOnboarded()

    /**
     * Clears both completion flags without touching the saved identity material — mirrors
     * `ConfigurationActivity#resetOnboarding`, called when step 3 (confirm) fails so the next
     * launch re-runs the full offline cycle from the handshake.
     */
    fun resetCompletion() {
        prefs.edit()
            .putBoolean(KEY_OFFLINE_ONBOARDED, false)
            .putBoolean(KEY_ONLINE_ONBOARDED, false)
            .apply()
    }

    fun saveSacs(sacs: String) {
        prefs.edit().putString(KEY_SACS, sacs).apply()
    }

    fun getSacs(): String? = prefs.getString(KEY_SACS, null)

    fun saveChallenge(challenge: String) {
        prefs.edit().putString(KEY_CHALLENGE, challenge).apply()
    }

    fun getChallenge(): String? = prefs.getString(KEY_CHALLENGE, null)

    fun savePublicKey(publicKey: String) {
        prefs.edit().putString(KEY_PUBLIC_KEY, publicKey).apply()
    }

    fun getPublicKey(): String? = prefs.getString(KEY_PUBLIC_KEY, null)

    fun saveServiceAccount(serviceAccount: String) {
        prefs.edit().putString(KEY_SERVICE_ACCOUNT, serviceAccount).apply()
    }

    fun getServiceAccount(): String? = prefs.getString(KEY_SERVICE_ACCOUNT, null)

    /**
     * The bearer access token `OnboardingClient#createToken` fetched — not technician-entered,
     * unlike an earlier version of this class assumed; see that method's doc for where it comes
     * from. `HostHeaders#buildAuthenticated` reads this for every authenticated call once
     * onboarding has completed (EMV param download, sale/exchange).
     */
    fun saveAccessToken(accessToken: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply()
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    /** ISO-local-date-time-ish string, as the server returns it — see [isTokenValid]. */
    fun saveTokenExpiration(expiration: String) {
        prefs.edit().putString(KEY_TOKEN_EXPIRATION, expiration).apply()
    }

    fun getTokenExpiration(): String? = prefs.getString(KEY_TOKEN_EXPIRATION, null)

    /**
     * Whether [getAccessToken] is still safe to use — ports `ConfigurationActivity#isTokenValid()`
     * verbatim (same 30s refresh buffer, same dual-format parse). `false` whenever there's no
     * saved expiration, it fails to parse, or `now` is within [TOKEN_REFRESH_BUFFER_SECONDS] of
     * it — a caller should treat any of those as "go get a fresh token"
     * (`OnboardingClient#ensureValidAccessToken`), not as an error.
     */
    fun isTokenValid(): Boolean {
        val expiration = getTokenExpiration()
        if (expiration == null || expiration.trim().isEmpty()) {
            return false
        }
        return try {
            val expiryInstant = parseTokenExpiration(expiration.trim())
            val refreshAt = expiryInstant.minusSeconds(TOKEN_REFRESH_BUFFER_SECONDS)
            Instant.now().isBefore(refreshAt)
        } catch (e: DateTimeParseException) {
            false
        }
    }

    private companion object {
        const val PREFS_NAME = "onboarding_state"
        const val KEY_OFFLINE_ONBOARDED = "offline_onboarded"
        const val KEY_ONLINE_ONBOARDED = "online_onboarded"
        const val KEY_TERMINAL_INITIALIZED = "terminal_initialized"
        const val KEY_SACS = "sacs"
        const val KEY_CHALLENGE = "challenge"
        const val KEY_PUBLIC_KEY = "public_key"
        const val KEY_SERVICE_ACCOUNT = "service_account"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_TOKEN_EXPIRATION = "token_expiration"

        /**
         * Zone assumed for a bare (no-offset) `expiration` timestamp — matches
         * `ConfigurationActivity#TOKEN_EXPIRATION_ZONE` in the old project exactly (confirmed
         * against its real source, not reconstructed): the host is in Egypt and apparently sends
         * this field as local time with no offset, and the old app hardcoded UTC+3 for it rather
         * than reading the device's zone. Real JWT-expired errors traced back to this class never
         * checking expiration at all (an access token, once saved, was reused forever) — see
         * [isTokenValid], ported to close that gap the same way the old app did.
         */
        val TOKEN_EXPIRATION_ZONE: ZoneOffset = ZoneOffset.ofHours(3)
        const val TOKEN_REFRESH_BUFFER_SECONDS = 30L

        /**
         * Accepts either a proper offset/UTC timestamp (`...Z` or `...+HH:MM`) or a bare local
         * one with no offset at all — the real captured `expiration` field is the latter, so that
         * branch is the one that actually fires; the offset-aware branch is kept only because the
         * old app kept it (defensive, never observed).
         */
        fun parseTokenExpiration(expiration: String): Instant {
            var normalized = expiration
            if (normalized.endsWith("Z")) {
                normalized = normalized.substring(0, normalized.length - 1) + "+00:00"
            }
            return try {
                OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            } catch (ignored: DateTimeParseException) {
                LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .toInstant(TOKEN_EXPIRATION_ZONE)
            }
        }
    }
}

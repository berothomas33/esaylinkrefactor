package com.emvenhance.network.onboarding;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Tracks whether this terminal has completed each onboarding cycle — the same "run once and
 * remember it" shape {@code PaxTerminal} already uses for its own
 * {@code KEY_EMV_CONFIG_INITIALIZED} flag, generalized here since onboarding is vendor-agnostic
 * (plain HTTP against {@link com.emvenhance.network.HostApiClient}), not PAX-specific.
 *
 * <p>Also persists the offline cycle's identity material (SACS / challenge / public key /
 * service account) — {@code ConfigurationActivity} reuses the saved challenge again in its later
 * {@code createToken()} step (signing {@code accessToken + challenge}), so this needs to survive
 * past the onboarding cycle itself, not just live in {@link OnboardingClient}'s call chain.
 */
public final class OnboardingState {

    private static final String PREFS_NAME = "onboarding_state";
    private static final String KEY_OFFLINE_ONBOARDED = "offline_onboarded";
    private static final String KEY_ONLINE_ONBOARDED = "online_onboarded";
    private static final String KEY_SACS = "sacs";
    private static final String KEY_CHALLENGE = "challenge";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_SERVICE_ACCOUNT = "service_account";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_TOKEN_EXPIRATION = "token_expiration";

    private final SharedPreferences prefs;

    public OnboardingState(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isOfflineOnboarded() {
        return prefs.getBoolean(KEY_OFFLINE_ONBOARDED, false);
    }

    public void markOfflineOnboarded() {
        prefs.edit().putBoolean(KEY_OFFLINE_ONBOARDED, true).apply();
    }

    public boolean isOnlineOnboarded() {
        return prefs.getBoolean(KEY_ONLINE_ONBOARDED, false);
    }

    public void markOnlineOnboarded() {
        prefs.edit().putBoolean(KEY_ONLINE_ONBOARDED, true).apply();
    }

    /** Both cycles done — see {@code EmvEnhanceApp} for where this should gate transactions. */
    public boolean isFullyOnboarded() {
        return isOfflineOnboarded() && isOnlineOnboarded();
    }

    /**
     * Clears both completion flags without touching the saved identity material — mirrors
     * {@code ConfigurationActivity#resetOnboarding}, called when step 3 (confirm) fails so the
     * next launch re-runs the full offline cycle from the handshake.
     */
    public void resetCompletion() {
        prefs.edit()
                .putBoolean(KEY_OFFLINE_ONBOARDED, false)
                .putBoolean(KEY_ONLINE_ONBOARDED, false)
                .apply();
    }

    public void saveSacs(String sacs) {
        prefs.edit().putString(KEY_SACS, sacs).apply();
    }

    @Nullable
    public String getSacs() {
        return prefs.getString(KEY_SACS, null);
    }

    public void saveChallenge(String challenge) {
        prefs.edit().putString(KEY_CHALLENGE, challenge).apply();
    }

    @Nullable
    public String getChallenge() {
        return prefs.getString(KEY_CHALLENGE, null);
    }

    public void savePublicKey(String publicKey) {
        prefs.edit().putString(KEY_PUBLIC_KEY, publicKey).apply();
    }

    @Nullable
    public String getPublicKey() {
        return prefs.getString(KEY_PUBLIC_KEY, null);
    }

    public void saveServiceAccount(String serviceAccount) {
        prefs.edit().putString(KEY_SERVICE_ACCOUNT, serviceAccount).apply();
    }

    @Nullable
    public String getServiceAccount() {
        return prefs.getString(KEY_SERVICE_ACCOUNT, null);
    }

    /**
     * The bearer access token {@code OnboardingClient#createToken} fetched — not technician-entered,
     * unlike an earlier version of this class assumed; see that method's javadoc for where it
     * comes from. {@code HostHeaders#buildAuthenticated} reads this for every authenticated call
     * once onboarding has completed (EMV param download, sale/exchange).
     */
    public void saveAccessToken(String accessToken) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }

    @Nullable
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    /** ISO-local-date-time-ish string, as the server returns it — not parsed/validated here yet. */
    public void saveTokenExpiration(String expiration) {
        prefs.edit().putString(KEY_TOKEN_EXPIRATION, expiration).apply();
    }

    @Nullable
    public String getTokenExpiration() {
        return prefs.getString(KEY_TOKEN_EXPIRATION, null);
    }
}

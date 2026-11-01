package com.emvenhance.network.onboarding;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import androidx.annotation.Nullable;

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

    private static final ZoneOffset TOKEN_EXPIRATION_ZONE = ZoneOffset.ofHours(3);
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 30;

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

    public boolean isFullyOnboarded() {
        return isOfflineOnboarded() && isOnlineOnboarded();
    }

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

    public void saveAccessToken(String accessToken) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, accessToken).apply();
    }

    @Nullable
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public void saveTokenExpiration(String expiration) {
        prefs.edit().putString(KEY_TOKEN_EXPIRATION, expiration).apply();
    }

    @Nullable
    public String getTokenExpiration() {
        return prefs.getString(KEY_TOKEN_EXPIRATION, null);
    }

    public boolean isTokenValid() {
        String expiration = getTokenExpiration();
        if (expiration == null || expiration.trim().isEmpty()) {
            return false;
        }
        try {
            Instant expiryInstant = parseTokenExpiration(expiration.trim());
            Instant refreshAt = expiryInstant.minusSeconds(TOKEN_REFRESH_BUFFER_SECONDS);
            return Instant.now().isBefore(refreshAt);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static Instant parseTokenExpiration(String expiration) {
        String normalized = expiration;
        if (normalized.endsWith("Z")) {
            normalized = normalized.substring(0, normalized.length() - 1) + "+00:00";
        }
        try {
            return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return localDateTime.toInstant(TOKEN_EXPIRATION_ZONE);
        }
    }
}

package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * {@code authorization/token/pos}'s {@code data} — field names confirmed against a real captured
 * response (not reconstructed from a call site like most of this module's other models).
 *
 * <p>{@link #getSignature()} is meant to be verified as {@code HMAC-SHA256(accessToken + challenge)}
 * — see {@code ConfigurationActivity#createToken} — but the key isn't the SACS (that's confirm's
 * key, already consumed), and none of this app's now-known fixed constants (aggregator-app-key,
 * system-app-key, apiKey) reproduce the real captured signature either (checked). Not verified
 * here — see {@code OnboardingClient#createToken}'s javadoc.
 */
public final class CreateTokenResult {

    @SerializedName("userId")
    private int userId;

    @SerializedName("accessToken")
    @Nullable
    private String accessToken;

    @SerializedName("signature")
    @Nullable
    private String signature;

    @SerializedName("expiration")
    @Nullable
    private String expiration;

    @SerializedName("serialNumber")
    @Nullable
    private String serialNumber;

    public int getUserId() {
        return userId;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    @Nullable
    public String getSignature() {
        return signature;
    }

    @Nullable
    public String getExpiration() {
        return expiration;
    }

    @Nullable
    public String getSerialNumber() {
        return serialNumber;
    }
}

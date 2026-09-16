package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * The host's identity, returned by step 1 of the offline onboarding cycle — mirrors the old
 * project's {@code OnboardingModel} (renamed here to avoid clashing with the "model" package
 * name).
 */
public final class OnboardingResult {

    @SerializedName("serviceAccount")
    @Nullable
    private String serviceAccount;

    @SerializedName("publicKey")
    @Nullable
    private String publicKey;

    @SerializedName("signature")
    @Nullable
    private String signature;

    @Nullable
    public String getServiceAccount() {
        return serviceAccount;
    }

    @Nullable
    public String getPublicKey() {
        return publicKey;
    }

    @Nullable
    public String getSignature() {
        return signature;
    }
}

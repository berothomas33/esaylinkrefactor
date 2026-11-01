package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

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

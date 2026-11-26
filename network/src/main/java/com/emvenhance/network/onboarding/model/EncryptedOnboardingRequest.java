package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/** RKI onboarding and its confirm: the {@link OnboardingIdentity} under the APP_TDK. */
public final class EncryptedOnboardingRequest {

    @SerializedName("encryptedRequest")
    private final String encryptedRequest;

    public EncryptedOnboardingRequest(String encryptedRequest) {
        this.encryptedRequest = encryptedRequest;
    }

    public String getEncryptedRequest() {
        return encryptedRequest;
    }
}

package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/** {@code foundation/onboarding/offline} request — mirrors the old project's OnboardingRequest. */
public final class OnboardingRequest {

    @SerializedName("challenge")
    private final String challenge;

    public OnboardingRequest(String challenge) {
        this.challenge = challenge;
    }

    public String getChallenge() {
        return challenge;
    }
}

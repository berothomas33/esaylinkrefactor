package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

public final class ConfirmOnboardingRequest {

    @SerializedName("challenge")
    private final String challenge;

    public ConfirmOnboardingRequest(String challenge) {
        this.challenge = challenge;
    }

    public String getChallenge() {
        return challenge;
    }
}

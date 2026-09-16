package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/** {@code foundation/onboarding/confirm} request — same challenge sent in step 1. */
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

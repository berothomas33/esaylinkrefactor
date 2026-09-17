package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/**
 * {@code authorization/token/pos} request — confirmed against a real captured onboarding run:
 * {@code {"username": "<serviceAccount from the onboard step's response>"}}.
 */
public final class CreateTokenRequest {

    @SerializedName("username")
    private final String username;

    public CreateTokenRequest(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

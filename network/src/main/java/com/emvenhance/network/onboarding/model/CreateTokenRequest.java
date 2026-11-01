package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

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

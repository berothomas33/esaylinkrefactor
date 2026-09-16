package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/** {@code foundation/onboarding/offline} response — mirrors the old project's OnboardingResponse. */
public final class OnboardingResponse {

    @SerializedName("statusCode")
    private int statusCode;

    @SerializedName("message")
    @Nullable
    private String message;

    @SerializedName("data")
    @Nullable
    private OnboardingResult data;

    public int getStatusCode() {
        return statusCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public OnboardingResult getData() {
        return data;
    }
}

package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Generic encrypted-envelope response shape — mirrors the old project's {@code GeneralResponse}.
 * {@link #getData()} carries a {@link DataModel} whose {@code encSerializedResponse} needs
 * decrypting (via the host's {@code crypto/{algorithm}/decrypt} endpoint or equivalent local key
 * material) before it can be parsed into whatever payload the call actually returned — that
 * decryption step wasn't present in the reference model layer this was ported from, so it isn't
 * implemented here; see {@code OnboardingClient#onboardOnline}.
 */
public final class GeneralResponse {

    @SerializedName("statusCode")
    private int statusCode;

    @SerializedName("message")
    @Nullable
    private String message;

    @SerializedName("data")
    @Nullable
    private DataModel data;

    public int getStatusCode() {
        return statusCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public DataModel getData() {
        return data;
    }
}

package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Shared {@code {statusCode, message, data: String}} shape — the old project defined this same
 * shape three times over (its own {@code HandshakeResponse}, {@code ConfirmOnboardingResponse},
 * {@code HandshakeOnboardingOnlineResponse}), and its {@code confirm-online} endpoint already
 * returned the offline {@code ConfirmOnboardingResponse} type rather than the separate
 * {@code ConfirmOnboardingOnlineResponse} it also defined — so one class here covers all four:
 * the offline handshake, the offline confirm, the online handshake, and the online confirm.
 */
public final class OnboardingStatusResponse {

    @SerializedName("statusCode")
    private int statusCode;

    @SerializedName("message")
    @Nullable
    private String message;

    @SerializedName("data")
    @Nullable
    private String data;

    public int getStatusCode() {
        return statusCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public String getData() {
        return data;
    }
}

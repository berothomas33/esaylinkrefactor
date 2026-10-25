package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class CreateTokenResult {

    @SerializedName("userId")
    private int userId;

    @SerializedName("accessToken")
    @Nullable
    private String accessToken;

    @SerializedName("signature")
    @Nullable
    private String signature;

    @SerializedName("expiration")
    @Nullable
    private String expiration;

    @SerializedName("serialNumber")
    @Nullable
    private String serialNumber;

    public int getUserId() {
        return userId;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    @Nullable
    public String getSignature() {
        return signature;
    }

    @Nullable
    public String getExpiration() {
        return expiration;
    }

    @Nullable
    public String getSerialNumber() {
        return serialNumber;
    }
}

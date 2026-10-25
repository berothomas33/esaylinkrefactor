package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class PurchaseResponse {

    @SerializedName("responseCode")
    private Integer responseCode;

    @SerializedName("authCode")
    @Nullable
    private String authCode;

    @SerializedName("responseMessage")
    @Nullable
    private String responseMessage;

    @SerializedName("chipData")
    @Nullable
    private String chipData;

    @SerializedName("referenceNumber")
    @Nullable
    private String referenceNumber;

    public Integer getResponseCode() {
        return responseCode;
    }

    @Nullable
    public String getAuthCode() {
        return authCode;
    }

    @Nullable
    public String getResponseMessage() {
        return responseMessage;
    }

    @Nullable
    public String getChipData() {
        return chipData;
    }

    @Nullable
    public String getReferenceNumber() {
        return referenceNumber;
    }
}

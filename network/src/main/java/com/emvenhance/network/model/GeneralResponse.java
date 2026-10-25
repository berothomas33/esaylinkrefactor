package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

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

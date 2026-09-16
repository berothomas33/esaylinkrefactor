package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/** The encrypted envelope carried by {@link GeneralResponse#getData()}. */
public final class DataModel {

    @SerializedName("encSerializedResponse")
    @Nullable
    private String encSerializedResponse;

    @SerializedName("transactionType")
    @Nullable
    private String transactionType;

    @Nullable
    public String getEncSerializedResponse() {
        return encSerializedResponse;
    }

    @Nullable
    public String getTransactionType() {
        return transactionType;
    }
}

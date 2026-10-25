package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class GeneralRequest {

    @SerializedName("encSerializedRequest")
    private final String encSerializedRequest;

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    @SerializedName("isRetry")
    private final int isRetry;

    @SerializedName("transactionKey")
    @Nullable
    private final String transactionKeyEncrypted;

    @SerializedName("pinKey")
    @Nullable
    private final String pinKeyEncrypted;

    @SerializedName("encPinBlock")
    @Nullable
    private final String encPinBlock;

    @SerializedName("transactionType")
    @Nullable
    private final String transactionType;

    private GeneralRequest(String encSerializedRequest, String asyncRequestId, int isRetry,
            @Nullable String transactionKeyEncrypted, @Nullable String pinKeyEncrypted,
            @Nullable String encPinBlock, @Nullable String transactionType) {
        this.encSerializedRequest = encSerializedRequest;
        this.asyncRequestId = asyncRequestId;
        this.isRetry = isRetry;
        this.transactionKeyEncrypted = transactionKeyEncrypted;
        this.pinKeyEncrypted = pinKeyEncrypted;
        this.encPinBlock = encPinBlock;
        this.transactionType = transactionType;
    }

    public static GeneralRequest forExchange(String encSerializedRequest, String asyncRequestId,
            String transactionKeyEncrypted, @Nullable String pinKeyEncrypted, String transactionType) {
        return new GeneralRequest(encSerializedRequest, asyncRequestId, 0,
                transactionKeyEncrypted, pinKeyEncrypted, null, transactionType);
    }

    public static GeneralRequest forSale(String encSerializedRequest, String asyncRequestId,
            @Nullable String encPinBlock) {
        return new GeneralRequest(encSerializedRequest, asyncRequestId, 0, null, null, encPinBlock, null);
    }

    public String getEncSerializedRequest() {
        return encSerializedRequest;
    }

    public String getAsyncRequestId() {
        return asyncRequestId;
    }

    public int getIsRetry() {
        return isRetry;
    }

    @Nullable
    public String getTransactionKeyEncrypted() {
        return transactionKeyEncrypted;
    }

    @Nullable
    public String getPinKeyEncrypted() {
        return pinKeyEncrypted;
    }

    @Nullable
    public String getEncPinBlock() {
        return encPinBlock;
    }

    @Nullable
    public String getTransactionType() {
        return transactionType;
    }
}

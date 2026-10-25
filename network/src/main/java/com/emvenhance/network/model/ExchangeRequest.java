package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

public final class ExchangeRequest {

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    @SerializedName("pan")
    private final String pan;

    @SerializedName("cvm")
    private final int cvm;

    @SerializedName("transactionType")
    private final String transactionType;

    public ExchangeRequest(String asyncRequestId, String pan, int cvm, String transactionType) {
        this.asyncRequestId = asyncRequestId;
        this.pan = pan;
        this.cvm = cvm;
        this.transactionType = transactionType;
    }

    public String getAsyncRequestId() {
        return asyncRequestId;
    }

    public String getPan() {
        return pan;
    }

    public int getCvm() {
        return cvm;
    }

    public String getTransactionType() {
        return transactionType;
    }
}

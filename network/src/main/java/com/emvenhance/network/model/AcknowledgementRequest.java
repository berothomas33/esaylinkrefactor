package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

public final class AcknowledgementRequest {

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    @SerializedName("transactionType")
    private final String transactionType;

    public AcknowledgementRequest(String asyncRequestId, String transactionType) {
        this.asyncRequestId = asyncRequestId;
        this.transactionType = transactionType;
    }

    public String getAsyncRequestId() {
        return asyncRequestId;
    }

    public String getTransactionType() {
        return transactionType;
    }
}

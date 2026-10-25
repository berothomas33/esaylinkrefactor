package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * {@code orchestration/reverse}'s body — the old project's {@code ReverseRequest}, field for
 * field: plain JSON, no envelope. Names the request to undo by the {@code asyncRequestId} its
 * exchange and sale/refund went with.
 */
public final class ReverseRequest {

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    /** {@code "PURCHASE"} or {@code "REFUND"}, as the exchange sent it. */
    @SerializedName("transactionType")
    private final String transactionType;

    public ReverseRequest(String asyncRequestId, String transactionType) {
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

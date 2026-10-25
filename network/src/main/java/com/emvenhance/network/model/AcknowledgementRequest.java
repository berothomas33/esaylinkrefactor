package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * {@code orchestration/acknowledgement}'s body — the old project's {@code AcknowledgementRequest},
 * field for field: plain JSON, no envelope. Names the completed request by the
 * {@code asyncRequestId} its exchange and sale/refund went with.
 */
public final class AcknowledgementRequest {

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    /** {@code "PURCHASE"} or {@code "REFUND"}, as the exchange sent it. */
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

package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * Payload carried (AES-encrypted) inside {@link GeneralRequest#forExchange}'s
 * {@code encSerializedRequest} — field names confirmed against the real {@code ExchangeRequest}
 * class (found in an uploaded {@code model_layer.rar}): its
 * {@code ExchangeRequest(asyncRequestId, pan, cvm, transactionType)} constructor is exactly the
 * shape {@code SaleActivity#prepareExchangeRequest} calls. An earlier version of this class used
 * {@code asyncId}/{@code type} for these same two fields, guessed before the real source was
 * found — wrong names, now corrected.
 */
public final class ExchangeRequest {

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    @SerializedName("pan")
    private final String pan;

    /** CVM used for this transaction — see {@code SaleCommunicationBehavior#cvmCode}. */
    @SerializedName("cvm")
    private final int cvm;

    /** {@code "PURCHASE"} for a sale — see {@code SaleCommunicationBehavior#TRANSACTION_TYPE_SALE}. */
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

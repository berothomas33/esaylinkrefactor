package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class RefundRequest {

    @SerializedName("referenceNumber")
    private final String referenceNumber;

    @SerializedName("amount")
    private final double amount;

    @SerializedName("cvm")
    private final int cvm;

    @SerializedName("pan")
    private final String pan;

    @Nullable
    @SerializedName("pinBlock")
    private final String pinBlock;

    @SerializedName("chipData")
    private final String chipData;

    @SerializedName("expirationMonth")
    private final int expirationMonth;

    @SerializedName("expirationYear")
    private final int expirationYear;

    @SerializedName("posEntryMode")
    private final String posEntryMode;

    @SerializedName("cardType")
    private final String cardType;

    @SerializedName("trailer")
    private final String trailer;

    @SerializedName("dcc")
    private final String dcc;

    @Nullable
    @SerializedName("transactionId")
    private final String transactionId;

    @SerializedName("extraData")
    private final String extraData;

    public RefundRequest(String referenceNumber, double amount, int cvm, String pan,
            @Nullable String pinBlock, String chipData, int expirationMonth, int expirationYear,
            String posEntryMode, String cardType, String trailer, String dcc,
            @Nullable String transactionId, String extraData) {
        this.referenceNumber = referenceNumber;
        this.amount = Math.round(amount * 100) / 100.0;
        this.cvm = cvm;
        this.pan = pan;
        this.pinBlock = pinBlock;
        this.chipData = chipData;
        this.expirationMonth = expirationMonth;
        this.expirationYear = expirationYear;
        this.posEntryMode = posEntryMode;
        this.cardType = cardType;
        this.trailer = trailer;
        this.dcc = dcc;
        this.transactionId = transactionId;
        this.extraData = extraData;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public double getAmount() {
        return amount;
    }
}

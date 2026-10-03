package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Payload carried (AES-encrypted) inside {@code orchestration/refund}'s {@link GeneralRequest} —
 * same field names as the old project's {@code model_layer.data.refund.RefundRequest}. Mostly
 * {@link SaleRequest}'s card fields, plus {@link #referenceNumber} (the original sale's reference
 * number) and {@link #transactionId}, and without {@code netAmount}/{@code singleTap}.
 *
 * <p>{@link #transactionId} is sent as {@code null}: the old refund screen that filled it wasn't
 * shared, so what it should carry (the original sale's {@code trxRefNumber}?) isn't confirmed.
 * {@code cardType}/{@code dcc} carry the same placeholders as {@link SaleRequest}'s.
 */
public final class RefundRequest {

    @SerializedName("referenceNumber")
    private final String referenceNumber;

    /** Major currency units (e.g. {@code 10.50}), like {@link SaleRequest}'s amount. */
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

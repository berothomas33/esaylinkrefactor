package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class SaleRequest {

    @SerializedName("amount")
    private final double amount;

    @SerializedName("netAmount")
    private final double netAmount;

    @SerializedName("cvm")
    private final int cvm;

    @SerializedName("pan")
    private final String pan;

    @SerializedName("pinBlock")
    @Nullable
    private final String pinBlock;

    @SerializedName("chipData")
    @Nullable
    private final String chipData;

    @SerializedName("expirationMonth")
    private final int expirationMonth;

    @SerializedName("expirationYear")
    private final int expirationYear;

    @SerializedName("trailer")
    private final String trailer;

    @SerializedName("posEntryMode")
    private final String posEntryMode;

    @SerializedName("cardType")
    @Nullable
    private final String cardType;

    @SerializedName("dcc")
    private final String dcc;

    @SerializedName("singleTap")
    @Nullable
    private final String singleTap;

    @SerializedName("extraData")
    private final String extraData;

    public SaleRequest(double amount, double netAmount, int cvm, String pan, @Nullable String pinBlock,
            @Nullable String chipData, int expirationMonth, int expirationYear, String trailer,
            String posEntryMode, @Nullable String cardType, String dcc, String extraData) {
        this.amount = amount;
        this.netAmount = netAmount;
        this.cvm = cvm;
        this.pan = pan;
        this.pinBlock = pinBlock;
        this.chipData = chipData;
        this.expirationMonth = expirationMonth;
        this.expirationYear = expirationYear;
        this.trailer = trailer;
        this.posEntryMode = posEntryMode;
        this.cardType = cardType;
        this.dcc = dcc;
        this.singleTap = null;
        this.extraData = extraData;
    }

    public double getAmount() {
        return amount;
    }

    public double getNetAmount() {
        return netAmount;
    }

    public int getCvm() {
        return cvm;
    }

    public String getPan() {
        return pan;
    }

    @Nullable
    public String getPinBlock() {
        return pinBlock;
    }

    @Nullable
    public String getChipData() {
        return chipData;
    }

    public int getExpirationMonth() {
        return expirationMonth;
    }

    public int getExpirationYear() {
        return expirationYear;
    }

    public String getTrailer() {
        return trailer;
    }

    public String getPosEntryMode() {
        return posEntryMode;
    }

    @Nullable
    public String getCardType() {
        return cardType;
    }

    public String getDcc() {
        return dcc;
    }

    @Nullable
    public String getSingleTap() {
        return singleTap;
    }

    public String getExtraData() {
        return extraData;
    }
}

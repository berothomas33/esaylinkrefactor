package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

public final class PurchaseRequest {

    @SerializedName("amount")
    private final long amount;

    @SerializedName("procCode")
    private final String procCode;

    @SerializedName("posEntryMode")
    private final String posEntryMode;

    @SerializedName("chipData")
    @Nullable
    private final String chipData;

    @SerializedName("pan")
    @Nullable
    private final String pan;

    @SerializedName("pinBlock")
    @Nullable
    private final String pinBlock;

    @SerializedName("pinKey")
    @Nullable
    private final String pinKeyEncrypted;

    public PurchaseRequest(long amount, String procCode, String posEntryMode,
            @Nullable String chipData, @Nullable String pan, @Nullable String pinBlock,
            @Nullable String pinKeyEncrypted) {
        this.amount = amount;
        this.procCode = procCode;
        this.posEntryMode = posEntryMode;
        this.chipData = chipData;
        this.pan = pan;
        this.pinBlock = pinBlock;
        this.pinKeyEncrypted = pinKeyEncrypted;
    }

    public long getAmount() {
        return amount;
    }

    public String getProcCode() {
        return procCode;
    }

    public String getPosEntryMode() {
        return posEntryMode;
    }

    @Nullable
    public String getChipData() {
        return chipData;
    }

    @Nullable
    public String getPan() {
        return pan;
    }

    @Nullable
    public String getPinBlock() {
        return pinBlock;
    }

    @Nullable
    public String getPinKeyEncrypted() {
        return pinKeyEncrypted;
    }
}

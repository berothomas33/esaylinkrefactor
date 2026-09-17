package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Serialized (as JSON, into {@link SaleRequest#getExtraData()}) supplementary EMV data — mirrors
 * the old project's {@code SaleExtraData}, reconstructed from its setter calls in
 * {@code SaleActivity#getSaleRequest}.
 */
public final class SaleExtraData {

    @SerializedName("aid")
    private final String aid;

    @SerializedName("appName")
    private final String appName;

    @SerializedName("issuerName")
    @Nullable
    private final String issuerName;

    @SerializedName("pinEnterMode")
    private final String pinEnterMode;

    @SerializedName("cardHolderName")
    @Nullable
    private final String cardHolderName;

    @SerializedName("cardType")
    private final String cardType;

    public SaleExtraData(String aid, String appName, @Nullable String issuerName,
            String pinEnterMode, @Nullable String cardHolderName, String cardType) {
        this.aid = aid;
        this.appName = appName;
        this.issuerName = issuerName;
        this.pinEnterMode = pinEnterMode;
        this.cardHolderName = cardHolderName;
        this.cardType = cardType;
    }

    public String getAid() {
        return aid;
    }

    public String getAppName() {
        return appName;
    }

    @Nullable
    public String getIssuerName() {
        return issuerName;
    }

    public String getPinEnterMode() {
        return pinEnterMode;
    }

    @Nullable
    public String getCardHolderName() {
        return cardHolderName;
    }

    public String getCardType() {
        return cardType;
    }
}

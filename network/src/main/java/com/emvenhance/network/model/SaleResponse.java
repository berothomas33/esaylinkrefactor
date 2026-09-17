package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Decrypted payload of the sale call's response — mirrors the old project's {@code SaleResponse},
 * reconstructed from its getters referenced in {@code SaleActivity} ({@code fillMomknPayResponse}
 * and the {@code getResponseCode() == 0} success check; the class itself wasn't shared). Receipt-
 * only fields the old project also read here (merchant name/address, terminal id, ...) are kept
 * since {@code SaleCommunicationBehavior} passes them straight through — this module doesn't print
 * receipts itself.
 */
public final class SaleResponse {

    /** {@code 0} on success — anything else is a decline/error, see {@code SaleCommunicationBehavior}. */
    @SerializedName("responseCode")
    private int responseCode;

    @SerializedName("responseMessage")
    @Nullable
    private String responseMessage;

    @SerializedName("authCode")
    @Nullable
    private String authCode;

    /** Bundles issuer EMV tags (91/71/72) the same way {@link PurchaseResponse#getChipData()} does. */
    @SerializedName("chipData")
    @Nullable
    private String chipData;

    @SerializedName("authDate")
    @Nullable
    private String authDate;

    @SerializedName("authTime")
    @Nullable
    private String authTime;

    @SerializedName("nii")
    @Nullable
    private String nii;

    @SerializedName("referenceNumber")
    @Nullable
    private String referenceNumber;

    @SerializedName("batchId")
    @Nullable
    private String batchId;

    @SerializedName("trxRefNumber")
    @Nullable
    private String trxRefNumber;

    @SerializedName("merchantName")
    @Nullable
    private String merchantName;

    @SerializedName("merchantId")
    @Nullable
    private String merchantId;

    @SerializedName("merchantAddressLine1")
    @Nullable
    private String merchantAddressLine1;

    @SerializedName("merchantAddressLine2")
    @Nullable
    private String merchantAddressLine2;

    @SerializedName("maskedPan")
    @Nullable
    private String maskedPan;

    @SerializedName("amount")
    @Nullable
    private String amount;

    @SerializedName("terminalId")
    @Nullable
    private String terminalId;

    @SerializedName("trailer")
    @Nullable
    private String trailer;

    @SerializedName("targetBank")
    @Nullable
    private String targetBank;

    public int getResponseCode() {
        return responseCode;
    }

    @Nullable
    public String getResponseMessage() {
        return responseMessage;
    }

    @Nullable
    public String getAuthCode() {
        return authCode;
    }

    @Nullable
    public String getChipData() {
        return chipData;
    }

    @Nullable
    public String getAuthDate() {
        return authDate;
    }

    @Nullable
    public String getAuthTime() {
        return authTime;
    }

    @Nullable
    public String getNii() {
        return nii;
    }

    @Nullable
    public String getReferenceNumber() {
        return referenceNumber;
    }

    @Nullable
    public String getBatchId() {
        return batchId;
    }

    @Nullable
    public String getTrxRefNumber() {
        return trxRefNumber;
    }

    @Nullable
    public String getMerchantName() {
        return merchantName;
    }

    @Nullable
    public String getMerchantId() {
        return merchantId;
    }

    @Nullable
    public String getMerchantAddressLine1() {
        return merchantAddressLine1;
    }

    @Nullable
    public String getMerchantAddressLine2() {
        return merchantAddressLine2;
    }

    @Nullable
    public String getMaskedPan() {
        return maskedPan;
    }

    @Nullable
    public String getAmount() {
        return amount;
    }

    @Nullable
    public String getTerminalId() {
        return terminalId;
    }

    @Nullable
    public String getTrailer() {
        return trailer;
    }

    @Nullable
    public String getTargetBank() {
        return targetBank;
    }
}

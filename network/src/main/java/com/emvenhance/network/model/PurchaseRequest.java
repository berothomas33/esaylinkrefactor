package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Mirrors the old project's {@code EmvPurchaseRequest} (crypto/purchase), trimmed to the fields
 * this app actually has available at {@code START_ONLINE_PROCESS} — see
 * {@code TransactionConfig}. The old project's onboarding/token/report/void/refund/reverse
 * endpoints are out of scope here; this module only covers online purchase authorization.
 */
public final class PurchaseRequest {

    @SerializedName("amount")
    private final long amount;

    @SerializedName("procCode")
    private final String procCode;

    @SerializedName("posEntryMode")
    private final String posEntryMode;

    /** Field 55 (ICC System Related Data), hex-encoded BER-TLV — see {@code TransactionConfig#getIccData}. */
    @SerializedName("chipData")
    @Nullable
    private final String chipData;

    @SerializedName("pan")
    @Nullable
    private final String pan;

    /** Online PIN block, hex-encoded — {@code null} when no online PIN was collected. */
    @SerializedName("pinBlock")
    @Nullable
    private final String pinBlock;

    /**
     * The PIN key that encrypted {@link #pinBlock}, RSA-wrapped with the host's public key
     * (base64) — see {@code RsaPublicKeyEncryptor}. {@code null} whenever {@link #pinBlock} is.
     */
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

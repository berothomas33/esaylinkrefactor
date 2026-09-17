package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Payload carried (AES-encrypted) inside {@link GeneralRequest#forSale}'s
 * {@code encSerializedRequest} — mirrors the old project's {@code SaleRequest}, reconstructed
 * from its setter calls in {@code SaleActivity#getSaleRequest} (the class itself wasn't shared).
 *
 * <p>Two fields are placeholders pending the real per-issuer tables, same disclosure as
 * {@code RsaPublicKeyEncryptor}'s key-encoding assumption:
 * <ul>
 *   <li>{@link #getCardType()} — the old project derived this from a {@code getIssueName(issuerName)}
 *       lookup (issuer/network → numeric or short code) that wasn't shared; this carries the raw
 *       issuer name instead of guessing at that table.
 *   <li>{@link #getDcc()} — the old project derived this from a {@code getDCC(issuerName)} lookup
 *       (which issuers are DCC-eligible) that also wasn't shared; always {@code "false"} here.
 * </ul>
 * Confirm both against the server team before relying on this for a live transaction.
 */
public final class SaleRequest {

    @SerializedName("amount")
    private final long amount;

    @SerializedName("cvm")
    private final int cvm;

    @SerializedName("pan")
    private final String pan;

    @SerializedName("pinBlock")
    @Nullable
    private final String pinBlock;

    /** Field 55 (ICC System Related Data), hex-encoded BER-TLV — see {@code TransactionConfig#getIccData}. */
    @SerializedName("chipData")
    @Nullable
    private final String chipData;

    @SerializedName("expirationMonth")
    private final int expirationMonth;

    @SerializedName("expirationYear")
    private final int expirationYear;

    /** Track 2 discretionary data + separators, past the PAN/expiry — {@code ""} when unavailable. */
    @SerializedName("trailer")
    private final String trailer;

    @SerializedName("posEntryMode")
    private final String posEntryMode;

    /** See the class javadoc's DCC/cardType caveat — issuer name, not a numeric code. */
    @SerializedName("cardType")
    @Nullable
    private final String cardType;

    @SerializedName("dcc")
    private final String dcc;

    /** {@link SaleExtraData}, JSON-serialized — see {@code SaleCommunicationBehavior}. */
    @SerializedName("extraData")
    private final String extraData;

    public SaleRequest(long amount, int cvm, String pan, @Nullable String pinBlock,
            @Nullable String chipData, int expirationMonth, int expirationYear, String trailer,
            String posEntryMode, @Nullable String cardType, String dcc, String extraData) {
        this.amount = amount;
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
        this.extraData = extraData;
    }

    public long getAmount() {
        return amount;
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

    public String getExtraData() {
        return extraData;
    }
}

package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Payload carried (AES-encrypted) inside {@link GeneralRequest#forSale}'s
 * {@code encSerializedRequest} — field set confirmed against the real {@code SaleRequest} class
 * (found in an uploaded {@code model_layer.rar}), which also revealed two things an earlier,
 * reconstructed-from-setter-calls version of this class had wrong:
 * <ul>
 *   <li>{@link #getAmount()}/{@link #getNetAmount()} are {@code double} major-currency-unit
 *       values (e.g. {@code 10.50}), not minor-unit integers — the real class even rounds
 *       {@code amount} to 2 decimal places on the way out. An earlier version sent
 *       {@code TransactionConfig#getAmountMinor()} (cents) directly as a {@code long}.
 *   <li>{@link #getNetAmount()} and {@link #getSingleTap()} weren't in the reconstructed version
 *       at all.
 * </ul>
 * {@link #getNetAmount()} presumably differs from {@link #getAmount()} when a surcharge/fee
 * applies — this codebase has no such concept, so both carry the same value.
 * {@link #getSingleTap()} is the Mastercard-CLSS single-tap retry marker (old project:
 * {@code "ST"}) — out of scope here (see {@code SaleCommunicationBehavior}'s javadoc), always
 * {@code null}.
 *
 * <p>Two fields are still placeholders pending the real per-issuer tables, same disclosure as
 * {@code RsaPublicKeyEncryptor}'s key-encoding assumption before it was confirmed:
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

    /** Mastercard-CLSS single-tap retry marker — see the class javadoc; always {@code null} here. */
    @SerializedName("singleTap")
    @Nullable
    private final String singleTap;

    /** {@link SaleExtraData}, JSON-serialized — see {@code SaleCommunicationBehavior}. */
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

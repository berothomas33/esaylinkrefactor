package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Mirrors the old project's {@code EmvPurchaseResponse}. {@code authCode}/{@code responseCode}
 * arrive as their own JSON fields; the issuer's EMV tags (ARPC / issuer scripts) come bundled
 * inside {@code chipData} as a hex BER-TLV blob — see {@link com.emvenhance.network.tlv.BerTlv}
 * and {@link com.emvenhance.network.RetrofitCommunicationBehavior} for how they're pulled out.
 * This is the one part of the old project's wire format that isn't fully documented from static
 * analysis alone — the exact set of tags a given issuer bundles into {@code chipData} may need
 * adjusting once this is exercised against a real response.
 */
public final class PurchaseResponse {

    @SerializedName("responseCode")
    private Integer responseCode;

    @SerializedName("authCode")
    @Nullable
    private String authCode;

    @SerializedName("responseMessage")
    @Nullable
    private String responseMessage;

    /** Hex-encoded BER-TLV bundling whatever EMV tags the issuer returned (91 / 71 / 72 / ...). */
    @SerializedName("chipData")
    @Nullable
    private String chipData;

    @SerializedName("referenceNumber")
    @Nullable
    private String referenceNumber;

    public Integer getResponseCode() {
        return responseCode;
    }

    @Nullable
    public String getAuthCode() {
        return authCode;
    }

    @Nullable
    public String getResponseMessage() {
        return responseMessage;
    }

    @Nullable
    public String getChipData() {
        return chipData;
    }

    @Nullable
    public String getReferenceNumber() {
        return referenceNumber;
    }
}

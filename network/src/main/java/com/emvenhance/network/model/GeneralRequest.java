package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Generic encrypted-envelope request shape — the request-side counterpart to
 * {@link GeneralResponse}, mirrors the old project's {@code GeneralRequest} as used by
 * {@code cacore/exchange} and {@code cacore/sale} (see {@code SaleCommunicationBehavior}).
 *
 * <p>The old project's {@code GeneralRequest} class itself wasn't shared, only its call sites
 * ({@code SaleActivity#prepareExchangeRequest}/{@code #callSaleProcess}) — the field set and
 * names here are reconstructed from those setter calls, not a verified match:
 * <ul>
 *   <li>Exchange sends {@link #transactionKeyEncrypted} (the RSA-wrapped TEK) and
 *       {@link #pinKeyEncrypted} (the RSA-wrapped PEK) once, keyed to {@link #asyncRequestId};
 *       sale doesn't resend either — the host is expected to already have both from the exchange
 *       call for the same {@link #asyncRequestId}. See {@link #forExchange}/{@link #forSale}.
 *   <li>Sale instead sends {@link #encPinBlock} (the online PIN block itself, encrypted under the
 *       PEK in the PIN pad's hardware) — {@code null} for a no-online-PIN transaction.
 * </ul>
 */
public final class GeneralRequest {

    @SerializedName("encSerializedRequest")
    private final String encSerializedRequest;

    @SerializedName("asyncRequestId")
    private final String asyncRequestId;

    @SerializedName("isRetry")
    private final int isRetry;

    @SerializedName("transactionKey")
    @Nullable
    private final String transactionKeyEncrypted;

    @SerializedName("pinKey")
    @Nullable
    private final String pinKeyEncrypted;

    @SerializedName("encPinBlock")
    @Nullable
    private final String encPinBlock;

    private GeneralRequest(String encSerializedRequest, String asyncRequestId, int isRetry,
            @Nullable String transactionKeyEncrypted, @Nullable String pinKeyEncrypted,
            @Nullable String encPinBlock) {
        this.encSerializedRequest = encSerializedRequest;
        this.asyncRequestId = asyncRequestId;
        this.isRetry = isRetry;
        this.transactionKeyEncrypted = transactionKeyEncrypted;
        this.pinKeyEncrypted = pinKeyEncrypted;
        this.encPinBlock = encPinBlock;
    }

    /** The exchange call — establishes the TEK (and PEK, if online PIN was collected) for {@code asyncRequestId}. */
    public static GeneralRequest forExchange(String encSerializedRequest, String asyncRequestId,
            String transactionKeyEncrypted, @Nullable String pinKeyEncrypted) {
        return new GeneralRequest(encSerializedRequest, asyncRequestId, 0,
                transactionKeyEncrypted, pinKeyEncrypted, null);
    }

    /** The sale call — reuses the TEK/PEK already established by {@link #forExchange} for {@code asyncRequestId}. */
    public static GeneralRequest forSale(String encSerializedRequest, String asyncRequestId,
            @Nullable String encPinBlock) {
        return new GeneralRequest(encSerializedRequest, asyncRequestId, 0, null, null, encPinBlock);
    }

    public String getEncSerializedRequest() {
        return encSerializedRequest;
    }

    public String getAsyncRequestId() {
        return asyncRequestId;
    }

    public int getIsRetry() {
        return isRetry;
    }

    @Nullable
    public String getTransactionKeyEncrypted() {
        return transactionKeyEncrypted;
    }

    @Nullable
    public String getPinKeyEncrypted() {
        return pinKeyEncrypted;
    }

    @Nullable
    public String getEncPinBlock() {
        return encPinBlock;
    }
}

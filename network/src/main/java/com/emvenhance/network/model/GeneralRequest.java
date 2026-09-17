package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Generic encrypted-envelope request shape — the request-side counterpart to
 * {@link GeneralResponse}, mirrors the old project's {@code GeneralRequest} as used by
 * {@code orchestration/exchange} and {@code orchestration/sale} (see
 * {@code SaleCommunicationBehavior}).
 *
 * <p>Field set confirmed against the real {@code GeneralRequest} class, found in an uploaded
 * {@code model_layer.rar}:
 * <ul>
 *   <li>Exchange sends {@link #transactionKeyEncrypted} (the RSA-wrapped TEK),
 *       {@link #pinKeyEncrypted} (the RSA-wrapped PEK, if online PIN was collected), and
 *       {@link #transactionType} once, keyed to {@link #asyncRequestId}; sale doesn't resend any
 *       of those three — the host is expected to already have them from the exchange call for the
 *       same {@link #asyncRequestId}. See {@link #forExchange}/{@link #forSale}.
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

    /** e.g. {@code "SALE"} — the old project's real enum value isn't confirmed, only that this field exists. */
    @SerializedName("transactionType")
    @Nullable
    private final String transactionType;

    private GeneralRequest(String encSerializedRequest, String asyncRequestId, int isRetry,
            @Nullable String transactionKeyEncrypted, @Nullable String pinKeyEncrypted,
            @Nullable String encPinBlock, @Nullable String transactionType) {
        this.encSerializedRequest = encSerializedRequest;
        this.asyncRequestId = asyncRequestId;
        this.isRetry = isRetry;
        this.transactionKeyEncrypted = transactionKeyEncrypted;
        this.pinKeyEncrypted = pinKeyEncrypted;
        this.encPinBlock = encPinBlock;
        this.transactionType = transactionType;
    }

    /** The exchange call — establishes the TEK (and PEK, if online PIN was collected) for {@code asyncRequestId}. */
    public static GeneralRequest forExchange(String encSerializedRequest, String asyncRequestId,
            String transactionKeyEncrypted, @Nullable String pinKeyEncrypted, String transactionType) {
        return new GeneralRequest(encSerializedRequest, asyncRequestId, 0,
                transactionKeyEncrypted, pinKeyEncrypted, null, transactionType);
    }

    /** The sale call — reuses the TEK/PEK already established by {@link #forExchange} for {@code asyncRequestId}. */
    public static GeneralRequest forSale(String encSerializedRequest, String asyncRequestId,
            @Nullable String encPinBlock) {
        return new GeneralRequest(encSerializedRequest, asyncRequestId, 0, null, null, encPinBlock, null);
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

    @Nullable
    public String getTransactionType() {
        return transactionType;
    }
}

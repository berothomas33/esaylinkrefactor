package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * Payload carried (AES-encrypted) inside {@link GeneralRequest#forExchange}'s
 * {@code encSerializedRequest} — mirrors the old project's {@code ExchangeRequest}, reconstructed
 * from its {@code new ExchangeRequest(asyncId, pan, cvm, type)} call site in
 * {@code SaleActivity#prepareExchangeRequest} (the class itself wasn't shared).
 */
public final class ExchangeRequest {

    @SerializedName("asyncId")
    private final String asyncId;

    @SerializedName("pan")
    private final String pan;

    /** CVM used for this transaction — see {@code SaleCommunicationBehavior#cvmCode}. */
    @SerializedName("cvm")
    private final int cvm;

    /** Transaction type, e.g. {@code "SALE"} — the old project's real enum value isn't confirmed. */
    @SerializedName("type")
    private final String type;

    public ExchangeRequest(String asyncId, String pan, int cvm, String type) {
        this.asyncId = asyncId;
        this.pan = pan;
        this.cvm = cvm;
        this.type = type;
    }

    public String getAsyncId() {
        return asyncId;
    }

    public String getPan() {
        return pan;
    }

    public int getCvm() {
        return cvm;
    }

    public String getType() {
        return type;
    }
}

package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Decrypted payload of the exchange call's response — mirrors the old project's
 * {@code ExchngeResponseModel} (sic), reconstructed from its two getters referenced in
 * {@code SaleActivity#callExchangeProcess} ({@code getPinKey()}/{@code getBankIndex()}, used
 * there to save a bank-issued TPK — that step is out of scope here, see
 * {@code SaleCommunicationBehavior}).
 */
public final class ExchangeResponse {

    @SerializedName("pinKey")
    @Nullable
    private String pinKey;

    @SerializedName("bankIndex")
    private int bankIndex;

    @Nullable
    public String getPinKey() {
        return pinKey;
    }

    public int getBankIndex() {
        return bankIndex;
    }
}

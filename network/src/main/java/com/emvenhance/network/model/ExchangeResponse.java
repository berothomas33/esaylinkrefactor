package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/**
 * Decrypted payload of the exchange call's response — the old project's real
 * {@code ExchngeResponseModel} (sic) class turned up in an uploaded {@code model_layer.rar}
 * (older snapshot than {@code SaleActivity.java}, but this class matches what
 * {@code SaleActivity#callExchangeProcess} reads from it: {@code onUs}, {@code bankIndex},
 * {@code pinKey} — a bank-issued TPK, used there to save it — that step is out of scope here, see
 * {@code SaleCommunicationBehavior}).
 */
public final class ExchangeResponse {

    @SerializedName("onUs")
    private boolean onUs;

    @SerializedName("bankIndex")
    private int bankIndex;

    @SerializedName("pinKey")
    @Nullable
    private String pinKey;

    public boolean isOnUs() {
        return onUs;
    }

    public int getBankIndex() {
        return bankIndex;
    }

    @Nullable
    public String getPinKey() {
        return pinKey;
    }
}

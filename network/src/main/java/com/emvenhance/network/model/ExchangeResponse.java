package com.emvenhance.network.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

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

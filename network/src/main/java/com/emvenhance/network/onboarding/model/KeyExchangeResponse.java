package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/** RKI pilot key exchange answer: the KBPK ("mak") as TR-31 under the KEK, and its KCV. */
public final class KeyExchangeResponse {

    @SerializedName("statusCode")
    private int statusCode;

    @SerializedName("message")
    @Nullable
    private String message;

    @SerializedName("data")
    @Nullable
    private Data data;

    public int getStatusCode() {
        return statusCode;
    }

    @Nullable
    public String getMessage() {
        return message;
    }

    @Nullable
    public Data getData() {
        return data;
    }

    public static final class Data {

        @SerializedName("mak")
        @Nullable
        private String mak;

        @SerializedName("makKcv")
        @Nullable
        private String makKcv;

        @Nullable
        public String getMak() {
            return mak;
        }

        @Nullable
        public String getMakKcv() {
            return makKcv;
        }
    }
}

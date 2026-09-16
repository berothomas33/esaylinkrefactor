package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

import androidx.annotation.Nullable;

/** One Terminal Master Key block for one acquiring bank. */
public final class OnboardingOnlineModel {

    @SerializedName("bankIndex")
    private int bankIndex;

    @SerializedName("bankTmk")
    @Nullable
    private String bankTmk;

    @SerializedName("bankCode")
    @Nullable
    private String bankCode;

    public int getBankIndex() {
        return bankIndex;
    }

    @Nullable
    public String getBankTmk() {
        return bankTmk;
    }

    @Nullable
    public String getBankCode() {
        return bankCode;
    }
}

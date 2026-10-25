package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

public final class ConfirmOnboardingOnlineRequest {

    @SerializedName("sacs")
    private final String sacs;

    @SerializedName("bankCodes")
    private final String bankCodes;

    public ConfirmOnboardingOnlineRequest(String sacs, String bankCodes) {
        this.sacs = sacs;
        this.bankCodes = bankCodes;
    }

    public String getSacs() {
        return sacs;
    }

    public String getBankCodes() {
        return bankCodes;
    }
}

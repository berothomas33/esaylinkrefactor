package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/**
 * {@code foundation/onboarding/confirm-online} request — {@code sacs}/{@code bankCodes} are the
 * service-account and bank-code lists {@link OnboardingOnlineResponse}'s TMK blocks were keyed
 * by, echoed back to let the host confirm which blocks were received.
 */
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

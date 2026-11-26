package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/** Who the terminal is, sent under the APP_TDK (card acceptance's OnboardingRequestModel; same field order). */
public final class OnboardingIdentity {

    @SerializedName("systemAppKey")
    private final String systemAppKey;

    @SerializedName("aggregatorAppKey")
    private final String aggregatorAppKey;

    @SerializedName("serialNumber")
    private final String serialNumber;

    public OnboardingIdentity(String systemAppKey, String aggregatorAppKey, String serialNumber) {
        this.systemAppKey = systemAppKey;
        this.aggregatorAppKey = aggregatorAppKey;
        this.serialNumber = serialNumber;
    }
}

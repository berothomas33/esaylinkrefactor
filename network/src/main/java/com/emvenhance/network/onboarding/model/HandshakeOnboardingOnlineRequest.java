package com.emvenhance.network.onboarding.model;

import com.google.gson.annotations.SerializedName;

/** {@code foundation/onboarding/online-handshake} request. */
public final class HandshakeOnboardingOnlineRequest {

    public static final String TYPE_TEMPLATE = "TEMPLATE_ONBOARDING";
    public static final String TYPE_GLOBAL = "GLOBAL_ONBOARDING";

    @SerializedName("onboardingType")
    private final String onboardingType;

    @SerializedName("onboardingKey")
    private final String onboardingKey;

    public HandshakeOnboardingOnlineRequest(String onboardingType, String onboardingKey) {
        this.onboardingType = onboardingType;
        this.onboardingKey = onboardingKey;
    }

    public String getOnboardingType() {
        return onboardingType;
    }

    public String getOnboardingKey() {
        return onboardingKey;
    }
}

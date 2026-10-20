package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/** `foundation/onboarding/online-handshake` request. */
class HandshakeOnboardingOnlineRequest(
    @field:SerializedName("onboardingType") val onboardingType: String,
    @field:SerializedName("onboardingKey") val onboardingKey: String,
) {
    companion object {
        const val TYPE_TEMPLATE = "TEMPLATE_ONBOARDING"
        const val TYPE_GLOBAL = "GLOBAL_ONBOARDING"
    }
}

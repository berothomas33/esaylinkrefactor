package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/** `foundation/onboarding/offline` response — mirrors the old project's OnboardingResponse. */
class OnboardingResponse {

    @SerializedName("statusCode")
    var statusCode: Int = 0
        private set

    @SerializedName("message")
    var message: String? = null
        private set

    @SerializedName("data")
    var data: OnboardingResult? = null
        private set
}

package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/**
 * The host's identity, returned by step 1 of the offline onboarding cycle — mirrors the old
 * project's `OnboardingModel` (renamed here to avoid clashing with the "model" package name).
 */
class OnboardingResult {

    @SerializedName("serviceAccount")
    var serviceAccount: String? = null
        private set

    @SerializedName("publicKey")
    var publicKey: String? = null
        private set

    @SerializedName("signature")
    var signature: String? = null
        private set
}

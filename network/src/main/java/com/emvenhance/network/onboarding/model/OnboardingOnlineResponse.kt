package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/**
 * Decrypted shape of `foundation/onboarding/online`'s payload — one TMK block per acquiring
 * bank. The wire response is [com.emvenhance.network.model.GeneralResponse] (encrypted); see
 * `OnboardingClient#onboardOnline` for why decrypting into this shape isn't wired up yet.
 */
class OnboardingOnlineResponse {

    @SerializedName("tmk_blocks")
    var tmkBlocks: List<OnboardingOnlineModel>? = null
        private set
}

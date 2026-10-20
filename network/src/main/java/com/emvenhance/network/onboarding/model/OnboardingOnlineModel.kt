package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/** One Terminal Master Key block for one acquiring bank. */
class OnboardingOnlineModel {

    @SerializedName("bankIndex")
    var bankIndex: Int = 0
        private set

    @SerializedName("bankTmk")
    var bankTmk: String? = null
        private set

    @SerializedName("bankCode")
    var bankCode: String? = null
        private set
}

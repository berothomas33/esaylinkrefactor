package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/** `authorization/token/pos` response envelope. */
class CreateTokenResponse {

    @SerializedName("statusCode")
    var statusCode: Int = 0
        private set

    @SerializedName("message")
    var message: String? = null
        private set

    @SerializedName("data")
    var data: CreateTokenResult? = null
        private set
}

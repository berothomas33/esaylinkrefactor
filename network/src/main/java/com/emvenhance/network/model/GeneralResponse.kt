package com.emvenhance.network.model

import com.google.gson.annotations.SerializedName

/**
 * Generic encrypted-envelope response shape — mirrors the old project's `GeneralResponse`.
 * [data] carries a [DataModel] whose `encSerializedResponse` needs decrypting (via the host's
 * `crypto/{algorithm}/decrypt` endpoint or equivalent local key material) before it can be parsed
 * into whatever payload the call actually returned — that decryption step wasn't present in the
 * reference model layer this was ported from, so it isn't implemented here; see
 * `OnboardingClient#onboardOnline`.
 */
class GeneralResponse {

    @SerializedName("statusCode")
    var statusCode: Int = 0
        private set

    @SerializedName("message")
    var message: String? = null
        private set

    @SerializedName("data")
    var data: DataModel? = null
        private set
}

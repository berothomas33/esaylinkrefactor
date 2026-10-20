package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/**
 * `authorization/token/pos`'s `data` — field names confirmed against a real captured response
 * (not reconstructed from a call site like most of this module's other models).
 *
 * [signature] is meant to be verified as `HMAC-SHA256(accessToken + challenge)` — see
 * `ConfigurationActivity#createToken` — but the key isn't the SACS (that's confirm's key,
 * already consumed), and none of this app's now-known fixed constants (aggregator-app-key,
 * system-app-key, apiKey) reproduce the real captured signature either (checked). Not verified
 * here — see `OnboardingClient#createToken`'s doc.
 */
class CreateTokenResult {

    @SerializedName("userId")
    var userId: Int = 0
        private set

    @SerializedName("accessToken")
    var accessToken: String? = null
        private set

    @SerializedName("signature")
    var signature: String? = null
        private set

    @SerializedName("expiration")
    var expiration: String? = null
        private set

    @SerializedName("serialNumber")
    var serialNumber: String? = null
        private set
}

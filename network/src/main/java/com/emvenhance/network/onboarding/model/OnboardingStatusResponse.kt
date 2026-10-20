package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/**
 * Shared `{statusCode, message, data: String}` shape — the old project defined this same shape
 * three times over (its own `HandshakeResponse`, `ConfirmOnboardingResponse`,
 * `HandshakeOnboardingOnlineResponse`), and its `confirm-online` endpoint already returned the
 * offline `ConfirmOnboardingResponse` type rather than the separate
 * `ConfirmOnboardingOnlineResponse` it also defined — so one class here covers all four: the
 * offline handshake, the offline confirm, the online handshake, and the online confirm.
 */
class OnboardingStatusResponse {

    @SerializedName("statusCode")
    var statusCode: Int = 0
        private set

    @SerializedName("message")
    var message: String? = null
        private set

    @SerializedName("data")
    var data: String? = null
        private set
}

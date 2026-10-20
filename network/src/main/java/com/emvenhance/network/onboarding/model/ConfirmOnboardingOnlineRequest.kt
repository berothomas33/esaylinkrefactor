package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/**
 * `foundation/onboarding/confirm-online` request — `sacs`/`bankCodes` are the service-account and
 * bank-code lists [OnboardingOnlineResponse]'s TMK blocks were keyed by, echoed back to let the
 * host confirm which blocks were received.
 */
class ConfirmOnboardingOnlineRequest(
    @field:SerializedName("sacs") val sacs: String,
    @field:SerializedName("bankCodes") val bankCodes: String,
)

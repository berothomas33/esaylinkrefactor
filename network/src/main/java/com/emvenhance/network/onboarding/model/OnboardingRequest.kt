package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/** `foundation/onboarding/offline` request — mirrors the old project's OnboardingRequest. */
class OnboardingRequest(@field:SerializedName("challenge") val challenge: String)

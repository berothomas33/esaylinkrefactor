package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/** `foundation/onboarding/confirm` request — same challenge sent in step 1. */
class ConfirmOnboardingRequest(@field:SerializedName("challenge") val challenge: String)

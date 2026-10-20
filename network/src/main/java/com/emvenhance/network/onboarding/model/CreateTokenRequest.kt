package com.emvenhance.network.onboarding.model

import com.google.gson.annotations.SerializedName

/**
 * `authorization/token/pos` request — confirmed against a real captured onboarding run:
 * `{"username": "<serviceAccount from the onboard step's response>"}`.
 */
class CreateTokenRequest(@field:SerializedName("username") val username: String)

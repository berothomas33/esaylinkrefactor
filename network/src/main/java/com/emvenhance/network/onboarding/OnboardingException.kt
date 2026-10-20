package com.emvenhance.network.onboarding

/** An onboarding step's `statusCode` wasn't [OnboardingClient.SUCCESS_STATUS_CODE]. */
class OnboardingException(val statusCode: Int, message: String) : RuntimeException(message)

package com.emvenhance.network.onboarding

import java.util.UUID

/**
 * Generates the `challenge` nonce for the offline onboarding cycle (sent in step 1's
 * `OnboardingRequest`; step 3 sends `HMAC-SHA256(challenge, SACS)` instead of the raw value — see
 * [HmacSigner] and `ConfigurationActivity#onboardingConfirmation`).
 *
 * `ConfigurationActivity` generates it via `MomknKeysUtils.createChallenge()`, whose own source
 * wasn't shared — this is a plain random UUID standing in for that, until the real generation
 * requirement (length, charset, entropy source) is known. It only needs to be unpredictable and
 * unique per onboarding attempt; the server doesn't appear to validate its shape, only that the
 * same value comes back correctly signed.
 */
object ChallengeGenerator {

    @JvmStatic
    fun generate(): String = UUID.randomUUID().toString()
}

package com.emvenhance.network.onboarding;

import java.util.UUID;

/**
 * Generates the {@code challenge} nonce for the offline onboarding cycle (sent in step 1's
 * {@code OnboardingRequest}; step 3 sends {@code HMAC-SHA256(challenge, SACS)} instead of the
 * raw value — see {@link HmacSigner} and {@code ConfigurationActivity#onboardingConfirmation}).
 *
 * <p>{@code ConfigurationActivity} generates it via {@code MomknKeysUtils.createChallenge()},
 * whose own source wasn't shared — this is a plain random UUID standing in for that, until the
 * real generation requirement (length, charset, entropy source) is known. It only needs to be
 * unpredictable and unique per onboarding attempt; the server doesn't appear to validate its
 * shape, only that the same value comes back correctly signed.
 */
public final class ChallengeGenerator {

    private ChallengeGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

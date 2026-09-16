package com.emvenhance.network.onboarding;

import java.util.UUID;

/**
 * Generates the {@code challenge} nonce for the offline onboarding cycle (sent in step 1's
 * {@code OnboardingRequest} and echoed back in step 3's {@code ConfirmOnboardingRequest}).
 *
 * <p>The reference model layer this was ported from never showed where its challenge came from
 * or how the step-1 response's {@code signature}/{@code publicKey} get verified against it —
 * that logic lived above the model layer, in code not included here. This is a plain random UUID,
 * standing in until the real generation/verification requirement is known.
 */
public final class ChallengeGenerator {

    private ChallengeGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

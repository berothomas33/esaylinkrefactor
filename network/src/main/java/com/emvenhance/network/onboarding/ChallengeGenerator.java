package com.emvenhance.network.onboarding;

import java.util.UUID;

public final class ChallengeGenerator {

    private ChallengeGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}

package com.emvenhance.network.onboarding;

public final class OnboardingException extends RuntimeException {

    private final int statusCode;

    public OnboardingException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

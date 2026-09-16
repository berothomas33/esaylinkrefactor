package com.emvenhance.network.onboarding;

/** An onboarding step's {@code statusCode} wasn't {@link OnboardingClient#SUCCESS_STATUS_CODE}. */
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

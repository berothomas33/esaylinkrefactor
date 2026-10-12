package com.emvenhance.network;

/**
 * A host request couldn't complete: a missing onboarding prerequisite, an envelope that couldn't
 * be encrypted or decrypted, or a non-success answer. {@code EmvEngine} reports it as online failed.
 */
public final class HostException extends RuntimeException {

    public HostException(String message) {
        super(message);
    }

    public HostException(String message, Throwable cause) {
        super(message, cause);
    }
}

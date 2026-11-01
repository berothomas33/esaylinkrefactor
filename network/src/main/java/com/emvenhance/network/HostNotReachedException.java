package com.emvenhance.network;

/**
 * Inside {@link SaleCommunicationBehavior}: a step failed in a way the host could not have
 * approved the request — it was never sent, or the host refused who sent it. Its
 * {@code authorize} answers it as {@code AuthResult.notSent} (not reversed); any other error as
 * {@code AuthResult.failed} (reversed). It never leaves the network module.
 */
public class HostNotReachedException extends RuntimeException {

    public HostNotReachedException(String message) {
        super(message);
    }

    public HostNotReachedException(String message, Throwable cause) {
        super(message, cause);
    }
}

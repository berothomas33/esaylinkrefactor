package com.emvenhance.core.host;

/**
 * A {@link CommunicationBehavior#authorize} error meaning the host could not have approved the
 * request: it was never sent, or the host refused who sent it. {@code PosTerminal} makes it
 * {@link AuthResult#notSent}, which is not reversed; any other error is reversed.
 */
public class HostNotReachedException extends RuntimeException {

    public HostNotReachedException(String message) {
        super(message);
    }

    public HostNotReachedException(String message, Throwable cause) {
        super(message, cause);
    }
}

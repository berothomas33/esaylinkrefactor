package com.emvenhance.core.host;

public class HostNotReachedException extends RuntimeException {

    public HostNotReachedException(String message) {
        super(message);
    }

    public HostNotReachedException(String message, Throwable cause) {
        super(message, cause);
    }
}

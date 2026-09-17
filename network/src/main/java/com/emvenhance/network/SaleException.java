package com.emvenhance.network;

/** A sale/exchange step failed — either a transport-level error or a non-success envelope. */
public final class SaleException extends RuntimeException {

    public SaleException(String message) {
        super(message);
    }

    public SaleException(String message, Throwable cause) {
        super(message, cause);
    }
}

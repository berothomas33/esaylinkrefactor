package com.emvenhance.core;

/**
 * Immutable value object holding everything the EMV engine needs to start a transaction.
 *
 * <p>New fields (currency code, cashback amount, terminal ID) can be added here without
 * touching the engine's method signature — every vendor implementation reads the same object.
 */
public final class TransactionConfig {

    public enum Mode { CONTACT, CONTACTLESS }

    private final String procCode;
    private final long amountMinor;
    private final Mode mode;

    public TransactionConfig(String procCode, long amountMinor, Mode mode) {
        this.procCode = procCode;
        this.amountMinor = amountMinor;
        this.mode = mode;
    }

    public String getProcCode() {
        return procCode;
    }

    /** Amount in the smallest currency unit (e.g. cents). */
    public long getAmountMinor() {
        return amountMinor;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isContact() {
        return mode == Mode.CONTACT;
    }

    public boolean isContactless() {
        return mode == Mode.CONTACTLESS;
    }
}

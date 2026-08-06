package com.emvenhance.core.card;

/**
 * Immutable value object holding everything needed to start a transaction.
 */
public final class TransactionConfig {

    public enum Mode {
        CONTACT,
        CONTACTLESS,
        MAGSTRIPE,
        MANUAL,
        /** Chip + contactless + mag — terminal selects the first presented. */
        ANY
    }

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

    public boolean isMagstripe() {
        return mode == Mode.MAGSTRIPE;
    }

    public boolean isManual() {
        return mode == Mode.MANUAL;
    }

    public boolean allowsChip() {
        return mode == Mode.CONTACT || mode == Mode.ANY;
    }

    public boolean allowsContactless() {
        return mode == Mode.CONTACTLESS || mode == Mode.ANY;
    }

    public boolean allowsMagstripe() {
        return mode == Mode.MAGSTRIPE || mode == Mode.ANY;
    }

    public boolean allowsManual() {
        return mode == Mode.MANUAL || mode == Mode.ANY;
    }

    /** Same amount and proc code, different entry mode — for a kernel-requested retry. */
    public TransactionConfig withMode(Mode mode) {
        return new TransactionConfig(procCode, amountMinor, mode);
    }
}

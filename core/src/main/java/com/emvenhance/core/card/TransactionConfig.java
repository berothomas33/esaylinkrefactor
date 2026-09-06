package com.emvenhance.core.card;

/**
 * Immutable value object holding everything needed to start a transaction.
 */
public final class TransactionConfig {

    private final TransactionType type;
    private final long amountMinor;
    private final EntryMethod mode;

    public TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode) {
        this.type = type;
        this.amountMinor = amountMinor;
        this.mode = mode;
    }

    public TransactionType getType() {
        return type;
    }

    /** ISO 8583-style processing code for {@link #getType()} — see {@link TransactionType}. */
    public String getProcCode() {
        return type.getProcCode();
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public EntryMethod getMode() {
        return mode;
    }

    public boolean isContact() {
        return mode == EntryMethod.CHIP;
    }

    public boolean isContactless() {
        return mode == EntryMethod.CONTACTLESS;
    }

    public boolean isMagstripe() {
        return mode == EntryMethod.MAGSTRIPE;
    }

    public boolean isManual() {
        return mode == EntryMethod.MANUAL;
    }

    public boolean allowsChip() {
        return mode == EntryMethod.CHIP || mode == EntryMethod.ANY;
    }

    public boolean allowsContactless() {
        return mode == EntryMethod.CONTACTLESS || mode == EntryMethod.ANY;
    }

    public boolean allowsMagstripe() {
        return mode == EntryMethod.MAGSTRIPE || mode == EntryMethod.ANY;
    }

    public boolean allowsManual() {
        return mode == EntryMethod.MANUAL || mode == EntryMethod.ANY;
    }

    /** Same amount and proc code, different entry mode — for a kernel-requested retry. */
    public TransactionConfig withMode(EntryMethod mode) {
        return new TransactionConfig(type, amountMinor, mode);
    }
}

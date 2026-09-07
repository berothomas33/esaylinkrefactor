package com.emvenhance.core.card;

import androidx.annotation.Nullable;

/**
 * Immutable value object holding everything needed to start a transaction.
 */
public final class TransactionConfig {

    private final TransactionType type;
    private final long amountMinor;
    private final EntryMethod mode;

    /**
     * Field 55 (ICC System Related Data) as hex-encoded BER-TLV, built once the kernel has read
     * the card — {@code null} for magstripe/manual (no ICC) or before a chip/contactless
     * transaction reaches {@code START_ONLINE_PROCESS}. See {@link #withIccData}.
     */
    @Nullable
    private final String iccData;

    public TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode) {
        this(type, amountMinor, mode, null);
    }

    private TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode,
            @Nullable String iccData) {
        this.type = type;
        this.amountMinor = amountMinor;
        this.mode = mode;
        this.iccData = iccData;
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

    @Nullable
    public String getIccData() {
        return iccData;
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
        return new TransactionConfig(type, amountMinor, mode, iccData);
    }

    /** Same everything else, with Field 55 attached — see {@link #getIccData()}. */
    public TransactionConfig withIccData(@Nullable String iccData) {
        return new TransactionConfig(type, amountMinor, mode, iccData);
    }
}

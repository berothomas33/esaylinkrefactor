package com.emvenhance.core.card;

import androidx.annotation.Nullable;

public final class TransactionConfig {

    private final TransactionType type;
    private final long amountMinor;
    private final EntryMethod mode;

    @Nullable
    private final String iccData;

    @Nullable
    private final String pan;

    @Nullable
    private final String onlinePinBlock;

    @Nullable
    private final String onlinePinKeyEncrypted;

    @Nullable
    private final EmvTransactionResult emvResult;

    @Nullable
    private final String referenceNumber;

    public TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode) {
        this(type, amountMinor, mode, null, null, null, null, null, null);
    }

    private TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode,
            @Nullable String iccData, @Nullable String pan, @Nullable String onlinePinBlock,
            @Nullable String onlinePinKeyEncrypted, @Nullable EmvTransactionResult emvResult,
            @Nullable String referenceNumber) {
        this.type = type;
        this.amountMinor = amountMinor;
        this.mode = mode;
        this.iccData = iccData;
        this.pan = pan;
        this.onlinePinBlock = onlinePinBlock;
        this.onlinePinKeyEncrypted = onlinePinKeyEncrypted;
        this.emvResult = emvResult;
        this.referenceNumber = referenceNumber;
    }

    public TransactionType getType() {
        return type;
    }

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

    @Nullable
    public String getPan() {
        return pan;
    }

    @Nullable
    public String getOnlinePinBlock() {
        return onlinePinBlock;
    }

    @Nullable
    public String getOnlinePinKeyEncrypted() {
        return onlinePinKeyEncrypted;
    }

    @Nullable
    public EmvTransactionResult getEmvResult() {
        return emvResult;
    }

    @Nullable
    public String getReferenceNumber() {
        return referenceNumber;
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

    public TransactionConfig withMode(EntryMethod mode) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }

    public TransactionConfig withIccData(@Nullable String iccData) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }

    public TransactionConfig withPan(@Nullable String pan) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }

    public TransactionConfig withOnlinePinBlock(@Nullable String onlinePinBlock) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }

    public TransactionConfig withOnlinePinKeyEncrypted(@Nullable String onlinePinKeyEncrypted) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }

    public TransactionConfig withEmvResult(@Nullable EmvTransactionResult emvResult) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }

    public TransactionConfig withReferenceNumber(@Nullable String referenceNumber) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult, referenceNumber);
    }
}

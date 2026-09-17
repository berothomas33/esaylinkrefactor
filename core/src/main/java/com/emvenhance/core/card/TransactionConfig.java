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

    /** PAN, captured off the kernel once read — see {@link #withPan}. */
    @Nullable
    private final String pan;

    /**
     * Online PIN block, hex-encoded — captured by the vendor's PIN pad during online PIN entry
     * and attached before {@code START_ONLINE_PROCESS} calls host authorize; {@code null} for
     * no-CVM/offline-PIN/CDCVM transactions. See {@link #withOnlinePinBlock}.
     */
    @Nullable
    private final String onlinePinBlock;

    /**
     * The online PIN key (PEK) that encrypted {@link #onlinePinBlock} in the PIN pad's secure
     * hardware, RSA-wrapped with the host's public key (base64) so the host can independently
     * recover the same key value — see {@code RsaPublicKeyEncryptor} /
     * {@code PaxOnlinePinKeyProvisioner}. {@code null} whenever {@link #onlinePinBlock} is.
     */
    @Nullable
    private final String onlinePinKeyEncrypted;

    /**
     * The full EMV-kernel result (PAN, track2, AID, TVR/TSI/ATC/ARQC-or-TC, ...) assembled right
     * before {@code START_ONLINE_PROCESS} — see {@link EmvTransactionResult} and
     * {@link #withEmvResult}. {@code null} until then, and always for mag/manual (no EMV kernel).
     */
    @Nullable
    private final EmvTransactionResult emvResult;

    public TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode) {
        this(type, amountMinor, mode, null, null, null, null, null);
    }

    private TransactionConfig(TransactionType type, long amountMinor, EntryMethod mode,
            @Nullable String iccData, @Nullable String pan, @Nullable String onlinePinBlock,
            @Nullable String onlinePinKeyEncrypted, @Nullable EmvTransactionResult emvResult) {
        this.type = type;
        this.amountMinor = amountMinor;
        this.mode = mode;
        this.iccData = iccData;
        this.pan = pan;
        this.onlinePinBlock = onlinePinBlock;
        this.onlinePinKeyEncrypted = onlinePinKeyEncrypted;
        this.emvResult = emvResult;
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
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult);
    }

    /** Same everything else, with Field 55 attached — see {@link #getIccData()}. */
    public TransactionConfig withIccData(@Nullable String iccData) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult);
    }

    /** Same everything else, with the PAN attached — see {@link #getPan()}. */
    public TransactionConfig withPan(@Nullable String pan) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult);
    }

    /** Same everything else, with the online PIN block attached — see {@link #getOnlinePinBlock()}. */
    public TransactionConfig withOnlinePinBlock(@Nullable String onlinePinBlock) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult);
    }

    /** Same everything else, with the RSA-wrapped PIN key attached — see {@link #getOnlinePinKeyEncrypted()}. */
    public TransactionConfig withOnlinePinKeyEncrypted(@Nullable String onlinePinKeyEncrypted) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult);
    }

    /** Same everything else, with the full EMV result attached — see {@link #getEmvResult()}. */
    public TransactionConfig withEmvResult(@Nullable EmvTransactionResult emvResult) {
        return new TransactionConfig(type, amountMinor, mode, iccData, pan, onlinePinBlock,
                onlinePinKeyEncrypted, emvResult);
    }
}

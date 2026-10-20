package com.emvenhance.core.card

/**
 * Immutable value object holding everything needed to start a transaction.
 */
class TransactionConfig private constructor(
    val type: TransactionType,
    val amountMinor: Long,
    val mode: EntryMethod,
    /**
     * Field 55 (ICC System Related Data) as hex-encoded BER-TLV, built once the kernel has read
     * the card — `null` for magstripe/manual (no ICC) or before a chip/contactless transaction
     * reaches `START_ONLINE_PROCESS`. See [withIccData].
     */
    val iccData: String?,
    /** PAN, captured off the kernel once read — see [withPan]. */
    val pan: String?,
    /**
     * Online PIN block, hex-encoded — captured by the vendor's PIN pad during online PIN entry
     * and attached before `START_ONLINE_PROCESS` calls host authorize; `null` for
     * no-CVM/offline-PIN/CDCVM transactions. See [withOnlinePinBlock].
     */
    val onlinePinBlock: String?,
    /**
     * The online PIN key (PEK) that encrypted [onlinePinBlock] in the PIN pad's secure hardware,
     * RSA-wrapped with the host's public key (base64) so the host can independently recover the
     * same key value — see `RsaPublicKeyEncryptor` / `PaxOnlinePinKeyProvisioner`. `null`
     * whenever [onlinePinBlock] is.
     */
    val onlinePinKeyEncrypted: String?,
    /**
     * The full EMV-kernel result (PAN, track2, AID, TVR/TSI/ATC/ARQC-or-TC, ...) assembled right
     * before `START_ONLINE_PROCESS` — see [EmvTransactionResult] and [withEmvResult]. `null`
     * until then, and always for mag/manual (no EMV kernel).
     */
    val emvResult: EmvTransactionResult?,
    /**
     * For a refund: the original sale's reference number, as the host returned it on that sale
     * (and the receipt shows) — the host's refund request (`RefundRequest#referenceNumber`)
     * needs it. `null` for other types. See [withReferenceNumber].
     */
    val referenceNumber: String?,
) {

    constructor(type: TransactionType, amountMinor: Long, mode: EntryMethod) :
        this(type, amountMinor, mode, null, null, null, null, null, null)

    /** ISO 8583-style processing code for [type] — see [TransactionType]. */
    val procCode: String get() = type.procCode

    val isContact: Boolean get() = mode == EntryMethod.CHIP

    val isContactless: Boolean get() = mode == EntryMethod.CONTACTLESS

    val isMagstripe: Boolean get() = mode == EntryMethod.MAGSTRIPE

    val isManual: Boolean get() = mode == EntryMethod.MANUAL

    fun allowsChip(): Boolean = mode == EntryMethod.CHIP || mode == EntryMethod.ANY

    fun allowsContactless(): Boolean = mode == EntryMethod.CONTACTLESS || mode == EntryMethod.ANY

    fun allowsMagstripe(): Boolean = mode == EntryMethod.MAGSTRIPE || mode == EntryMethod.ANY

    fun allowsManual(): Boolean = mode == EntryMethod.MANUAL || mode == EntryMethod.ANY

    private fun copy(
        mode: EntryMethod = this.mode,
        iccData: String? = this.iccData,
        pan: String? = this.pan,
        onlinePinBlock: String? = this.onlinePinBlock,
        onlinePinKeyEncrypted: String? = this.onlinePinKeyEncrypted,
        emvResult: EmvTransactionResult? = this.emvResult,
        referenceNumber: String? = this.referenceNumber,
    ) = TransactionConfig(
        type, amountMinor, mode, iccData, pan, onlinePinBlock, onlinePinKeyEncrypted,
        emvResult, referenceNumber,
    )

    /** Same amount and proc code, different entry mode — for a kernel-requested retry. */
    fun withMode(mode: EntryMethod): TransactionConfig = copy(mode = mode)

    /** Same everything else, with Field 55 attached — see [iccData]. */
    fun withIccData(iccData: String?): TransactionConfig = copy(iccData = iccData)

    /** Same everything else, with the PAN attached — see [pan]. */
    fun withPan(pan: String?): TransactionConfig = copy(pan = pan)

    /** Same everything else, with the online PIN block attached — see [onlinePinBlock]. */
    fun withOnlinePinBlock(onlinePinBlock: String?): TransactionConfig =
        copy(onlinePinBlock = onlinePinBlock)

    /** Same everything else, with the RSA-wrapped PIN key attached — see [onlinePinKeyEncrypted]. */
    fun withOnlinePinKeyEncrypted(onlinePinKeyEncrypted: String?): TransactionConfig =
        copy(onlinePinKeyEncrypted = onlinePinKeyEncrypted)

    /** Same everything else, with the full EMV result attached — see [emvResult]. */
    fun withEmvResult(emvResult: EmvTransactionResult?): TransactionConfig =
        copy(emvResult = emvResult)

    /** Same everything else, with the original sale's reference number — see [referenceNumber]. */
    fun withReferenceNumber(referenceNumber: String?): TransactionConfig =
        copy(referenceNumber = referenceNumber)
}

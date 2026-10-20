package com.emvenhance.core.card

/**
 * Everything read off the card during EMV processing that the host needs for online
 * authorization — assembled once, right before `START_ONLINE_PROCESS`, and attached to
 * [TransactionConfig.withEmvResult]. Vendor-agnostic mirror of the old project's
 * `PosTransactionResult` (itself produced by a vendor SDK's EMV callback), trimmed to the
 * fields the exchange/sale request bodies actually read from it.
 *
 * Built via [Builder] since it has this many fields — same shape as
 * `EmvTransParam.Builder`/`EmvProcessParam.Builder` already used elsewhere in this codebase for
 * the same reason.
 */
class EmvTransactionResult private constructor(b: Builder) {

    val pan: String? = b.pan
    val maskedPan: String? = b.maskedPan
    val track2: String? = b.track2

    /** YYMM, tag 5F24 truncated — see [Builder.expDate]. */
    val expDate: String? = b.expDate
    val cardHolderName: String? = b.cardHolderName

    /** Field 55 (ICC System Related Data), hex BER-TLV — same value as [TransactionConfig.iccData]. */
    val chipData: String? = b.chipData
    val pinBlock: String? = b.pinBlock
    val emvAppLabel: String? = b.emvAppLabel
    val emvAppName: String? = b.emvAppName
    val aid: String? = b.aid
    val tvr: String? = b.tvr
    val tsi: String? = b.tsi
    val atc: String? = b.atc

    /** Tag 9F26 when CID (9F27) says this cryptogram is an ARQC — `null` otherwise. */
    val arqc: String? = b.arqc

    /** Tag 9F26 when CID (9F27) says this cryptogram is a TC — `null` otherwise. */
    val tc: String? = b.tc
    val issuerName: String? = b.issuerName
    val hasPin: Boolean = b.hasPin
    val entryMethod: EntryMethod = b.entryMethod

    class Builder {
        internal var pan: String? = null
        internal var maskedPan: String? = null
        internal var track2: String? = null
        internal var expDate: String? = null
        internal var cardHolderName: String? = null
        internal var chipData: String? = null
        internal var pinBlock: String? = null
        internal var emvAppLabel: String? = null
        internal var emvAppName: String? = null
        internal var aid: String? = null
        internal var tvr: String? = null
        internal var tsi: String? = null
        internal var atc: String? = null
        internal var arqc: String? = null
        internal var tc: String? = null
        internal var issuerName: String? = null
        internal var hasPin: Boolean = false
        internal var entryMethod: EntryMethod = EntryMethod.CHIP

        fun pan(pan: String?) = apply { this.pan = pan }

        fun maskedPan(maskedPan: String?) = apply { this.maskedPan = maskedPan }

        fun track2(track2: String?) = apply { this.track2 = track2 }

        fun expDate(expDate: String?) = apply { this.expDate = expDate }

        fun cardHolderName(cardHolderName: String?) = apply { this.cardHolderName = cardHolderName }

        fun chipData(chipData: String?) = apply { this.chipData = chipData }

        fun pinBlock(pinBlock: String?) = apply { this.pinBlock = pinBlock }

        fun emvAppLabel(emvAppLabel: String?) = apply { this.emvAppLabel = emvAppLabel }

        fun emvAppName(emvAppName: String?) = apply { this.emvAppName = emvAppName }

        fun aid(aid: String?) = apply { this.aid = aid }

        fun tvr(tvr: String?) = apply { this.tvr = tvr }

        fun tsi(tsi: String?) = apply { this.tsi = tsi }

        fun atc(atc: String?) = apply { this.atc = atc }

        fun arqc(arqc: String?) = apply { this.arqc = arqc }

        fun tc(tc: String?) = apply { this.tc = tc }

        fun issuerName(issuerName: String?) = apply { this.issuerName = issuerName }

        fun hasPin(hasPin: Boolean) = apply { this.hasPin = hasPin }

        fun entryMethod(entryMethod: EntryMethod) = apply { this.entryMethod = entryMethod }

        fun build(): EmvTransactionResult = EmvTransactionResult(this)
    }
}

package com.emvenhance.core.card

/**
 * Result of [com.emvenhance.core.terminal.PosTerminal.searchCard] — selected [EntryMethod] plus
 * track/UID data.
 */
class CardPresence private constructor(
    val entryMethod: EntryMethod,
    val track1: String?,
    val track2: String?,
    val track3: String?,
    val serialInfo: ByteArray?,
    val manualPan: String?,
) {

    val isChip: Boolean get() = entryMethod == EntryMethod.CHIP

    val isContactless: Boolean get() = entryMethod == EntryMethod.CONTACTLESS

    val isMagstripe: Boolean get() = entryMethod == EntryMethod.MAGSTRIPE

    val isManual: Boolean get() = entryMethod == EntryMethod.MANUAL

    val modeLabel: String
        get() = when (entryMethod) {
            EntryMethod.CHIP -> "Chip"
            EntryMethod.CONTACTLESS -> "Contactless"
            EntryMethod.MAGSTRIPE -> "Magstripe"
            EntryMethod.MANUAL -> "Manual"
            EntryMethod.ANY -> entryMethod.name
        }

    companion object {
        @JvmStatic
        fun chip(): CardPresence = CardPresence(EntryMethod.CHIP, null, null, null, null, null)

        @JvmStatic
        fun contactless(serialInfo: ByteArray?): CardPresence =
            CardPresence(EntryMethod.CONTACTLESS, null, null, null, serialInfo, null)

        @JvmStatic
        fun magstripe(track1: String?, track2: String?, track3: String?): CardPresence =
            CardPresence(EntryMethod.MAGSTRIPE, track1, track2, track3, null, null)

        @JvmStatic
        fun manual(pan: String?): CardPresence =
            CardPresence(EntryMethod.MANUAL, null, null, null, null, pan)
    }
}

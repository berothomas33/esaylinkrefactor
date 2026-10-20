package com.emvenhance.network

import com.emvenhance.core.card.EntryMethod

/**
 * ISO 8583 POS entry mode (three digits): how the PAN was read, then the terminal's PIN entry
 * capability (`1`: can accept a PIN). The old app's approved chip sale sent `"051"`; the other
 * entry methods follow the same layout. Shared so [RetrofitCommunicationBehavior]'s
 * `crypto/purchase` flow and `SaleCommunicationBehavior`'s `cacore/exchange`+`cacore/sale` flow
 * can't drift onto two different tables.
 */
object PosEntryModeCodes {

    private const val PIN_ENTRY_CAPABLE = "1"

    @JvmStatic
    fun forEntryMethod(mode: EntryMethod): String = panEntryMode(mode) + PIN_ENTRY_CAPABLE

    private fun panEntryMode(mode: EntryMethod): String = when (mode) {
        EntryMethod.CHIP -> "05"
        EntryMethod.CONTACTLESS -> "07"
        EntryMethod.MAGSTRIPE -> "90"
        EntryMethod.MANUAL -> "01"
        EntryMethod.ANY -> "00"
    }
}

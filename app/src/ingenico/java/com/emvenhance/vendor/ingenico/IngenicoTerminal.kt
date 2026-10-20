package com.emvenhance.vendor.ingenico

import android.util.Log
import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.core.host.HostDefaults
import com.emvenhance.core.host.PrinterBehavior
import com.emvenhance.core.terminal.PosTerminal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ingenico terminal stub. Replace [searchCard] with Tetra/Axium reader APIs when the SDK is
 * attached — no core changes required.
 */
class IngenicoTerminal private constructor(
    communication: CommunicationBehavior,
    printer: PrinterBehavior,
) : PosTerminal(IngenicoEmvBehavior(), communication, printer) {

    private val stopSearch = AtomicBoolean(false)

    constructor() : this(HostDefaults.approveAlways(), HostDefaults.logPrinter(TAG))

    override fun initializeVendor() {
        Log.w(TAG, "Ingenico SDK not attached — stub card search")
    }

    override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
        stopSearch.set(false)
        listener.onSearchStarted(config)

        try {
            Thread.sleep(SEARCH_DELAY_MS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            listener.onSearchCancelled()
            return null
        }

        if (stopSearch.get() || isSearchCancelled()) {
            listener.onSearchCancelled()
            return null
        }

        // TODO: native Ingenico ICC / PICC / Mag poll + listener callbacks
        if (config.isManual) {
            return CardPresence.manual("4111111111111111").also(listener::onManualEntrySelected)
        }
        if (config.isMagstripe) {
            return CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null)
                .also(listener::onMagstripeDetected)
        }
        if (config.isContactless) {
            return CardPresence.contactless(byteArrayOf(0x0A, 0x0B)).also(listener::onContactlessDetected)
        }
        if (config.allowsChip()) {
            return CardPresence.chip().also(listener::onChipDetected)
        }
        if (config.allowsContactless()) {
            return CardPresence.contactless(byteArrayOf(0x0A, 0x0B)).also(listener::onContactlessDetected)
        }
        if (config.allowsMagstripe()) {
            return CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null)
                .also(listener::onMagstripeDetected)
        }

        listener.onSearchTimeout()
        return null
    }

    override fun cancelCardSearch() {
        stopSearch.set(true)
    }

    private companion object {
        const val TAG = "IngenicoTerminal"
        const val SEARCH_DELAY_MS = 200L
    }
}

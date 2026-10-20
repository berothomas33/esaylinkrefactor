package com.emvenhance.vendor.fake

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.core.host.HostDefaults
import com.emvenhance.core.host.PrinterBehavior
import com.emvenhance.core.terminal.PosTerminal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Demo terminal — simulates search events so the UI works without POS hardware. The search
 * blocks the transaction thread like a real vendor poll does.
 */
class FakeTerminal private constructor(
    communication: CommunicationBehavior,
    printer: PrinterBehavior,
) : PosTerminal(FakeEmvBehavior(), communication, printer) {

    private val stopSearch = AtomicBoolean(false)

    constructor() : this(HostDefaults.approveAlways(), HostDefaults.logPrinter("FakePrinter"))

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

        if (config.isManual) {
            return CardPresence.manual("4111111111111111").also(listener::onManualEntrySelected)
        }
        if (config.isMagstripe) {
            return CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null)
                .also(listener::onMagstripeDetected)
        }
        if (config.isContactless) {
            return CardPresence.contactless(byteArrayOf(0x01, 0x02)).also(listener::onContactlessDetected)
        }
        // CONTACT and ANY → chip first (demo)
        if (config.allowsChip()) {
            return CardPresence.chip().also(listener::onChipDetected)
        }
        if (config.allowsContactless()) {
            return CardPresence.contactless(byteArrayOf(0x01, 0x02)).also(listener::onContactlessDetected)
        }
        if (config.allowsMagstripe()) {
            return CardPresence.magstripe("CARD HOLDER", "4111111111111111=4912", null)
                .also(listener::onMagstripeDetected)
        }
        if (config.allowsManual()) {
            return CardPresence.manual("4111111111111111").also(listener::onManualEntrySelected)
        }

        listener.onSearchTimeout()
        return null
    }

    override fun cancelCardSearch() {
        stopSearch.set(true)
    }

    private companion object {
        const val SEARCH_DELAY_MS = 200L
    }
}

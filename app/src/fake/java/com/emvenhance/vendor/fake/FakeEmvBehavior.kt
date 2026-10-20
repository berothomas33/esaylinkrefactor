package com.emvenhance.vendor.fake

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.AbstractEmvBehavior

/**
 * Fake vendor: each step does its work, then [goToStep] publishes it on the engine and runs the
 * next vendor method — on the transaction thread, blocking, like a real kernel.
 */
class FakeEmvBehavior : AbstractEmvBehavior() {

    private var lastAuth: AuthResult? = null

    override fun onTerminalInitialization(engine: EmvEngine, config: TransactionConfig) {
        lastAuth = null
        // Stop here — PosTerminal.searchCard runs next. Do NOT goToStep.
    }

    override fun onApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        if (card.isContactless) {
            // Abbreviated CLSS: skip wait/final/ODA/… → read → complete offline
            goToStep(EmvStep.READ_APPLICATION_DATA, "contactless")
            return
        }
        goToStep(EmvStep.WAIT_APPLICATION_SELECTION)
    }

    override fun onWaitApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.FINAL_APPLICATION_SELECTION)
    }

    override fun onFinalApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED))
        goToStep(EmvStep.READ_APPLICATION_DATA)
    }

    override fun onReadApplicationData(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        when {
            card.isChip -> engine.notifyTransactionStep(
                cardRead("4111111111111111", "Demo Bank", "CARD HOLDER", "Contact"),
            )
            card.isContactless -> engine.notifyTransactionStep(
                cardRead("5555444433332222", "Demo Bank", "CL HOLDER", "Contactless"),
            )
            card.isMagstripe -> {
                val pan = card.track2?.split(Regex("[=D]"))?.get(0) ?: "4111111111111111"
                engine.notifyTransactionStep(cardRead(pan, "MAG Bank", "", "Magstripe"))
            }
            card.isManual -> engine.notifyTransactionStep(cardRead(card.manualPan ?: "", "MANUAL", "", "Manual"))
        }
        sleep(200)

        if (card.isContactless) {
            goToStep(EmvStep.TRANSACTION_COMPLETION)
            return
        }
        if (isSynchronousEntry(card)) {
            goToStep(EmvStep.START_ONLINE_PROCESS)
            return
        }
        goToStep(EmvStep.SET_TRANSACTION_DATA)
    }

    override fun onSetTransactionData(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION)
    }

    override fun onOfflineDataAuthentication(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.PROCESS_RESTRICTIONS)
    }

    override fun onProcessRestrictions(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.CARDHOLDER_VERIFICATION)
    }

    override fun onCardholderVerification(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.OFFLINE_PIN_VERIFICATION)
    }

    override fun onOfflinePinVerification(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        engine.notifyTransactionStep(
            TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, true)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, true)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, 3)
                .build(),
        )
        goToStep(EmvStep.TERMINAL_RISK_MANAGEMENT)
    }

    override fun onTerminalRiskManagement(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.TERMINAL_ACTION_ANALYSIS)
    }

    override fun onTerminalActionAnalysis(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.START_ONLINE_PROCESS)
    }

    override fun onStartOnlineProcess(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        if (isCancelled()) {
            finishError("Cancelled")
            return
        }
        lastAuth = authorizeOnline()
        if (card.isChip) {
            goToStep(EmvStep.ISSUER_AUTHENTICATION)
        } else {
            goToStep(EmvStep.TRANSACTION_COMPLETION)
        }
    }

    /**
     * Demo card data (no kernel): a fixed ARQC (9F26), CID 80 (9F27), ATC (9F36) and IAD (9F10),
     * so the online request has the same shape as a real card's.
     */
    override fun readIccTag(tag: Int): ByteArray = when (tag) {
        0x9F26 -> byteArrayOf(0xF4.toByte(), 0x3D, 0x16, 0x6E, 0xC3.toByte(), 0x61, 0xA9.toByte(), 0xB2.toByte())
        0x9F27 -> byteArrayOf(0x80.toByte())
        0x9F36 -> byteArrayOf(0x00, 0x01)
        0x9F10 -> byteArrayOf(0x01, 0x10, 0xA5.toByte(), 0x00, 0x0F, 0x04, 0x00, 0x00)
        else -> ByteArray(0)
    }

    override fun onIssuerAuthentication(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.SCRIPT_PROCESSING)
    }

    override fun onScriptProcessing(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.TRANSACTION_COMPLETION)
    }

    override fun onTransactionCompletion(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
        val auth = lastAuth
        when {
            card.isContactless && auth == null -> finishApproved("OFFLINE APPROVED")
            auth != null && auth.isApproved -> finishApproved("ONLINE APPROVED")
            auth != null -> finishDeclined(auth.message ?: "DECLINED")
            else -> finishApproved("APPROVED")
        }
    }

    private companion object {
        fun cardRead(pan: String, issuer: String, holder: String, mode: String): TransactionStepEvent =
            TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, pan)
                .put(TransactionStepEvent.KEY_ISSUER_NAME, issuer)
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, holder)
                .put(TransactionStepEvent.KEY_MODE, mode)
                .build()

        fun sleep(ms: Long) {
            try {
                Thread.sleep(ms)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }
}

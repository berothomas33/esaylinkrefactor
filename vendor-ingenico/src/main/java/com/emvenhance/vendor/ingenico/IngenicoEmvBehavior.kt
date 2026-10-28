package com.emvenhance.vendor.ingenico

import android.util.Log
import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.AbstractEmvBehavior

/**
 * Ingenico stub — each step ends with [goToStep] (engine → next method).
 */
class IngenicoEmvBehavior : AbstractEmvBehavior() {

    private var lastAuth: AuthResult? = null
    private var stubPan: String? = null
    private var stubMode: String? = null

    override fun onTerminalInitialization(config: TransactionConfig) {
        lastAuth = null
        Log.w(TAG, "Ingenico EMV SDK not attached — stub init")
        // Do not goToStep — PosTerminal searches next.
    }

    override fun firstStepAfterSearch(card: CardPresence): EmvStep {
        stubMode = card.modeLabel
        stubPan = when {
            card.isChip -> "4111111111111111"
            card.isContactless -> "5555444433332222"
            card.isMagstripe -> card.track2?.split(Regex("[=D]"))?.get(0) ?: "4111111111111111"
            else -> card.manualPan ?: ""
        }
        return EmvStep.APPLICATION_SELECTION
    }

    override fun onApplicationSelection(config: TransactionConfig, card: CardPresence) {
        goToStep(EmvStep.READ_APPLICATION_DATA, "ingenico-$stubMode")
    }

    // ─── Not reached — the stub jumps APPLICATION_SELECTION → READ_APPLICATION_DATA →
    // START_ONLINE_PROCESS → TRANSACTION_COMPLETION directly above and below. These 11 exist
    // because EmvBehavior requires every phase to be a conscious decision, not a silent
    // inherited no-op. Wire real Ingenico SDK candidate-selection / CVM / risk-management
    // logic into these when the SDK is attached, instead of deleting them. ─────────────────

    override fun onWaitApplicationSelection(config: TransactionConfig, card: CardPresence) {
        // Not reached: no AID candidate list — the stub has exactly one application.
    }

    override fun onFinalApplicationSelection(config: TransactionConfig, card: CardPresence) {
        // Not reached: see onWaitApplicationSelection.
    }

    override fun onSetTransactionData(config: TransactionConfig, card: CardPresence) {
        // Not reached: no TLV set-up beyond the stub PAN/amount already in TransactionConfig.
    }

    override fun onOfflineDataAuthentication(config: TransactionConfig, card: CardPresence) {
        // Not reached: the stub does not simulate SDA/DDA/CDA.
    }

    override fun onProcessRestrictions(config: TransactionConfig, card: CardPresence) {
        // Not reached: no AID/application usage-control checks in the stub.
    }

    override fun onCardholderVerification(config: TransactionConfig, card: CardPresence) {
        // Not reached: the stub always goes straight to online, no CVM list processing.
    }

    override fun onOfflinePinVerification(config: TransactionConfig, card: CardPresence) {
        // Not reached: see onCardholderVerification.
    }

    override fun onTerminalRiskManagement(config: TransactionConfig, card: CardPresence) {
        // Not reached: no floor limit / velocity checking in the stub.
    }

    override fun onTerminalActionAnalysis(config: TransactionConfig, card: CardPresence) {
        // Not reached: the stub always requests online, so there is no TAA/GAC decision.
    }

    override fun onIssuerAuthentication(config: TransactionConfig, card: CardPresence) {
        // Not reached: the stub's online result is final; no issuer authentication data (91) to check.
    }

    override fun onScriptProcessing(config: TransactionConfig, card: CardPresence) {
        // Not reached: the stub host never returns issuer scripts (71/72).
    }

    override fun onReadApplicationData(config: TransactionConfig, card: CardPresence) {
        publish(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED))
        publish(
            TransactionStepEvent.builder(TransactionStep.CARD_READ)
                .put(TransactionStepEvent.KEY_PAN, stubPan ?: "")
                .put(TransactionStepEvent.KEY_ISSUER_NAME, "Ingenico Stub")
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                .put(TransactionStepEvent.KEY_MODE, stubMode ?: card.modeLabel)
                .build(),
        )
        goToStep(EmvStep.START_ONLINE_PROCESS)
    }

    override fun onStartOnlineProcess(config: TransactionConfig, card: CardPresence) {
        lastAuth = authorizeOnline()
        goToStep(EmvStep.TRANSACTION_COMPLETION)
    }

    /**
     * Stub until the Ingenico SDK is attached: then return the tag's value from its kernel. Only
     * the cryptogram tags are faked here, so the online request isn't empty.
     */
    override fun readIccTag(tag: Int): ByteArray = when (tag) {
        0x9F26 -> byteArrayOf(0xF4.toByte(), 0x3D, 0x16, 0x6E, 0xC3.toByte(), 0x61, 0xA9.toByte(), 0xB2.toByte())
        0x9F27 -> byteArrayOf(0x80.toByte())
        0x9F36 -> byteArrayOf(0x00, 0x01)
        else -> ByteArray(0)
    }

    override fun onTransactionCompletion(config: TransactionConfig, card: CardPresence) {
        val auth = lastAuth
        if (auth != null && auth.isApproved) {
            finishApproved("INGENICO STUB APPROVED")
        } else {
            finishDeclined(auth?.message ?: "DECLINED")
        }
    }

    private companion object {
        const val TAG = "IngenicoEmvBehavior"
    }
}

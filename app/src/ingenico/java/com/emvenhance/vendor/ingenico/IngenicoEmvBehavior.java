package com.emvenhance.vendor.ingenico;

import android.util.Log;
import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.AbstractEmvBehavior;

/**
 * Ingenico stub — each step ends with {@link #goToStep} (EmvStep observable → next method).
 */
public class IngenicoEmvBehavior extends AbstractEmvBehavior {

    private static final String TAG = "IngenicoEmvBehavior";

    @Nullable
    private AuthResult lastAuth;
    @Nullable
    private String stubPan;
    @Nullable
    private String stubMode;

    @Override
    public void onTerminalInitialization(EmvEngine engine, TransactionConfig config) {
        lastAuth = null;
        Log.w(TAG, "Ingenico EMV SDK not attached — stub init");
        // Do not goToStep — PosTerminal searches next.
    }

    @Override
    protected EmvStep firstStepAfterSearch(CardPresence card) {
        stubMode = card.getModeLabel();
        if (card.isChip()) {
            stubPan = "4111111111111111";
        } else if (card.isContactless()) {
            stubPan = "5555444433332222";
        } else if (card.isMagstripe()) {
            stubPan = card.getTrack2() != null
                    ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
        } else {
            stubPan = card.getManualPan() != null ? card.getManualPan() : "";
        }
        return EmvStep.APPLICATION_SELECTION;
    }

    @Override
    public void onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.READ_APPLICATION_DATA, "ingenico-" + stubMode);
    }

    // ─── Not reached — the stub jumps APPLICATION_SELECTION → READ_APPLICATION_DATA →
    // START_ONLINE_PROCESS → TRANSACTION_COMPLETION directly above and below. These 11 exist
    // because EmvBehavior requires every phase to be a conscious decision, not a silent
    // inherited no-op. Wire real Ingenico SDK candidate-selection / CVM / risk-management
    // logic into these when the SDK is attached, instead of deleting them. ─────────────────

    @Override
    public void onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: no AID candidate list — the stub has exactly one application.
    }

    @Override
    public void onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: see onWaitApplicationSelection.
    }

    @Override
    public void onSetTransactionData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: no TLV set-up beyond the stub PAN/amount already in TransactionConfig.
    }

    @Override
    public void onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: the stub does not simulate SDA/DDA/CDA.
    }

    @Override
    public void onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: no AID/application usage-control checks in the stub.
    }

    @Override
    public void onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: the stub always goes straight to online, no CVM list processing.
    }

    @Override
    public void onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: see onCardholderVerification.
    }

    @Override
    public void onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: no floor limit / velocity checking in the stub.
    }

    @Override
    public void onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: the stub always requests online, so there is no TAA/GAC decision.
    }

    @Override
    public void onIssuerAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: the stub's online result is final; no issuer authentication data (91) to check.
    }

    @Override
    public void onScriptProcessing(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Not reached: the stub host never returns issuer scripts (71/72).
    }

    @Override
    public void onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        engine.notifyCardDetected(stubPan != null ? stubPan : "", "Ingenico Stub", "",
                stubMode != null ? stubMode : card.getModeLabel());
        goToStep(EmvStep.START_ONLINE_PROCESS);
    }

    @Override
    public void onStartOnlineProcess(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (isCancelled()) {
            finishError("Cancelled");
            return;
        }
        lastAuth = engine.authorize(config);
        goToStep(EmvStep.TRANSACTION_COMPLETION);
    }

    @Override
    public void onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (lastAuth != null && lastAuth.isApproved()) {
            finishApproved("INGENICO STUB APPROVED");
        } else {
            finishDeclined(lastAuth != null && lastAuth.getMessage() != null
                    ? lastAuth.getMessage() : "DECLINED");
        }
    }
}

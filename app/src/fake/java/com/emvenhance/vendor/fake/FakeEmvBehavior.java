package com.emvenhance.vendor.fake;

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
 * Fake vendor: each step does its work, then {@link #goToStep} publishes on the EmvStep
 * observable and runs the next vendor method.
 */
public class FakeEmvBehavior extends AbstractEmvBehavior {

    @Nullable
    private AuthResult lastAuth;

    @Override
    public void onTerminalInitialization(EmvEngine engine, TransactionConfig config) {
        lastAuth = null;
        // Stop here — PosTerminal.searchCard runs next. Do NOT goToStep.
    }

    @Override
    public void onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isContactless()) {
            // Abbreviated CLSS: skip wait/final/ODA/… → read → complete offline
            goToStep(EmvStep.READ_APPLICATION_DATA, "contactless");
            return;
        }
        goToStep(EmvStep.WAIT_APPLICATION_SELECTION);
    }

    @Override
    public void onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.FINAL_APPLICATION_SELECTION);
    }

    @Override
    public void onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        goToStep(EmvStep.READ_APPLICATION_DATA);
    }

    @Override
    public void onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isChip()) {
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, "4111111111111111")
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "Demo Bank")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "CARD HOLDER")
                    .put(TransactionStepEvent.KEY_MODE, "Contact")
                    .build());
        } else if (card.isContactless()) {
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, "5555444433332222")
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "Demo Bank")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "CL HOLDER")
                    .put(TransactionStepEvent.KEY_MODE, "Contactless")
                    .build());
        } else if (card.isMagstripe()) {
            String pan = card.getTrack2() != null
                    ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MAG Bank")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, "Magstripe")
                    .build());
        } else if (card.isManual()) {
            String pan = card.getManualPan() != null ? card.getManualPan() : "";
            engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_READ)
                    .put(TransactionStepEvent.KEY_PAN, pan)
                    .put(TransactionStepEvent.KEY_ISSUER_NAME, "MANUAL")
                    .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, "")
                    .put(TransactionStepEvent.KEY_MODE, "Manual")
                    .build());
        }
        sleep(200);

        if (card.isContactless()) {
            goToStep(EmvStep.TRANSACTION_COMPLETION);
            return;
        }
        if (isSynchronousEntry(card)) {
            goToStep(EmvStep.START_ONLINE_PROCESS);
            return;
        }
        goToStep(EmvStep.SET_TRANSACTION_DATA);
    }

    @Override
    public void onSetTransactionData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION);
    }

    @Override
    public void onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.PROCESS_RESTRICTIONS);
    }

    @Override
    public void onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.CARDHOLDER_VERIFICATION);
    }

    @Override
    public void onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.OFFLINE_PIN_VERIFICATION);
    }

    @Override
    public void onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, true)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, true)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, 3)
                .build());
        goToStep(EmvStep.TERMINAL_RISK_MANAGEMENT);
    }

    @Override
    public void onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.TERMINAL_ACTION_ANALYSIS);
    }

    @Override
    public void onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
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
        if (card.isChip()) {
            goToStep(EmvStep.ISSUER_AUTHENTICATION);
        } else {
            goToStep(EmvStep.TRANSACTION_COMPLETION);
        }
    }

    @Override
    public void onIssuerAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.SCRIPT_PROCESSING);
    }

    @Override
    public void onScriptProcessing(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        goToStep(EmvStep.TRANSACTION_COMPLETION);
    }

    @Override
    public void onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isContactless() && lastAuth == null) {
            finishApproved("OFFLINE APPROVED");
            return;
        }
        if (lastAuth != null && lastAuth.isApproved()) {
            finishApproved("ONLINE APPROVED");
        } else if (lastAuth != null) {
            finishDeclined(lastAuth.getMessage() != null ? lastAuth.getMessage() : "DECLINED");
        } else {
            finishApproved("APPROVED");
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

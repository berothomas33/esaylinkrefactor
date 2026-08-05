package com.emvenhance.vendor.fake;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.AbstractEmvBehavior;
import com.emvenhance.core.terminal.EmvStepResult;

/**
 * Fake vendor — each EMV phase is an overridden step method on {@link AbstractEmvBehavior}.
 * Online goes through {@link EmvEngine#authorize}.
 */
public class FakeEmvBehavior extends AbstractEmvBehavior {

    @Nullable
    private AuthResult lastAuth;
    private boolean emitIssuerSteps;

    @Override
    public EmvStepResult onTerminalInitialization(EmvEngine engine, TransactionConfig config) {
        lastAuth = null;
        emitIssuerSteps = false;
        return EmvStepResult.continueResult();
    }

    @Override
    protected void runContactlessFlow(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        // Abbreviated offline CLSS demo
        if (!run(com.emvenhance.core.event.EmvStep.APPLICATION_SELECTION,
                () -> onApplicationSelection(engine, config, card))) {
            return;
        }
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        if (!run(com.emvenhance.core.event.EmvStep.READ_APPLICATION_DATA,
                () -> onReadApplicationData(engine, config, card))) {
            return;
        }
        run(com.emvenhance.core.event.EmvStep.TRANSACTION_COMPLETION,
                () -> EmvStepResult.approved("OFFLINE APPROVED"));
    }

    @Override
    public EmvStepResult onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult(card.isContactless() ? "contactless" : null);
    }

    @Override
    public EmvStepResult onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (card.isChip()) {
            engine.notifyCardDetected("4111111111111111", "Demo Bank", "CARD HOLDER", "Contact");
        } else if (card.isContactless()) {
            engine.notifyCardDetected("5555444433332222", "Demo Bank", "CL HOLDER", "Contactless");
        } else if (card.isMagstripe()) {
            String pan = card.getTrack2() != null
                    ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
            engine.notifyCardDetected(pan, "MAG Bank", "", "Magstripe");
        } else if (card.isManual()) {
            String pan = card.getManualPan() != null ? card.getManualPan() : "";
            engine.notifyCardDetected(pan, "MANUAL", "", "Manual");
        }
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        sleep(200);
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onSetTransactionData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult("DDA");
    }

    @Override
    public EmvStepResult onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, true)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, true)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, 3)
                .build());
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        emitIssuerSteps = card.isChip();
        return EmvStepResult.continueResult();
    }

    @Override
    protected boolean shouldRunIssuerSteps() {
        return emitIssuerSteps;
    }

    @Override
    public EmvStepResult onStartOnlineProcess(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (isCancelled()) {
            return EmvStepResult.fail("Cancelled");
        }
        lastAuth = engine.authorize(config);
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onIssuerAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (!emitIssuerSteps) {
            return EmvStepResult.skip("No issuer auth for this path");
        }
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onScriptProcessing(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (!emitIssuerSteps) {
            return EmvStepResult.skip("No scripts for this path");
        }
        return EmvStepResult.continueResult();
    }

    @Override
    public EmvStepResult onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (lastAuth != null) {
            if (lastAuth.isApproved()) {
                return EmvStepResult.approved("ONLINE APPROVED");
            }
            return EmvStepResult.declined(lastAuth.getMessage() != null
                    ? lastAuth.getMessage() : "DECLINED");
        }
        return EmvStepResult.approved("APPROVED");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

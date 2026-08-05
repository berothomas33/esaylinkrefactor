package com.emvenhance.vendor.ingenico;

import android.util.Log;
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
 * Ingenico stub — extends {@link AbstractEmvBehavior} so each EMV step is an overridable
 * method. Replace step bodies when the Ingenico SDK is wired.
 */
public class IngenicoEmvBehavior extends AbstractEmvBehavior {

    private static final String TAG = "IngenicoEmvBehavior";

    @Nullable
    private AuthResult lastAuth;

    @Override
    public EmvStepResult onTerminalInitialization(EmvEngine engine, TransactionConfig config) {
        lastAuth = null;
        Log.w(TAG, "Ingenico EMV SDK not attached — stub prepare");
        return EmvStepResult.continueResult("Ingenico stub");
    }

    @Override
    protected void runContactFlow(EmvEngine engine, TransactionConfig config, CardPresence card) {
        stubAbbreviated(engine, config, card, "Chip", "4111111111111111");
    }

    @Override
    protected void runContactlessFlow(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        stubAbbreviated(engine, config, card, "Contactless", "5555444433332222");
    }

    @Override
    protected void runMagstripeFlow(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        String pan = card.getTrack2() != null
                ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
        stubAbbreviated(engine, config, card, "Magstripe", pan);
    }

    @Override
    protected void runManualFlow(EmvEngine engine, TransactionConfig config, CardPresence card) {
        stubAbbreviated(engine, config, card, "Manual",
                card.getManualPan() != null ? card.getManualPan() : "");
    }

    private void stubAbbreviated(EmvEngine engine, TransactionConfig config, CardPresence card,
            String mode, String pan) {
        if (!run(com.emvenhance.core.event.EmvStep.APPLICATION_SELECTION,
                () -> onApplicationSelection(engine, config, card))) {
            return;
        }
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        if (!run(com.emvenhance.core.event.EmvStep.READ_APPLICATION_DATA, () -> {
            engine.notifyCardDetected(pan, "Ingenico Stub", "", mode);
            engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
            return EmvStepResult.continueResult();
        })) {
            return;
        }
        if (!run(com.emvenhance.core.event.EmvStep.START_ONLINE_PROCESS,
                () -> onStartOnlineProcess(engine, config, card))) {
            return;
        }
        run(com.emvenhance.core.event.EmvStep.TRANSACTION_COMPLETION,
                () -> onTransactionCompletion(engine, config, card));
    }

    @Override
    public EmvStepResult onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        return EmvStepResult.continueResult("ingenico-" + card.getModeLabel());
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
    public EmvStepResult onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (lastAuth != null && lastAuth.isApproved()) {
            return EmvStepResult.approved("INGENICO STUB APPROVED");
        }
        return EmvStepResult.declined(lastAuth != null && lastAuth.getMessage() != null
                ? lastAuth.getMessage() : "DECLINED");
    }
}

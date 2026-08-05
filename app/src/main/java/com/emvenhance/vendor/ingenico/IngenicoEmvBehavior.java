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

    @Override
    public void onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        engine.notifyCardDetected(stubPan != null ? stubPan : "", "Ingenico Stub", "",
                stubMode != null ? stubMode : card.getModeLabel());
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
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

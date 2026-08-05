package com.emvenhance.vendor.fake;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.EmvBehavior;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fake vendor EMV behavior. Online goes through {@link EmvEngine#authorize} — the engine
 * forwards to whatever {@link com.emvenhance.core.host.CommunicationBehavior}
 * {@link FakeTerminal} attached. This class never holds that reference itself.
 */
public class FakeEmvBehavior implements EmvBehavior {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    private TransactionConfig activeConfig;

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.activeConfig = config;
        cancelled.set(false);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        engine.notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION);
        return true;
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        this.activeConfig = config;
        cancelled.set(false);
        if (cancelled.get()) {
            return;
        }
        if (card.isManual()) {
            executeManual(engine, card);
        } else if (card.isContactless()) {
            executeContactless(engine);
        } else if (card.isMagstripe()) {
            executeMagstripe(engine, card);
        } else {
            executeContact(engine);
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    private void executeContact(EmvEngine engine) {
        engine.notifyEmvStep(EmvStep.APPLICATION_SELECTION);
        engine.notifyEmvStep(EmvStep.WAIT_APPLICATION_SELECTION);
        engine.notifyEmvStep(EmvStep.FINAL_APPLICATION_SELECTION);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));

        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA);
        engine.notifyEmvStep(EmvStep.SET_TRANSACTION_DATA);
        engine.notifyCardDetected("4111111111111111", "Demo Bank", "CARD HOLDER", "Contact");
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        sleep(200);

        engine.notifyEmvStep(EmvStep.OFFLINE_DATA_AUTHENTICATION, "DDA");
        engine.notifyEmvStep(EmvStep.PROCESS_RESTRICTIONS);
        engine.notifyEmvStep(EmvStep.CARDHOLDER_VERIFICATION);
        engine.notifyEmvStep(EmvStep.OFFLINE_PIN_VERIFICATION);
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, true)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, true)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, 3)
                .build());

        engine.notifyEmvStep(EmvStep.TERMINAL_RISK_MANAGEMENT);
        engine.notifyEmvStep(EmvStep.TERMINAL_ACTION_ANALYSIS);
        finishOnline(engine, true);
    }

    private void executeContactless(EmvEngine engine) {
        engine.notifyEmvStep(EmvStep.APPLICATION_SELECTION, "contactless");
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));
        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA);
        engine.notifyCardDetected("5555444433332222", "Demo Bank", "CL HOLDER", "Contactless");
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        sleep(200);
        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        engine.notifyApproved("OFFLINE APPROVED");
        engine.notifyCompleted();
    }

    private void executeMagstripe(EmvEngine engine, CardPresence card) {
        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA, "magstripe");
        String pan = card.getTrack2() != null ? card.getTrack2().split("[=D]")[0] : "4111111111111111";
        engine.notifyCardDetected(pan, "MAG Bank", "", "Magstripe");
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        finishOnline(engine, false);
    }

    private void executeManual(EmvEngine engine, CardPresence card) {
        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA, "manual");
        String pan = card.getManualPan() != null ? card.getManualPan() : "";
        engine.notifyCardDetected(pan, "MANUAL", "", "Manual");
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        finishOnline(engine, false);
    }

    private void finishOnline(EmvEngine engine, boolean emitIssuerSteps) {
        engine.notifyEmvStep(EmvStep.START_ONLINE_PROCESS);
        AuthResult auth = authorize(engine);

        if (emitIssuerSteps) {
            engine.notifyEmvStep(EmvStep.ISSUER_AUTHENTICATION);
            engine.notifyEmvStep(EmvStep.SCRIPT_PROCESSING);
        }
        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth.isApproved()) {
            engine.notifyApproved("ONLINE APPROVED");
        } else {
            engine.notifyDeclined(auth.getMessage());
        }
        engine.notifyCompleted();
    }

    private AuthResult authorize(EmvEngine engine) {
        if (cancelled.get()) {
            return AuthResult.declined("17", "Cancelled");
        }
        return engine.authorize(activeConfig);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

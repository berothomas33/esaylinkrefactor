package com.emvenhance.vendor;

import com.emvenhance.core.AbstractEmvBehavior;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.CardPresence;
import com.emvenhance.core.CardReader;
import com.emvenhance.core.CommunicationBehavior;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.PrinterBehavior;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fake vendor EMV behavior — full lifecycle without a real SDK.
 */
public class FakeEmvBehavior extends AbstractEmvBehavior {

    private final AtomicReference<AuthResult> pendingAuth = new AtomicReference<>();
    private volatile CountDownLatch authLatch;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public FakeEmvBehavior(CommunicationBehavior communication, PrinterBehavior printer) {
        super(communication, printer);
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardReader cardReader) {
        this.engine = engine;
        this.activeConfig = config;
        cancelled.set(false);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        engine.notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD,
                config.isContact() ? "Insert card"
                        : config.isMagstripe() ? "Swipe card" : "Tap card"));
        engine.notifyEmvStep(EmvStep.SEARCH_CARD);

        CardPresence card = cardReader.searchCard(config);
        if (cardReader.isSearchCancelled() || cancelled.get()) {
            return;
        }
        if (card == null) {
            engine.notifyError("Card search timeout");
            return;
        }

        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                .build());

        if (card.isContactless()) {
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
        CountDownLatch latch = authLatch;
        if (latch != null) {
            pendingAuth.compareAndSet(null, AuthResult.declined("17", "Cancelled"));
            latch.countDown();
        }
    }

    @Override
    protected void deliverOnlineResult(AuthResult authResult) {
        pendingAuth.set(authResult);
        CountDownLatch latch = authLatch;
        if (latch != null) {
            latch.countDown();
        }
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
        awaitOnlineAndFinish(engine, true);
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
        awaitOnlineAndFinish(engine, false);
    }

    private void awaitOnlineAndFinish(EmvEngine engine, boolean emitIssuerSteps) {
        engine.notifyEmvStep(EmvStep.START_ONLINE_PROCESS);
        pendingAuth.set(null);
        authLatch = new CountDownLatch(1);
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));

        AuthResult auth;
        try {
            authLatch.await();
            auth = pendingAuth.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            auth = null;
        } finally {
            authLatch = null;
        }

        if (emitIssuerSteps) {
            engine.notifyEmvStep(EmvStep.ISSUER_AUTHENTICATION);
            engine.notifyEmvStep(EmvStep.SCRIPT_PROCESSING);
        }
        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION);
        if (auth != null && auth.isApproved()) {
            engine.notifyApproved("ONLINE APPROVED");
        } else {
            engine.notifyDeclined(auth != null ? auth.getMessage() : "Declined");
        }
        engine.notifyCompleted();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

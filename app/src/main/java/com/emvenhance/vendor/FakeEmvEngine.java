package com.emvenhance.vendor;

import com.emvenhance.core.AuthResult;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;

/**
 * Simulated EMV engine so the architecture can be exercised off a real terminal.
 *
 * <p>Notice there is no {@code Callback} parameter anywhere. The vendor implementation
 * drives progress by calling the protected {@code emit} methods inherited from
 * {@link EmvEngine}. Consumers never appear in this code — the subjects handle delivery.
 */
public class FakeEmvEngine extends EmvEngine {

    private TransactionConfig config;

    @Override
    public boolean prepare(TransactionConfig config) {
        if (!acquireRunning()) {
            emitError("A transaction is already running");
            return false;
        }
        this.config = config;

        emitTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        emitEmvStep(EmvStep.TERMINAL_INITIALIZATION);

        emitTransactionStep(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD,
                config.isContact() ? "Insert card" : "Tap card"));
        return true;
    }

    @Override
    public void execute() {
        try {
            if (config.isContact()) {
                executeContact();
            } else {
                executeContactless();
            }
        } catch (Exception e) {
            emitError(e.getMessage());
            releaseRunning();
        }
    }

    private void executeContact() {
        // ── Card detection ───────────────────────────────────────
        emitEmvStep(EmvStep.SEARCH_CARD);
        sleep(200);
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_DETECTED));

        // ── Application selection ────────────────────────────────
        emitEmvStep(EmvStep.APPLICATION_SELECTION);
        emitEmvStep(EmvStep.WAIT_APPLICATION_SELECTION);
        emitEmvStep(EmvStep.FINAL_APPLICATION_SELECTION);
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));

        // ── Read application data ────────────────────────────────
        emitEmvStep(EmvStep.READ_APPLICATION_DATA);
        emitEmvStep(EmvStep.SET_TRANSACTION_DATA);
        emitCardDetected("4111111111111111", "Demo Bank", "CARD HOLDER", "Contact");
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        sleep(300);

        // ── Offline data authentication ──────────────────────────
        emitEmvStep(EmvStep.OFFLINE_DATA_AUTHENTICATION, "DDA");
        emitEmvStep(EmvStep.PROCESS_RESTRICTIONS);

        // ── CVM ──────────────────────────────────────────────────
        emitEmvStep(EmvStep.CARDHOLDER_VERIFICATION);
        emitEmvStep(EmvStep.OFFLINE_PIN_VERIFICATION);
        emitTransactionStep(TransactionStepEvent.builder(TransactionStep.CARDHOLDER_VERIFIED)
                .put(TransactionStepEvent.KEY_ONLINE_PIN, true)
                .put(TransactionStepEvent.KEY_PIN_BYPASS, true)
                .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT, 3)
                .build());
        sleep(200);

        // ── Risk management & action analysis ────────────────────
        emitEmvStep(EmvStep.TERMINAL_RISK_MANAGEMENT);
        emitEmvStep(EmvStep.TERMINAL_ACTION_ANALYSIS);

        // ── Online required (PosTerminal takes over from here) ───
        emitEmvStep(EmvStep.START_ONLINE_PROCESS);
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        // Method returns; PosTerminal calls complete() with the AuthResult.
    }

    private void executeContactless() {
        emitEmvStep(EmvStep.SEARCH_CARD);
        sleep(200);
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_DETECTED));

        emitEmvStep(EmvStep.APPLICATION_SELECTION, "contactless");
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));

        emitEmvStep(EmvStep.READ_APPLICATION_DATA);
        emitCardDetected("5555444433332222", "Demo Bank", "CL HOLDER", "Contactless");
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.CARD_READ));
        sleep(200);

        // Offline approval — no online step, straight to result.
        emitApproved("OFFLINE APPROVED");
        emitTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        releaseRunning();
    }

    @Override
    public void complete(AuthResult authResult) {
        try {
            emitEmvStep(EmvStep.ISSUER_AUTHENTICATION);
            emitEmvStep(EmvStep.SCRIPT_PROCESSING);
            emitEmvStep(EmvStep.TRANSACTION_COMPLETION);

            if (authResult.isApproved()) {
                emitApproved("ONLINE APPROVED");
            } else {
                emitDeclined(authResult.getMessage());
            }
            emitTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        } finally {
            releaseRunning();
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

package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.AuthResult;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;

/**
 * PAX vendor implementation of the EMV engine — owns the transaction lifecycle.
 *
 * <p>All PAX SDK I/O is delegated to {@link PaxEmvBehavior}, the single bridge between the
 * PAX processing layer and this engine. Every kernel callback is translated by the behavior
 * into {@link #emitEmvStep} / {@link #emitTransactionStep} emissions, which
 * {@link com.emvenhance.core.PosTerminal} routes through {@code dispatchEmvStep(...)}.
 *
 * <pre>
 *   PosTerminal.startTransaction
 *     → prepare()  → PaxEmvBehavior.prepare (EmvPreProcessFacade)
 *     → execute()  → PaxEmvBehavior.executeContact / executeContactless
 *                      → PAX callbacks → emitEmvStep / emitTransactionStep
 *                      → ONLINE_REQUIRED → (blocks until complete)
 *     → complete() → PaxEmvBehavior.deliverAuthResult (unblocks online)
 * </pre>
 */
public class PaxEmvEngine extends EmvEngine {

    private final PaxEmvBehavior behavior = new PaxEmvBehavior(this);

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

        if (!behavior.prepare(config)) {
            emitError("Terminal initialization failed");
            releaseRunning();
            return false;
        }

        emitTransactionStep(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD,
                config.isContact() ? "Insert card" : "Tap card"));
        return true;
    }

    @Override
    public void execute() {
        try {
            if (config.isContact()) {
                behavior.executeContact();
            } else {
                behavior.executeContactless();
            }
        } catch (Exception e) {
            reportError(e.getMessage() != null ? e.getMessage() : "EMV execution failed");
        }
    }

    /**
     * Feeds the host authorization result back into the blocked PAX online callback.
     *
     * <p>Issuer authentication, script processing, and the final approved/declined emissions
     * are produced by {@link PaxEmvBehavior} as the PAX kernel continues and the result
     * listener fires.
     */
    @Override
    public void complete(AuthResult authResult) {
        behavior.deliverAuthResult(authResult);
    }

    // ─── Package bridge: PaxEmvBehavior → EmvEngine subjects ─────────────

    void reportEmvStep(EmvStep step, @Nullable String detail) {
        emitEmvStep(step, detail);
    }

    void reportTransactionStep(TransactionStepEvent event) {
        emitTransactionStep(event);
        if (event.getStep() == TransactionStep.COMPLETED) {
            releaseRunning();
        }
    }

    void reportCardDetected(String pan, String issuerName, String cardHolderName, String mode) {
        emitCardDetected(pan, issuerName, cardHolderName, mode);
    }

    void reportApproved(String result) {
        emitApproved(result);
    }

    void reportDeclined(String reason) {
        emitDeclined(reason);
    }

    void reportError(String error) {
        emitError(error);
        releaseRunning();
    }
}

package com.emvenhance.vendor;

import com.emvenhance.core.AuthResult;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;
import com.emvenhance.core.TransactionConfig;
import com.emvenhance.core.TransactionStep;
import com.emvenhance.core.TransactionStepEvent;

/**
 * PAX vendor implementation of the EMV engine.
 *
 * <p>This class bridges the PAX kernel's callback-based SDK to the reactive subject model.
 * Internally it still receives callbacks from the PAX kernel (that is the only way the
 * kernel communicates), but it <em>translates</em> each callback into a subject emission
 * rather than forwarding to a {@code Callback} interface.  From the architecture's
 * perspective, no callback crosses the core boundary.
 *
 * <h3>How the PAX bridge works</h3>
 *
 * <pre>
 *   PAX kernel thread
 *     → ContactProcess callback (emvWaitAppSel, emvSetParam, onCardHolderPwd, …)
 *       → this class calls emitEmvStep() / emitTransactionStep()
 *         → subjects deliver to PosTerminal and UI subscribers
 * </pre>
 *
 * <p>The callback lives inside this class as an anonymous implementation of the PAX
 * interfaces — it is an internal detail, not an architectural boundary.
 */
public class PaxEmvEngine extends EmvEngine {

    // TODO inject via constructor:
    //   - EmvPreProcessFacade (or its dependencies)
    //   - ContactEmvRunner / ContactlessEmvRunner
    //   - EmvStepProgress (now emits into subjects instead of Callback)

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

        // TODO: call EmvPreProcessFacade.start() here
        // byte searchMode = new EmvPreProcessFacade(
        //         config.getProcCode(), config.getAmountMinor(), timestamp(),
        //         0, buildSearchMode(config)).start();
        // if (searchMode == 0) {
        //     emitError("preTransProcess disabled every search mode");
        //     releaseRunning();
        //     return false;
        // }

        emitTransactionStep(TransactionStepEvent.of(TransactionStep.WAITING_FOR_CARD));
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
        // TODO: replace with real PAX ContactEmvRunner that pushes into subjects.
        //
        // The PAX kernel calls IContactCallback methods on its own thread.
        // Each callback translates to an emit call:
        //
        //   @Override
        //   public int onWaitAppSelect(...) {
        //       emitEmvStep(EmvStep.WAIT_APPLICATION_SELECTION);
        //       emitTransactionStep(TransactionStepEvent.of(
        //               TransactionStep.APPLICATION_SELECTED));
        //       // return selected index
        //   }
        //
        //   @Override
        //   public int onCardHolderPwd(boolean online, boolean bypass, int left, byte[] pin) {
        //       emitEmvStep(EmvStep.CARDHOLDER_VERIFICATION);
        //       emitTransactionStep(TransactionStepEvent.builder(
        //               TransactionStep.CARDHOLDER_VERIFIED)
        //               .put(KEY_ONLINE_PIN, online)
        //               .put(KEY_PIN_BYPASS, bypass)
        //               .put(KEY_PIN_TRIES_LEFT, left)
        //               .build());
        //       // return PIN result
        //   }
        //
        //   @Override
        //   public OnlineResultWrapper startOnlineProcess() {
        //       emitEmvStep(EmvStep.START_ONLINE_PROCESS);
        //       emitTransactionStep(TransactionStepEvent.of(
        //               TransactionStep.ONLINE_REQUIRED));
        //       // block until PosTerminal calls complete()
        //   }

        throw new UnsupportedOperationException(
                "Wire PAX ContactEmvRunner here — see comments for the callback→subject mapping");
    }

    private void executeContactless() {
        // TODO: same pattern as executeContact() but using ContactlessEmvRunner
        // and IContactlessCallback → subject mapping.
        throw new UnsupportedOperationException(
                "Wire PAX ContactlessEmvRunner here");
    }

    @Override
    public void complete(AuthResult authResult) {
        try {
            emitEmvStep(EmvStep.ISSUER_AUTHENTICATION);
            emitEmvStep(EmvStep.SCRIPT_PROCESSING);
            emitEmvStep(EmvStep.TRANSACTION_COMPLETION);

            // TODO: call EMVCallback.EMVCompleteTrans() with the AuthResult data
            //   IssuerRspData rsp = new IssuerRspData();
            //   rsp.setAuthCode(authResult.getAuthCode());
            //   rsp.setIssuerScript(authResult.getIssuerScript());
            //   int ret = emvContactService.completeTransProcess(rsp);

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
}

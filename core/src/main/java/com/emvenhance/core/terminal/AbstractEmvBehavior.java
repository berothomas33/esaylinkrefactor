package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Template-method runner for {@link EmvBehavior} step hooks.
 *
 * <p>Vendors extend this class and override only the {@code onXxx} step methods they need.
 * {@link #prepare} runs {@link #onTerminalInitialization}; {@link #start} runs the EMV
 * sequence for the selected entry method by calling each step method in order.
 *
 * <p>Kernel-callback vendors (e.g. PAX) may keep implementing {@link EmvBehavior} directly
 * with a custom {@code start}; they can still override the same {@code onXxx} hooks later.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    protected final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    protected EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = null;
        cancelled.set(false);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        EmvStepResult result = executeStep(EmvStep.TERMINAL_INITIALIZATION,
                () -> onTerminalInitialization(engine, config));
        return result.shouldContinue();
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = card;
        cancelled.set(false);

        EmvStepResult search = executeStep(EmvStep.SEARCH_CARD,
                () -> onSearchCard(engine, config, card));
        if (!search.shouldContinue()) {
            applyTerminalResult(search);
            return;
        }

        if (card.isManual()) {
            runManualFlow(engine, config, card);
        } else if (card.isContactless()) {
            runContactlessFlow(engine, config, card);
        } else if (card.isMagstripe()) {
            runMagstripeFlow(engine, config, card);
        } else {
            runContactFlow(engine, config, card);
        }
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    /** Full contact chip sequence. */
    protected void runContactFlow(EmvEngine engine, TransactionConfig config, CardPresence card) {
        if (!run(EmvStep.APPLICATION_SELECTION,
                () -> onApplicationSelection(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.WAIT_APPLICATION_SELECTION,
                () -> onWaitApplicationSelection(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.FINAL_APPLICATION_SELECTION,
                () -> onFinalApplicationSelection(engine, config, card))) {
            return;
        }
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.APPLICATION_SELECTED));

        if (!run(EmvStep.READ_APPLICATION_DATA,
                () -> onReadApplicationData(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.SET_TRANSACTION_DATA,
                () -> onSetTransactionData(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.OFFLINE_DATA_AUTHENTICATION,
                () -> onOfflineDataAuthentication(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.PROCESS_RESTRICTIONS,
                () -> onProcessRestrictions(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.CARDHOLDER_VERIFICATION,
                () -> onCardholderVerification(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.OFFLINE_PIN_VERIFICATION,
                () -> onOfflinePinVerification(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.TERMINAL_RISK_MANAGEMENT,
                () -> onTerminalRiskManagement(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.TERMINAL_ACTION_ANALYSIS,
                () -> onTerminalActionAnalysis(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.START_ONLINE_PROCESS,
                () -> onStartOnlineProcess(engine, config, card))) {
            return;
        }
        if (shouldRunIssuerSteps()) {
            if (!run(EmvStep.ISSUER_AUTHENTICATION,
                    () -> onIssuerAuthentication(engine, config, card))) {
                return;
            }
            if (!run(EmvStep.SCRIPT_PROCESSING,
                    () -> onScriptProcessing(engine, config, card))) {
                return;
            }
        }
        run(EmvStep.TRANSACTION_COMPLETION,
                () -> onTransactionCompletion(engine, config, card));
    }

    /**
     * Whether contact flow should run issuer auth + scripts after online.
     * Vendors override (e.g. Fake sets this after terminal action analysis).
     */
    protected boolean shouldRunIssuerSteps() {
        return true;
    }

    /**
     * Contactless sequence — default = full contact flow.
     * Override to shorten (e.g. Fake offline path).
     */
    protected void runContactlessFlow(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        runContactFlow(engine, config, card);
    }

    /** Magstripe: read → online → complete (skips chip-only phases by default via overrides). */
    protected void runMagstripeFlow(EmvEngine engine, TransactionConfig config,
            CardPresence card) {
        if (!run(EmvStep.READ_APPLICATION_DATA,
                () -> onReadApplicationData(engine, config, card))) {
            return;
        }
        if (!run(EmvStep.START_ONLINE_PROCESS,
                () -> onStartOnlineProcess(engine, config, card))) {
            return;
        }
        run(EmvStep.TRANSACTION_COMPLETION,
                () -> onTransactionCompletion(engine, config, card));
    }

    /** Manual entry: same abbreviated path as magstripe. */
    protected void runManualFlow(EmvEngine engine, TransactionConfig config, CardPresence card) {
        runMagstripeFlow(engine, config, card);
    }

    /**
     * Runs one step: notifies {@link EmvStep}, invokes the vendor method, handles the result.
     *
     * @return {@code true} if the flow should continue to the next step
     */
    protected final boolean run(EmvStep step, StepAction action) {
        if (cancelled.get()) {
            if (engine != null && engine.isRunning()) {
                engine.notifyError("Transaction cancelled");
            }
            return false;
        }
        EmvStepResult result = executeStep(step, action);
        if (result.shouldContinue()) {
            return true;
        }
        applyTerminalResult(result);
        return false;
    }

    protected final EmvStepResult executeStep(EmvStep step, StepAction action) {
        if (engine == null) {
            return EmvStepResult.fail("No EmvEngine");
        }
        if (cancelled.get()) {
            return EmvStepResult.fail("Transaction cancelled");
        }
        engine.notifyEmvStep(step);
        try {
            EmvStepResult result = action.run();
            return result != null ? result : EmvStepResult.continueResult();
        } catch (Exception e) {
            return EmvStepResult.fail(e.getMessage() != null ? e.getMessage() : step.getTitle());
        }
    }

    protected final void applyTerminalResult(EmvStepResult result) {
        if (engine == null) {
            return;
        }
        switch (result.getStatus()) {
            case APPROVED:
                engine.notifyApproved(result.getDetail() != null ? result.getDetail() : "APPROVED");
                engine.notifyCompleted();
                break;
            case DECLINED:
                engine.notifyDeclined(result.getDetail() != null ? result.getDetail() : "DECLINED");
                engine.notifyCompleted();
                break;
            case FAIL:
                engine.notifyError(result.getDetail() != null ? result.getDetail() : "EMV failed");
                break;
            default:
                break;
        }
    }

    protected final boolean isCancelled() {
        return cancelled.get();
    }

    @FunctionalInterface
    protected interface StepAction {
        EmvStepResult run() throws Exception;
    }
}

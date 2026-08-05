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
 * Vendor base: each step method does vendor work, then calls {@link #goToStep} to
 * publish on the EmvStep observable and run the next step method.
 *
 * <pre>
 *   onApplicationSelection(...) {
 *       // vendor work…
 *       goToStep(EmvStep.WAIT_APPLICATION_SELECTION);  // → observable + next method
 *   }
 * </pre>
 *
 * <p>{@link #prepare} only starts {@link EmvStep#TERMINAL_INITIALIZATION} (does not
 * chain further — PosTerminal searches next). {@link #start} kicks the first post-search
 * step; the vendor chain continues via {@link #goToStep}.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    protected final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    protected EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;

    @Nullable
    private EmvStep currentStep;

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = null;
        this.currentStep = null;
        cancelled.set(false);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        enterStep(EmvStep.TERMINAL_INITIALIZATION);
        return !cancelled.get() && engine.isRunning();
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = card;
        cancelled.set(false);

        // First post-search step — vendor methods chain via goToStep from here.
        goToStep(firstStepAfterSearch(card));
    }

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    /**
     * Vendor finished the current step → publish {@code step} on the EmvStep observable
     * and execute that step's {@code onXxx} method.
     */
    protected final void goToStep(EmvStep step) {
        goToStep(step, null);
    }

    protected final void goToStep(EmvStep step, @Nullable String detail) {
        if (cancelled.get()) {
            if (engine != null && engine.isRunning()) {
                engine.notifyError("Transaction cancelled");
            }
            return;
        }
        if (engine == null || activeConfig == null) {
            return;
        }
        enterStep(step, detail);
    }

    /**
     * Publish {@code step} on the EmvStep observable <em>without</em> invoking the vendor
     * {@code onXxx} method. Use from kernel callbacks (PAX) where the L2 process already
     * owns the phase — only the UI/observable needs to advance.
     */
    protected final void announceStep(EmvStep step) {
        announceStep(step, null);
    }

    protected final void announceStep(EmvStep step, @Nullable String detail) {
        if (engine == null) {
            return;
        }
        currentStep = step;
        engine.notifyEmvStep(step, detail);
    }

    /** Convenience: advance to the default next EMV step after {@link #currentStep()}. */
    protected final void goToNextLevel() {
        EmvStep next = defaultNext(currentStep);
        if (next == null) {
            return;
        }
        goToStep(next);
    }

    @Nullable
    protected final EmvStep currentStep() {
        return currentStep;
    }

    protected final void finishApproved(String message) {
        if (engine == null) {
            return;
        }
        engine.notifyApproved(message);
        engine.notifyCompleted();
    }

    protected final void finishDeclined(String message) {
        if (engine == null) {
            return;
        }
        engine.notifyDeclined(message);
        engine.notifyCompleted();
    }

    protected final void finishError(String message) {
        if (engine == null) {
            return;
        }
        engine.notifyError(message);
    }

    protected final boolean isCancelled() {
        return cancelled.get();
    }

    /** Which step {@link #start} enters after card search. Override per entry method. */
    protected EmvStep firstStepAfterSearch(CardPresence card) {
        if (card.isMagstripe() || card.isManual()) {
            return EmvStep.READ_APPLICATION_DATA;
        }
        return EmvStep.APPLICATION_SELECTION;
    }

    /**
     * Default linear next-step map (contact). Vendors usually call {@link #goToStep}
     * with an explicit next step instead of relying on this.
     */
    @Nullable
    protected EmvStep defaultNext(@Nullable EmvStep from) {
        if (from == null) {
            return EmvStep.APPLICATION_SELECTION;
        }
        switch (from) {
            case TERMINAL_INITIALIZATION:
                return EmvStep.SEARCH_CARD;
            case SEARCH_CARD:
                return EmvStep.APPLICATION_SELECTION;
            case APPLICATION_SELECTION:
                return EmvStep.WAIT_APPLICATION_SELECTION;
            case WAIT_APPLICATION_SELECTION:
                return EmvStep.FINAL_APPLICATION_SELECTION;
            case FINAL_APPLICATION_SELECTION:
                return EmvStep.READ_APPLICATION_DATA;
            case READ_APPLICATION_DATA:
                return EmvStep.SET_TRANSACTION_DATA;
            case SET_TRANSACTION_DATA:
                return EmvStep.OFFLINE_DATA_AUTHENTICATION;
            case OFFLINE_DATA_AUTHENTICATION:
                return EmvStep.PROCESS_RESTRICTIONS;
            case PROCESS_RESTRICTIONS:
                return EmvStep.CARDHOLDER_VERIFICATION;
            case CARDHOLDER_VERIFICATION:
                return EmvStep.OFFLINE_PIN_VERIFICATION;
            case OFFLINE_PIN_VERIFICATION:
                return EmvStep.TERMINAL_RISK_MANAGEMENT;
            case TERMINAL_RISK_MANAGEMENT:
                return EmvStep.TERMINAL_ACTION_ANALYSIS;
            case TERMINAL_ACTION_ANALYSIS:
                return EmvStep.START_ONLINE_PROCESS;
            case START_ONLINE_PROCESS:
                return EmvStep.ISSUER_AUTHENTICATION;
            case ISSUER_AUTHENTICATION:
                return EmvStep.SCRIPT_PROCESSING;
            case SCRIPT_PROCESSING:
                return EmvStep.TRANSACTION_COMPLETION;
            case TRANSACTION_COMPLETION:
            default:
                return null;
        }
    }

    private void enterStep(EmvStep step) {
        enterStep(step, null);
    }

    private void enterStep(EmvStep step, @Nullable String detail) {
        currentStep = step;
        engine.notifyEmvStep(step, detail); // EmvStep observable → UI + dispatchEmvStep
        if (cancelled.get()) {
            return;
        }
        try {
            dispatchStepMethod(step);
        } catch (Exception e) {
            finishError(e.getMessage() != null ? e.getMessage() : step.getTitle());
        }
    }

    private void dispatchStepMethod(EmvStep step) {
        TransactionConfig config = activeConfig;
        CardPresence card = activeCard;
        EmvEngine eng = engine;
        if (eng == null || config == null) {
            return;
        }

        switch (step) {
            case TERMINAL_INITIALIZATION:
                onTerminalInitialization(eng, config);
                break;
            case SEARCH_CARD:
                onSearchCard(eng, config, card);
                break;
            case APPLICATION_SELECTION:
                onApplicationSelection(eng, config, card);
                break;
            case WAIT_APPLICATION_SELECTION:
                onWaitApplicationSelection(eng, config, card);
                break;
            case FINAL_APPLICATION_SELECTION:
                onFinalApplicationSelection(eng, config, card);
                break;
            case READ_APPLICATION_DATA:
                onReadApplicationData(eng, config, card);
                break;
            case SET_TRANSACTION_DATA:
                onSetTransactionData(eng, config, card);
                break;
            case OFFLINE_DATA_AUTHENTICATION:
                onOfflineDataAuthentication(eng, config, card);
                break;
            case PROCESS_RESTRICTIONS:
                onProcessRestrictions(eng, config, card);
                break;
            case CARDHOLDER_VERIFICATION:
                onCardholderVerification(eng, config, card);
                break;
            case OFFLINE_PIN_VERIFICATION:
                onOfflinePinVerification(eng, config, card);
                break;
            case TERMINAL_RISK_MANAGEMENT:
                onTerminalRiskManagement(eng, config, card);
                break;
            case TERMINAL_ACTION_ANALYSIS:
                onTerminalActionAnalysis(eng, config, card);
                break;
            case START_ONLINE_PROCESS:
                onStartOnlineProcess(eng, config, card);
                break;
            case ISSUER_AUTHENTICATION:
                onIssuerAuthentication(eng, config, card);
                break;
            case SCRIPT_PROCESSING:
                onScriptProcessing(eng, config, card);
                break;
            case TRANSACTION_COMPLETION:
                onTransactionCompletion(eng, config, card);
                break;
            default:
                finishError("Unknown EMV step: " + step);
                break;
        }
    }
}

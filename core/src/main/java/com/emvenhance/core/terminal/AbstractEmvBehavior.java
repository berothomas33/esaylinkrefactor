package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.emv.Field55;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.emv.EmvEngine;
import com.emvenhance.core.emv.EmvStep;
import com.emvenhance.core.emv.EmvStepEvent;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.EmvLog;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.observers.DisposableObserver;

public abstract class AbstractEmvBehavior implements EmvBehavior {

    private EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;
    @Nullable
    private PosTerminal.Transaction transaction;

    private final CompositeDisposable disposables = new CompositeDisposable();

    public final void attach(EmvEngine engine) {
        this.engine = engine;
    }

    public final EmvEngine engine() {
        return engine;
    }

    @Override
    public boolean prepare(TransactionConfig config, PosTerminal.Transaction transaction) {
        this.transaction = transaction;
        initEmvObserver();
        this.activeConfig = config;
        this.activeCard = null;

        enterStep(EmvStep.TERMINAL_INITIALIZATION);
        return isRunning();
    }

    @Override
    public void start(TransactionConfig config, CardPresence card,
            PosTerminal.Transaction transaction) {
        this.transaction = transaction;
        this.activeConfig = config;
        this.activeCard = card;

        goToStep(firstStepAfterSearch(card));
    }

    @Override
    public void dispose() {
        disposables.clear();
    }

    protected final void initEmvObserver() {
        disposables.clear();
        disposables.add(engine.emvSteps().subscribeWith(new EmvStepObserver()));
    }

    protected final boolean goToStep(EmvStep step) {
        return goToStep(step, null);
    }

    protected final boolean goToStep(@Nullable EmvStep step, @Nullable String detail) {
        if (step == null) {
            unknownStep(null);
            return false;
        }
        if (activeConfig == null) {
            return false;
        }
        return enterStep(step, detail);
    }

    protected final int askStep(EmvStep step, @Nullable String detail, int failClosed) {
        StepAnswer outer = stepAnswer;
        StepAnswer answer = new StepAnswer();
        stepAnswer = answer;
        try {
            goToStep(step, detail);
        } finally {
            stepAnswer = outer;
        }
        boolean ended = !isRunning();
        if (!answer.answered || ended) {
            engine.apduTrace().note("EMV", (answer.answered
                    ? step.getTitle() + " answered after the transaction ended"
                    : "no answer for " + step.getTitle()) + " — answering " + failClosed);
            return failClosed;
        }
        return answer.value;
    }

    protected final void answerStep(int value) {
        if (stepAnswer == null) {
            return;
        }
        stepAnswer.value = value;
        stepAnswer.answered = true;
    }

    @Nullable
    private StepAnswer stepAnswer;

    private static final class StepAnswer {
        int value;
        boolean answered;
    }

    protected final void finishApproved(String message) {
        finishApproved(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, message)
                .build());
    }

    protected final void finishApproved(TransactionStepEvent approved) {
        emvFinished(approved);
    }

    protected final void finishDeclined(String message) {
        finishDeclined(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, message)
                .build());
    }

    protected final void finishDeclined(TransactionStepEvent declined) {
        emvFinished(declined);
    }

    protected final void finishError(String message) {
        emvFinished(PosTerminal.errorEvent(message));
    }

    private void emvFinished(TransactionStepEvent outcome) {
        if (transaction == null) {
            EmvLog.d("EMV outcome dropped, no transaction: " + outcome);
            return;
        }
        transaction.emvFinished(outcome);
    }

    protected final void publish(TransactionStepEvent event) {
        if (transaction == null) {
            EmvLog.d("TransactionStep dropped, no transaction: " + event);
            return;
        }
        TransactionStep step = event.getStep();
        if (step.isEnd() || step == TransactionStep.APPROVED || step == TransactionStep.DECLINED) {
            EmvLog.d("TransactionStep dropped, an outcome goes through finish*: " + event);
            engine().apduTrace().note("EMV",
                    "transaction step dropped — an outcome goes through finish*: " + event);
            return;
        }
        transaction.publish(event);
    }

    protected final boolean isRunning() {
        return transaction != null && transaction.isRunning();
    }

    protected final boolean isCancelled() {
        return transaction != null && transaction.isCancelled();
    }

    protected final AuthResult authorizeOnline() {
        if (activeConfig == null) {
            return AuthResult.failed("Online failed: no active transaction");
        }
        if (isCancelled()) {
            return AuthResult.declined("17", "Cancelled");
        }
        TransactionConfig config = activeConfig;
        if (activeCard != null && !isSynchronousEntry(activeCard)) {
            String iccData = buildField55();
            if (iccData.isEmpty()) {
                return AuthResult.failed("Online failed: no chip data (field 55) to send");
            }
            config = config.withIccData(iccData);
        }
        config = addOnlineRequestData(config);
        activeConfig = config;
        return engine.authorize(config);
    }

    protected final String buildField55() {
        Field55 field55 = Field55.read(this::readIccTag);
        String source = activeCard == null ? null
                : activeCard.isContactless() ? "contactless" : "contact";
        String summary = field55.describe(source);
        EmvLog.d(summary);
        engine.apduTrace().note("EMV", summary);
        return field55.hex();
    }

    protected TransactionConfig addOnlineRequestData(TransactionConfig config) {
        return config;
    }

    protected boolean isSynchronousEntry(CardPresence card) {
        return card.isMagstripe() || card.isManual();
    }

    protected EmvStep firstStepAfterSearch(CardPresence card) {
        if (isSynchronousEntry(card)) {
            return EmvStep.READ_APPLICATION_DATA;
        }
        return EmvStep.APPLICATION_SELECTION;
    }

    protected abstract void onTerminalInitialization(TransactionConfig config);

    protected void onSearchCard(TransactionConfig config, CardPresence card) {
    }

    protected abstract void onApplicationSelection(TransactionConfig config, CardPresence card);

    protected abstract void onWaitApplicationSelection(TransactionConfig config, CardPresence card);

    protected abstract void onFinalApplicationSelection(TransactionConfig config,
            CardPresence card);

    protected abstract void onReadApplicationData(TransactionConfig config, CardPresence card);

    protected abstract void onSetTransactionData(TransactionConfig config, CardPresence card);

    protected abstract void onOfflineDataAuthentication(TransactionConfig config,
            CardPresence card);

    protected abstract void onProcessRestrictions(TransactionConfig config, CardPresence card);

    protected abstract void onCardholderVerification(TransactionConfig config, CardPresence card);

    protected abstract void onOfflinePinVerification(TransactionConfig config, CardPresence card);

    protected abstract void onTerminalRiskManagement(TransactionConfig config, CardPresence card);

    protected abstract void onTerminalActionAnalysis(TransactionConfig config, CardPresence card);

    protected abstract void onStartOnlineProcess(TransactionConfig config, CardPresence card);

    protected abstract void onIssuerAuthentication(TransactionConfig config, CardPresence card);

    protected abstract void onScriptProcessing(TransactionConfig config, CardPresence card);

    protected abstract void onTransactionCompletion(TransactionConfig config, CardPresence card);

    protected abstract byte[] readIccTag(int tag);

    private void enterStep(EmvStep step) {
        enterStep(step, null);
    }

    private boolean enterStep(EmvStep step, @Nullable String detail) {
        if (!isRunning()) {
            EmvStepEvent event = new EmvStepEvent(step, detail);
            String why = isCancelled() ? "cancelled" : "the transaction has ended";
            EmvLog.d("EmvStep dropped, " + why + ": " + event);
            engine.apduTrace().note("EMV", "step dropped, " + why + ": " + event);
            return false;
        }
        engine.notifyEmvStep(step, detail);
        return true;
    }

    private final class EmvStepObserver extends DisposableObserver<EmvStepEvent> {

        @Override
        public void onNext(EmvStepEvent event) {
            EmvStep step = event.getStep();
            try {
                dispatchStepMethod(step);
            } catch (Exception e) {
                finishError(e.getMessage() != null ? e.getMessage() : step.getTitle());
            }
        }

        @Override
        public void onError(Throwable error) {
            finishError(error.getMessage() != null ? error.getMessage() : "EMV step failed");
        }

        @Override
        public void onComplete() {
        }
    }

    private void dispatchStepMethod(EmvStep step) {
        if (activeConfig == null) {
            return;
        }

        switch (step) {
            case TERMINAL_INITIALIZATION:
                onTerminalInitialization(activeConfig);
                break;
            case SEARCH_CARD:
                onSearchCard(activeConfig, activeCard);
                break;
            case APPLICATION_SELECTION:
                onApplicationSelection(activeConfig, activeCard);
                break;
            case WAIT_APPLICATION_SELECTION:
                onWaitApplicationSelection(activeConfig, activeCard);
                break;
            case FINAL_APPLICATION_SELECTION:
                onFinalApplicationSelection(activeConfig, activeCard);
                break;
            case READ_APPLICATION_DATA:
                onReadApplicationData(activeConfig, activeCard);
                break;
            case SET_TRANSACTION_DATA:
                onSetTransactionData(activeConfig, activeCard);
                break;
            case OFFLINE_DATA_AUTHENTICATION:
                onOfflineDataAuthentication(activeConfig, activeCard);
                break;
            case PROCESS_RESTRICTIONS:
                onProcessRestrictions(activeConfig, activeCard);
                break;
            case CARDHOLDER_VERIFICATION:
                onCardholderVerification(activeConfig, activeCard);
                break;
            case OFFLINE_PIN_VERIFICATION:
                onOfflinePinVerification(activeConfig, activeCard);
                break;
            case TERMINAL_RISK_MANAGEMENT:
                onTerminalRiskManagement(activeConfig, activeCard);
                break;
            case TERMINAL_ACTION_ANALYSIS:
                onTerminalActionAnalysis(activeConfig, activeCard);
                break;
            case START_ONLINE_PROCESS:
                onStartOnlineProcess(activeConfig, activeCard);
                break;
            case ISSUER_AUTHENTICATION:
                onIssuerAuthentication(activeConfig, activeCard);
                break;
            case SCRIPT_PROCESSING:
                onScriptProcessing(activeConfig, activeCard);
                break;
            case TRANSACTION_COMPLETION:
                onTransactionCompletion(activeConfig, activeCard);
                break;
            default:
                unknownStep(step);
                break;
        }
    }

    private void unknownStep(@Nullable EmvStep step) {
        if (!isRunning()) {
            return;
        }
        finishError("Unknown EMV step: " + step);
    }
}

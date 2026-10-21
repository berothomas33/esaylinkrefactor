package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.Field55;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.EmvLog;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.observers.DisposableObserver;

/**
 * Vendor base: each step method does vendor work, then calls {@link #goToStep} to
 * publish the next step on the EmvStep observable.
 *
 * <pre>
 *   onApplicationSelection(...) {
 *       // vendor work…
 *       goToStep(EmvStep.WAIT_APPLICATION_SELECTION);  // → observable → its onXxx
 *   }
 * </pre>
 *
 * <p>{@link #dispatchStepMethod} is the consumer of {@link EmvEngine#emvSteps()}: this class's
 * own observer ({@link EmvStepObserver}) hands it every step published there — by
 * {@link #goToStep}, from a kernel callback, or by PosTerminal's card search — and it runs that
 * step's {@code onXxx} method. Nothing calls a step method directly. A phase the kernel runs
 * internally is still published with {@link #goToStep}; its {@code onXxx} is then the place that
 * says so, with nothing to run.
 *
 * <p>{@link #prepare} only starts {@link EmvStep#TERMINAL_INITIALIZATION} (does not
 * chain further — PosTerminal searches next). {@link #start} kicks the first post-search
 * step; the vendor chain continues via {@link #goToStep}.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    @Nullable
    protected EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;

    /** This behavior's {@link EmvEngine#emvSteps()} subscription — see {@link #initEmvObserver}. */
    private final CompositeDisposable disposables = new CompositeDisposable();

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
        initEmvObserver(engine);
        this.activeConfig = config;
        this.activeCard = null;

        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        enterStep(EmvStep.TERMINAL_INITIALIZATION);
        return engine.isRunning(); // false once it ended: init failed, or cancelled
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        this.engine = engine;
        initEmvObserver(engine);
        this.activeConfig = config;
        this.activeCard = card;

        // First post-search step — vendor methods chain via goToStep from here.
        goToStep(firstStepAfterSearch(card));
    }

    @Override
    public void dispose() {
        disposables.clear();
    }

    /**
     * Subscribes {@link EmvStepObserver} to {@code engine}'s EmvStep observable, replacing any
     * earlier subscription.
     *
     * <p>Called at the start of every {@link #prepare} and {@link #start}, not once: the observer
     * must be the engine's <em>last</em> subscriber. A step method runs inside the observer and
     * publishes the next step from there, so every subscriber after it would see that next step
     * before the current one — the UI would end on the first step instead of the last.
     * Re-subscribing moves it behind every subscriber that came first, such as a
     * {@code MainViewModel} created after an earlier transaction.
     *
     * <p>Once a transaction has ended ({@link TransactionStep#isEnd()}) the engine publishes no
     * step until the next {@link EmvEngine#begin()}, so a step still published by code unwinding
     * never runs a step method; the next {@link #prepare} subscribes again.
     */
    protected final void initEmvObserver(EmvEngine engine) {
        disposables.clear();
        // No subscribeOn / observeOn: see EmvStepObserver.
        disposables.add(engine.emvSteps().subscribeWith(new EmvStepObserver()));
    }

    /**
     * Vendor finished the current step → publish {@code step} on the EmvStep observable, whose
     * observer runs that step's {@code onXxx} method before this returns.
     *
     * <p>No cancel check of its own: once the transaction is cancelled (or ended) the engine
     * publishes no step ({@link EmvEngine#notifyEmvStep}), so nothing runs.
     *
     * @return whether the step ran — {@code false} once the transaction is cancelled or ended,
     *         so a vendor holding a resource across steps (the ICC reader) knows to release it
     */
    protected final boolean goToStep(EmvStep step) {
        return goToStep(step, null);
    }

    protected final boolean goToStep(@Nullable EmvStep step, @Nullable String detail) {
        if (step == null) {
            unknownStep(null);
            return false;
        }
        if (engine == null || activeConfig == null) {
            return false;
        }
        return enterStep(step, detail);
    }

    /**
     * For a kernel callback that waits on an answer from the cardholder (application selection,
     * PIN entry): publishes {@code step} like {@link #goToStep}, and returns what that step's
     * {@code onXxx} method gave to {@link #answerStep} — the step method does the UI work, the
     * callback only hands its answer back to the kernel.
     *
     * <p>Works because the step method runs before {@code goToStep} returns (see
     * {@link EmvStepObserver}). If it doesn't answer — the transaction ended or was cancelled so
     * nothing ran, the method threw, or it simply never called {@link #answerStep} — this returns
     * {@code failClosed} and notes it in the APDU trace: the kernel always gets an answer and
     * never waits forever.
     */
    protected final int askStep(EmvStep step, @Nullable String detail, int failClosed) {
        StepAnswer outer = stepAnswer;
        StepAnswer answer = new StepAnswer();
        stepAnswer = answer;
        try {
            goToStep(step, detail);
        } finally {
            stepAnswer = outer;
        }
        if (!answer.answered) {
            if (engine != null) {
                engine.apduTrace().note("EMV", "no answer for " + step.getTitle()
                        + " — answering " + failClosed);
            }
            return failClosed;
        }
        return answer.value;
    }

    /** The answer to the {@link #askStep} running this step method. */
    protected final void answerStep(int value) {
        if (stepAnswer == null) {
            // Not asked: a plain goToStep (or no step at all) has no one waiting on an answer.
            return;
        }
        stepAnswer.value = value;
        stepAnswer.answered = true;
    }

    /** The answer the innermost {@link #askStep} is waiting for; null when nobody is asking. */
    @Nullable
    private StepAnswer stepAnswer;

    private static final class StepAnswer {
        int value;
        boolean answered;
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

    /** Whether {@code PosTerminal} cancelled the current transaction (see {@link EmvEngine#isCancelled()}). */
    protected final boolean isCancelled() {
        return engine != null && engine.isCancelled();
    }

    /**
     * The one way a vendor goes online. For chip and contactless it first builds field 55 from
     * the vendor's {@link #readIccTag} ({@link #buildField55}) and attaches it to
     * {@link #activeConfig}, so no request reaches the host without it; with no tag at all the
     * request is online failed instead. Then {@link #addOnlineRequestData} lets the vendor
     * attach anything else it read, and {@link EmvEngine#authorize} hands it to
     * {@link PosTerminal#authorize}, which sends it to the host.
     *
     * <p>Call it right after the 1st GENERATE AC returned the ARQC, when the kernel asks to go
     * online (the PAX kernel's {@code startOnlineProcess} callback, or {@code onStartOnlineProcess}).
     */
    protected final AuthResult authorizeOnline() {
        if (engine == null || activeConfig == null) {
            return AuthResult.failed("Online failed: no active transaction");
        }
        if (isCancelled()) {
            return AuthResult.declined("17", "Cancelled");
        }
        // Built up here and stored once: an early return leaves activeConfig as it was.
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

    /**
     * Field 55 as hex BER-TLV, read tag by tag through {@link #readIccTag}. Every tag sent (name
     * and value) and every tag missing goes to the APDU trace and the debug log. Empty when the
     * kernel had none of the tags.
     */
    protected final String buildField55() {
        Field55 field55 = Field55.read(this::readIccTag);
        String source = activeCard == null ? null
                : activeCard.isContactless() ? "contactless" : "contact";
        String summary = field55.describe(source);
        EmvLog.d(summary);
        if (engine != null) {
            engine.apduTrace().note("EMV", summary);
        }
        return field55.hex();
    }

    /**
     * Vendor extras for the online request (PAN, PIN block, EMV result, ...), added to
     * {@code config} after field 55 is attached. Default: nothing to add.
     */
    protected TransactionConfig addOnlineRequestData(TransactionConfig config) {
        return config;
    }

    /**
     * True for entry methods with no EMV kernel involved at all — magstripe and manual PAN
     * entry. Both {@link #firstStepAfterSearch} and a vendor's own step methods (typically
     * {@code onReadApplicationData}, {@code onStartOnlineProcess}, {@code onTransactionCompletion})
     * ask this same question to jump straight past the EMV-only phases; one named, overridable
     * predicate here instead of {@code card.isMagstripe() || card.isManual()} repeated at every
     * one of those call sites.
     */
    protected boolean isSynchronousEntry(CardPresence card) {
        return card.isMagstripe() || card.isManual();
    }

    /** Which step {@link #start} enters after card search. Override per entry method. */
    protected EmvStep firstStepAfterSearch(CardPresence card) {
        if (isSynchronousEntry(card)) {
            return EmvStep.READ_APPLICATION_DATA;
        }
        return EmvStep.APPLICATION_SELECTION;
    }

    // ─── EMV step methods — every vendor writes these ─────────────────────

    // Each EMV phase is a method, and — with the single exception of onSearchCard — every one of
    // them is abstract: a vendor class that doesn't write all of them fails to compile. This is
    // deliberate. A silently inherited no-op is indistinguishable from "the developer forgot
    // this phase"; a compile error is not. For a phase your kernel drives internally, the kernel
    // callback still publishes it with goToStep (so the UI and the APDU trace see it), and the
    // correct body is a one-line comment saying the kernel already did it — a real, considered
    // answer, not a stub.
    //
    // They are protected, not on EmvBehavior: only dispatchStepMethod runs them, for a step
    // published on the EmvStep observable. Nothing holding the engine's EmvBehavior can run one.

    protected abstract void onTerminalInitialization(EmvEngine engine, TransactionConfig config);

    /**
     * The one step method that stays optional. {@link PosTerminal#searchCard} is the real,
     * mandatory search for every vendor; this runs when {@code SEARCH_CARD} is published — by
     * PosTerminal, with its prompt — and has nothing left to do. Requiring a body here would
     * force an empty one in every vendor for no benefit.
     */
    protected void onSearchCard(EmvEngine engine, TransactionConfig config, CardPresence card) {
    }

    protected abstract void onApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onWaitApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onFinalApplicationSelection(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onReadApplicationData(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onSetTransactionData(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onOfflineDataAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onProcessRestrictions(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onCardholderVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onOfflinePinVerification(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onTerminalRiskManagement(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onTerminalActionAnalysis(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onStartOnlineProcess(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onIssuerAuthentication(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onScriptProcessing(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    protected abstract void onTransactionCompletion(EmvEngine engine, TransactionConfig config,
            CardPresence card);

    // ─── Field 55 — every vendor writes this ──────────────────────────────

    /**
     * One tag's value bytes, read from this vendor's own kernel in whatever way its SDK offers;
     * empty (or {@code null}) when the kernel has no value for it. That's all a vendor does for
     * field 55: {@link #authorizeOnline} picks the tags
     * ({@link com.emvenhance.core.card.Field55}), encodes them and logs them.
     *
     * <p>Called right after the 1st GENERATE AC returned the ARQC, so the card's cryptogram
     * data (9F26, 9F27, 9F10, 9F36) is there to read. Never called for magstripe or manual.
     */
    protected abstract byte[] readIccTag(int tag);

    private void enterStep(EmvStep step) {
        enterStep(step, null);
    }

    private boolean enterStep(EmvStep step, @Nullable String detail) {
        // EmvStep observable → UI, then EmvStepObserver → dispatchStepMethod → onXxx.
        return engine.notifyEmvStep(step, detail);
    }

    /**
     * The {@link #initEmvObserver} observer: hands every step published on
     * {@link EmvEngine#emvSteps()} to {@link #dispatchStepMethod}.
     *
     * <p>Runs on the thread that published the step, before {@code goToStep} returns — there is
     * deliberately no {@code subscribeOn} / {@code observeOn} (and {@code emvSteps} is never
     * serialized). Vendor kernels call {@code goToStep} from inside their own callbacks and expect
     * the step to have run when it returns; {@code PosTerminal} reads {@link #prepare}'s result
     * and any requested retry the moment {@link #prepare} / {@link #start} return; and a step
     * method blocks (APDUs, PIN entry, the host), which a {@code computation} thread must never do.
     */
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
            // emvSteps never errors: nothing calls onError on it.
            finishError(error.getMessage() != null ? error.getMessage() : "EMV step failed");
        }

        @Override
        public void onComplete() {
            // emvSteps never completes: it lives as long as the engine, and the UI keeps
            // observing it from one transaction to the next. After a transaction's end the engine
            // publishes nothing more, and the next prepare re-subscribes (initEmvObserver).
        }
    }

    /**
     * The consumer of {@link EmvEngine#emvSteps()}: runs the {@code onXxx} method of every step
     * published there, through {@link EmvStepObserver}.
     */
    private void dispatchStepMethod(EmvStep step) {
        if (engine == null || activeConfig == null) {
            return;
        }

        switch (step) {
            case TERMINAL_INITIALIZATION:
                onTerminalInitialization(engine, activeConfig);
                break;
            case SEARCH_CARD:
                onSearchCard(engine, activeConfig, activeCard);
                break;
            case APPLICATION_SELECTION:
                onApplicationSelection(engine, activeConfig, activeCard);
                break;
            case WAIT_APPLICATION_SELECTION:
                onWaitApplicationSelection(engine, activeConfig, activeCard);
                break;
            case FINAL_APPLICATION_SELECTION:
                onFinalApplicationSelection(engine, activeConfig, activeCard);
                break;
            case READ_APPLICATION_DATA:
                onReadApplicationData(engine, activeConfig, activeCard);
                break;
            case SET_TRANSACTION_DATA:
                onSetTransactionData(engine, activeConfig, activeCard);
                break;
            case OFFLINE_DATA_AUTHENTICATION:
                onOfflineDataAuthentication(engine, activeConfig, activeCard);
                break;
            case PROCESS_RESTRICTIONS:
                onProcessRestrictions(engine, activeConfig, activeCard);
                break;
            case CARDHOLDER_VERIFICATION:
                onCardholderVerification(engine, activeConfig, activeCard);
                break;
            case OFFLINE_PIN_VERIFICATION:
                onOfflinePinVerification(engine, activeConfig, activeCard);
                break;
            case TERMINAL_RISK_MANAGEMENT:
                onTerminalRiskManagement(engine, activeConfig, activeCard);
                break;
            case TERMINAL_ACTION_ANALYSIS:
                onTerminalActionAnalysis(engine, activeConfig, activeCard);
                break;
            case START_ONLINE_PROCESS:
                onStartOnlineProcess(engine, activeConfig, activeCard);
                break;
            case ISSUER_AUTHENTICATION:
                onIssuerAuthentication(engine, activeConfig, activeCard);
                break;
            case SCRIPT_PROCESSING:
                onScriptProcessing(engine, activeConfig, activeCard);
                break;
            case TRANSACTION_COMPLETION:
                onTransactionCompletion(engine, activeConfig, activeCard);
                break;
            default:
                unknownStep(step);
                break;
        }
    }

    /**
     * A step with no method to run ends the transaction: after its ERROR the engine publishes no
     * more steps ({@link EmvEngine#notifyEmvStep}).
     */
    private void unknownStep(@Nullable EmvStep step) {
        if (engine == null || !engine.isRunning()) {
            return; // already ended: no second ERROR
        }
        finishError("Unknown EMV step: " + step);
    }
}

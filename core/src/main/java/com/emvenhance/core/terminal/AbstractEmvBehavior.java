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
 * <p>The attempt's {@link PosTerminal.Transaction} comes with {@link #prepare} / {@link #start}:
 * the behavior reports its steps there ({@link #publish}, {@code finish*}) and asks it whether a
 * step may still run ({@link #goToStep}, {@link #isRunning()}, {@link #isCancelled()}).
 *
 * <p>{@link #prepare} only starts {@link EmvStep#TERMINAL_INITIALIZATION} (does not
 * chain further — PosTerminal searches next). {@link #start} kicks the first post-search
 * step; the vendor chain continues via {@link #goToStep}.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    /**
     * The EMV engine, from {@link #attach}: given once, when the terminal is built. The one place
     * it is kept, and only {@link #attach} writes it — {@code PosTerminal} and the vendor
     * subclass both reach it through {@link #engine()}.
     */
    private EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;
    /** The attempt's transaction, from {@link #prepare} / {@link #start}. */
    @Nullable
    private PosTerminal.Transaction transaction;

    /** This behavior's {@link EmvEngine#emvSteps()} subscription — see {@link #initEmvObserver}. */
    private final CompositeDisposable disposables = new CompositeDisposable();

    /** The EMV engine, given once by {@link PosTerminal}'s constructor; it never changes. */
    public final void attach(EmvEngine engine) {
        this.engine = engine;
    }

    /** The engine {@link #attach} gave: the one place it is kept. */
    public final EmvEngine engine() {
        return engine;
    }

    @Override
    public boolean prepare(TransactionConfig config, PosTerminal.Transaction transaction) {
        this.transaction = transaction;
        initEmvObserver();
        this.activeConfig = config;
        this.activeCard = null;

        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        enterStep(EmvStep.TERMINAL_INITIALIZATION);
        return isRunning(); // false once it ended: init failed, or cancelled
    }

    @Override
    public void start(TransactionConfig config, CardPresence card,
            PosTerminal.Transaction transaction) {
        this.transaction = transaction;
        // No initEmvObserver here: prepare, which always runs first in the same attempt,
        // subscribed it last; nothing in the app subscribes to emvSteps during a card search.
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
     * Subscribes {@link EmvStepObserver} to the engine's EmvStep observable, replacing any
     * earlier subscription.
     *
     * <p>Called at the start of every {@link #prepare}, not once: the observer must be the
     * engine's <em>last</em> subscriber. A step method runs inside the observer and publishes the
     * next step from there, so every subscriber after it would see that next step before the
     * current one — the UI would end on the first step instead of the last. Re-subscribing moves
     * it behind every subscriber that came first, such as a {@code MainViewModel} created after
     * an earlier transaction. {@link #start} doesn't repeat it: {@code PosTerminal} always runs
     * {@link #prepare} first in the same attempt (a retry is a new attempt), and the app
     * subscribes to {@code emvSteps} before a transaction starts, never during the card search.
     * A subscriber added during the search would see nested steps out of order.
     *
     * <p>Once a transaction has ended ({@link TransactionStep#isEnd()}) {@link #goToStep}
     * publishes no step, so a step still published by code unwinding never runs a step method;
     * the next {@link #prepare} subscribes again.
     */
    protected final void initEmvObserver() {
        disposables.clear();
        // No subscribeOn / observeOn: see EmvStepObserver.
        disposables.add(engine.emvSteps().subscribeWith(new EmvStepObserver()));
    }

    /**
     * Vendor finished the current step → publish {@code step} on the EmvStep observable, whose
     * observer runs that step's {@code onXxx} method before this returns.
     *
     * <p>The one cancel check at a step boundary: once the transaction is cancelled (or ended)
     * no step is published, so nothing runs.
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
        if (activeConfig == null) {
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
        // No answer, or one that came after the transaction ended (a cancel while the cardholder
        // was choosing): the kernel gets the fail-closed value, never a choice nobody waits for.
        boolean ended = !isRunning();
        if (!answer.answered || ended) {
            engine.apduTrace().note("EMV", (answer.answered
                    ? step.getTitle() + " answered after the transaction ended"
                    : "no answer for " + step.getTitle()) + " — answering " + failClosed);
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
        finishApproved(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, message)
                .build());
    }

    /** As {@link #finishApproved(String)}, with an APPROVED event the vendor built (its audit data). */
    protected final void finishApproved(TransactionStepEvent approved) {
        publish(approved);
        publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
    }

    protected final void finishDeclined(String message) {
        finishDeclined(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, message)
                .build());
    }

    /** As {@link #finishDeclined(String)}, with a DECLINED event the vendor built (its audit data). */
    protected final void finishDeclined(TransactionStepEvent declined) {
        publish(declined);
        publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
    }

    protected final void finishError(String message) {
        publish(PosTerminal.errorEvent(message));
    }

    /**
     * Reports a step of the transaction (CARD_READ, APPLICATION_SELECTED…) on its steps; dropped
     * once it has ended.
     */
    protected final void publish(TransactionStepEvent event) {
        if (transaction == null) {
            EmvLog.d("TransactionStep dropped, no transaction: " + event);
            return;
        }
        transaction.publish(event);
    }

    /** Whether the transaction is still running: false once it ended (an outcome, or a cancel). */
    protected final boolean isRunning() {
        return transaction != null && transaction.isRunning();
    }

    /** Whether {@code PosTerminal} cancelled the transaction ({@link PosTerminal.Transaction#cancel()}). */
    protected final boolean isCancelled() {
        return transaction != null && transaction.isCancelled();
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
        if (activeConfig == null) {
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
        engine.apduTrace().note("EMV", summary);
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

    protected abstract void onTerminalInitialization(TransactionConfig config);

    /**
     * The one step method that stays optional. {@link PosTerminal#searchCard} is the real,
     * mandatory search for every vendor; this runs when {@code SEARCH_CARD} is published — by
     * PosTerminal, with its prompt — and has nothing left to do. Requiring a body here would
     * force an empty one in every vendor for no benefit.
     */
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

    /**
     * Publishes {@code step} — only while the transaction is running. Once it has ended (an
     * outcome, or a cancel) a step from code still unwinding is dropped: it never reaches the UI
     * or a step method.
     *
     * @return whether it was published (and so ran)
     */
    private boolean enterStep(EmvStep step, @Nullable String detail) {
        if (!isRunning()) {
            EmvStepEvent event = new EmvStepEvent(step, detail);
            String why = isCancelled() ? "cancelled" : "the transaction has ended";
            EmvLog.d("EmvStep dropped, " + why + ": " + event);
            engine.apduTrace().note("EMV", "step dropped, " + why + ": " + event);
            return false;
        }
        // EmvStep observable → UI, then EmvStepObserver → dispatchStepMethod → onXxx.
        engine.notifyEmvStep(step, detail);
        return true;
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
            // observing it from one transaction to the next. After a transaction's end goToStep
            // publishes nothing more, and the next prepare re-subscribes (initEmvObserver).
        }
    }

    /**
     * The consumer of {@link EmvEngine#emvSteps()}: runs the {@code onXxx} method of every step
     * published there, through {@link EmvStepObserver}.
     */
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

    /**
     * A step with no method to run ends the transaction: after its ERROR no more steps are
     * published ({@link #goToStep}).
     */
    private void unknownStep(@Nullable EmvStep step) {
        if (!isRunning()) {
            return; // already ended: no second ERROR
        }
        finishError("Unknown EMV step: " + step);
    }
}

package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
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
 * {@link #goToStep} or from a kernel callback — and it runs that step's {@code onXxx} method.
 * Nothing calls a step method directly. A phase the kernel runs internally is still published
 * with {@link #goToStep}; its {@code onXxx} is then the place that says so, with nothing to run.
 *
 * <p>The transaction's {@link PosTerminal.Transaction} comes with {@link #run}: the behavior
 * reports its steps there ({@link #publish}, {@code finish*}) and asks it whether a step may
 * still run ({@link #goToStep}, {@link #isRunning()}, {@link #isCancelled()}).
 *
 * <p>The EMV owns its flow from the start, retries included — the way EMV Book B's Entry Point
 * does: {@link #run} takes each attempt through {@link EmvStep#TERMINAL_INITIALIZATION} and
 * {@link EmvStep#SEARCH_CARD} (the vendor's {@code onSearchCard} polls, reporting through
 * {@link #cardSearchListener()}); the card found starts the first post-search step, and the
 * vendor chain continues via {@link #goToStep}. A retry the kernel asks for
 * ({@link #requestRetry}) starts the next attempt only once the kernel has returned.
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
    /** The transaction, from {@link #run}. */
    @Nullable
    private PosTerminal.Transaction transaction;
    /** Where this attempt's card search reports — see {@link #cardSearchListener()}. */
    @Nullable
    private CardSearchListener cardSearch;
    /**
     * Set by {@link #searchCard} for the SEARCH_CARD it publishes, taken by the dispatch: only
     * that one runs {@code onSearchCard}. Transaction thread only.
     */
    private boolean searchRequested;

    // ─── Retry: the kernel asks, the run loop takes it once the kernel has returned ──
    /** The next attempt's config ({@link #requestRetry}); taken by {@link #nextAttempt}. */
    @Nullable
    private volatile TransactionConfig pendingRetry;
    /**
     * What the next search shows instead of its usual prompt ({@link #searchCard}): set with
     * each retry, so the search after one shows that retry's own.
     */
    @Nullable
    private volatile String retryPrompt;
    /** Retries taken in this transaction; transaction thread only. */
    private int retryCount;

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

    /**
     * The transaction's EMV, attempt after attempt: each one initializes the terminal, then
     * searches for the card ({@link #searchCard}), and the EMV runs on the card found. The next
     * attempt is the retry the kernel asked for, taken only once this one has returned.
     */
    @Override
    public void run(TransactionConfig config, PosTerminal.Transaction transaction) {
        pendingRetry = null;
        retryPrompt = null;
        retryCount = 0;
        TransactionConfig attempt = config;
        while (attempt != null) {
            if (prepare(attempt, transaction)) {
                searchCard();
            }
            attempt = nextAttempt();
        }
    }

    /**
     * One attempt's start: its transaction, a fresh dispatch observer, and
     * {@link EmvStep#TERMINAL_INITIALIZATION} only.
     *
     * @return whether the attempt goes on to the card search — {@code false} once the
     *         transaction ended: initialization failed, or it was cancelled
     */
    boolean prepare(TransactionConfig config, PosTerminal.Transaction transaction) {
        this.transaction = transaction;
        initEmvObserver();
        this.activeConfig = config;
        this.activeCard = null;
        enterStep(EmvStep.TERMINAL_INITIALIZATION);
        return isRunning();
    }

    /**
     * {@link EmvStep#SEARCH_CARD}, with the prompt the screen shows: a retry's own ("Card not
     * read — tap again"), or what the entry mode asks for. Its {@code onSearchCard} is the
     * vendor's search; the card it finds starts the EMV ({@link #start}) before this returns.
     */
    private void searchCard() {
        String prompt = retryPrompt;
        String message = prompt != null ? prompt : waitingMessage(activeConfig);
        cardSearch = new CardSearchReporter(transaction, prompt, message);
        searchRequested = true;
        goToStep(EmvStep.SEARCH_CARD, message);
        searchRequested = false;
    }

    /** The EMV on the card the search found: its first post-search step, then the vendor chain. */
    void start(CardPresence card) {
        this.activeCard = card;
        goToStep(firstStepAfterSearch(card));
    }

    /**
     * The retry the kernel asked for in the attempt that just returned, or {@code null}: no
     * attempt follows. Consumed either way; dropped, with a trace note, once the transaction has
     * ended (a cancel came first).
     */
    @Nullable
    private TransactionConfig nextAttempt() {
        TransactionConfig retry = pendingRetry;
        pendingRetry = null;
        if (retry == null) {
            return null;
        }
        if (!isRunning()) {
            EmvLog.d("Retry dropped: the transaction is over");
            engine.apduTrace().note("EMV", "retry dropped — the transaction is over");
            return null;
        }
        // Same transaction, so the trace carries on: the failed attempt stays above the new one.
        engine.apduTrace().transactionEvent("RETRY #" + ++retryCount);
        // ...but the card session starts over: the next GENERATE AC is a 1st one again.
        engine.apduTrace().beginAttempt();
        return retry;
    }

    /**
     * Asks for the transaction to start again from the card search with an adjusted config —
     * e.g. PAX's {@code fallback()} forcing magstripe, or {@code tryAnotherInterface()} forcing
     * contact — showing {@code prompt} instead of the usual "insert, tap, or swipe". Safe from
     * deep inside a kernel callback: it only records the request; {@link #run} starts the next
     * attempt once the kernel has returned (EMV Book B's Entry Point), on the same thread.
     */
    protected final void requestRetry(TransactionConfig adjustedConfig, @Nullable String prompt) {
        pendingRetry = adjustedConfig;
        retryPrompt = prompt;
    }

    /** Retries taken so far in this transaction (0 on the first attempt). */
    protected final int retryCount() {
        return retryCount;
    }

    /**
     * Where the vendor's card search ({@code onSearchCard}) reports: each callback becomes the
     * transaction's step (WAITING_FOR_CARD, CARD_DETECTED, the ERROR of a timeout or a reader
     * failure), and a card found starts the EMV on it, right there. Bound to this attempt's
     * transaction: once that is over, a late callback is dropped, so it can't fail, or start EMV
     * for, the next one.
     */
    protected final CardSearchListener cardSearchListener() {
        return cardSearch;
    }

    @Override
    public void dispose() {
        disposables.clear();
    }

    /**
     * Subscribes {@link EmvStepObserver} to the engine's EmvStep observable, replacing any
     * earlier subscription.
     *
     * <p>Called at the start of every attempt ({@link #prepare}), not once: the observer must be
     * the engine's <em>last</em> subscriber. A step method runs inside the observer and publishes
     * the next step from there, so every subscriber after it would see that next step before the
     * current one — the UI would end on the first step instead of the last. Re-subscribing moves
     * it behind every subscriber that came first, such as a {@code MainViewModel} created after
     * an earlier transaction. The app subscribes to {@code emvSteps} before a transaction starts,
     * never during the card search; a subscriber added during the search would see nested steps
     * out of order.
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

    /**
     * The card approved: the EMV is done, approved. Reported to the {@link PosTerminal.Transaction},
     * which ends the transaction ({@link PosTerminal.Transaction#emvFinished}) — the EMV reports
     * its outcome, it doesn't end the transaction itself.
     */
    protected final void finishApproved(String message) {
        finishApproved(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, message)
                .build());
    }

    /** As {@link #finishApproved(String)}, with an APPROVED event the vendor built (its audit data). */
    protected final void finishApproved(TransactionStepEvent approved) {
        emvFinished(approved);
    }

    /** The card or the host declined: the EMV is done, declined (see {@link #finishApproved(String)}). */
    protected final void finishDeclined(String message) {
        finishDeclined(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, message)
                .build());
    }

    /** As {@link #finishDeclined(String)}, with a DECLINED event the vendor built (its audit data). */
    protected final void finishDeclined(TransactionStepEvent declined) {
        emvFinished(declined);
    }

    /** The EMV failed (kernel, reader, online failed…): done, with an error (see {@link #finishApproved(String)}). */
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

    /**
     * Reports a step of the transaction (CARD_READ, APPLICATION_SELECTED…) on its steps; dropped
     * once it has ended. Not the outcome nor the end: those are {@code finish*}'s, so only the
     * {@link PosTerminal.Transaction} ends a transaction — one published here is dropped.
     */
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
     * The vendor's card search: polls its readers until a card, a timeout, a reader failure or
     * a cancel ({@link #cancelCardSearch}, {@link #isRunning()}), and reports each through
     * {@link #cardSearchListener()} — exactly one of found / timeout / cancelled / error before
     * it returns. A card found runs the EMV on it inside that callback. {@code card} is
     * {@code null}: none yet.
     */
    protected abstract void onSearchCard(TransactionConfig config, @Nullable CardPresence card);

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
     * ({@link com.emvenhance.core.emv.Field55}), encodes them and logs them.
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
     * the step to have run when it returns; {@link #run} reads {@link #prepare}'s result and any
     * requested retry the moment the step it published returns; and a step
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
                // The search runs only for the step searchCard() published. SEARCH_CARD published
                // from inside the EMV (a vendor asking for the card again, e.g. the Visa 2nd tap
                // prompt) shows on the banner and the trace, but must not start a second poll — and
                // a second EMV — inside the first.
                if (!searchRequested) {
                    EmvLog.d("SEARCH_CARD outside the search step: shown, no second search");
                    engine.apduTrace().note("EMV",
                            "SEARCH_CARD published inside the EMV — shown, no second search");
                    break;
                }
                searchRequested = false;
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
    /** The search's prompt for an entry mode, when no retry gave its own. */
    private static String waitingMessage(TransactionConfig config) {
        if (config.getMode() == EntryMethod.ANY) {
            return "Insert, tap, or swipe";
        }
        if (config.isContact()) {
            return "Insert card";
        }
        if (config.isMagstripe()) {
            return "Swipe card";
        }
        if (config.isManual()) {
            return "Enter card number";
        }
        return "Tap card";
    }

    /**
     * {@link #cardSearchListener()}: one attempt's search, reported on its own transaction. Its
     * callbacks are dropped once that transaction is over — the next one starts only after.
     */
    private final class CardSearchReporter implements CardSearchListener {

        private final PosTerminal.Transaction owner;
        @Nullable
        private final String retryPrompt;
        private final String message;

        CardSearchReporter(PosTerminal.Transaction owner, @Nullable String retryPrompt,
                String message) {
            this.owner = owner;
            this.retryPrompt = retryPrompt;
            this.message = message;
        }

        private boolean isStale(String callback) {
            if (!owner.isRunning()) {
                EmvLog.d("Ignored " + callback + " from a search whose transaction is over");
                return true;
            }
            return false;
        }

        @Override
        public void onSearchStarted(TransactionConfig config) {
            if (isStale("onSearchStarted")) {
                return;
            }
            TransactionStepEvent.Builder waiting = TransactionStepEvent
                    .builder(TransactionStep.WAITING_FOR_CARD).message(message);
            if (retryPrompt != null) {
                waiting.put(TransactionStepEvent.KEY_RETRY_PROMPT, retryPrompt);
            }
            owner.publish(waiting.build());
        }

        @Override
        public void onChipDetected(CardPresence card) {
            cardFound(card);
        }

        @Override
        public void onContactlessDetected(CardPresence card) {
            cardFound(card);
        }

        @Override
        public void onMagstripeDetected(CardPresence card) {
            cardFound(card);
        }

        @Override
        public void onManualEntrySelected(CardPresence card) {
            cardFound(card);
        }

        @Override
        public void onSearchTimeout() {
            if (isStale("onSearchTimeout")) {
                return;
            }
            owner.publish(PosTerminal.errorEvent("No card presented — card search timed out"));
        }

        @Override
        public void onSearchCancelled() {
            if (isStale("onSearchCancelled")) {
                return;
            }
            owner.publish(PosTerminal.errorEvent("Card search cancelled"));
        }

        @Override
        public void onReaderError(String message) {
            if (isStale("onReaderError")) {
                return;
            }
            owner.publish(PosTerminal.errorEvent(message != null ? message : "Reader error"));
        }

        /**
         * WAITING_FOR_CARD again, with what to do. Not SEARCH_CARD again: that step's method is
         * the search itself, still running.
         */
        @Override
        public void onSearchRetry(String message) {
            if (isStale("onSearchRetry")) {
                return;
            }
            owner.publish(TransactionStepEvent.builder(TransactionStep.WAITING_FOR_CARD)
                    .message(message)
                    .put(TransactionStepEvent.KEY_RETRY_PROMPT, message)
                    .build());
        }

        /** CARD_DETECTED, then the EMV on it, right here; a retry it asks for is {@link #run}'s. */
        private void cardFound(CardPresence card) {
            if (isStale("card detected")) {
                return;
            }
            owner.publish(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                    .put(TransactionStepEvent.KEY_MODE, card.getModeLabel())
                    .put("entryMethod", card.getEntryMethod().name())
                    .build());
            start(card);
        }
    }
}

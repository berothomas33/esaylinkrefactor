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
 *
 * <p>{@link #goToStep} never calls the {@code onXxx} method itself: it publishes the step, and
 * this class's own {@link EmvEngine#emvSteps()} observer ({@link EmvStepObserver}) runs it —
 * notify → onNext → observer → {@code dispatchStepMethod}, the same shape as
 * {@code EmvEngine}'s transaction steps. Steps published any other way
 * ({@link #announceStep}, PosTerminal's card-search prompt) reach the UI only.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    protected final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    protected EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;

    /** This behavior's {@link EmvEngine#emvSteps()} subscription — see {@link #initEmvObserver}. */
    private final CompositeDisposable disposables = new CompositeDisposable();

    /**
     * The step {@link #enterStep} is publishing right now: the one {@link EmvStepEvent} whose
     * {@code onXxx} method the observer runs. {@code null} outside that publish.
     */
    @Nullable
    private volatile EmvStep enteringStep;

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
        initEmvObserver(engine);
        this.activeConfig = config;
        this.activeCard = null;
        cancelled.set(false);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        enterStep(EmvStep.TERMINAL_INITIALIZATION);
        return !cancelled.get() && engine.isRunning();
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        this.engine = engine;
        initEmvObserver(engine);
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

    @Override
    public void dispose() {
        disposables.clear();
    }

    /**
     * Subscribes this behavior to {@code engine}'s EmvStep observable, replacing any earlier
     * subscription, so that a step {@link #goToStep} publishes runs its {@code onXxx} method.
     *
     * <p>Called at the start of every {@link #prepare} and {@link #start}, not once: the observer
     * must be the engine's <em>last</em> subscriber. A step method runs inside the observer and
     * publishes the next step from there, so every subscriber after it would see that next step
     * before the current one — the UI would end on the first step instead of the last.
     * Re-subscribing moves it behind every subscriber that came first, such as a
     * {@code MainViewModel} created after an earlier transaction.
     */
    protected final void initEmvObserver(EmvEngine engine) {
        disposables.clear();
        // No subscribeOn / observeOn: see EmvStepObserver.
        disposables.add(engine.emvSteps().subscribeWith(new EmvStepObserver()));
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
        engine.notifyEmvStep(step, detail);
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

    private void enterStep(EmvStep step) {
        enterStep(step, null);
    }

    private void enterStep(EmvStep step, @Nullable String detail) {
        enteringStep = step;
        try {
            // EmvStep observable → UI, then EmvStepObserver → dispatchStepMethod → onXxx.
            engine.notifyEmvStep(step, detail);
        } finally {
            enteringStep = null;
        }
    }

    /**
     * The {@link #initEmvObserver} observer: for the step {@link #enterStep} is publishing, runs
     * that step's {@code onXxx} method through {@link #dispatchStepMethod}. Steps published any
     * other way ({@link #announceStep}, PosTerminal's card-search prompt) are skipped.
     *
     * <p>Runs on the thread that published the step, before {@code goToStep} returns — there is
     * deliberately no {@code subscribeOn} / {@code observeOn}. Vendor kernels call
     * {@code goToStep} from inside their own callbacks and expect the next step to have run when
     * it returns; {@code PosTerminal} reads {@link #prepare}'s result and any requested retry the
     * moment {@link #prepare} / {@link #start} return; and a step method blocks (APDUs, PIN entry,
     * the host), which a {@code computation} thread must never do.
     */
    private final class EmvStepObserver extends DisposableObserver<EmvStepEvent> {

        @Override
        public void onNext(EmvStepEvent event) {
            EmvStep step = event.getStep();
            if (step != enteringStep) {
                return; // announced, not entered: the observable only
            }
            enteringStep = null;
            if (cancelled.get()) {
                return;
            }
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
            // emvSteps never completes.
        }
    }

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
                finishError("Unknown EMV step: " + step);
                break;
        }
    }
}

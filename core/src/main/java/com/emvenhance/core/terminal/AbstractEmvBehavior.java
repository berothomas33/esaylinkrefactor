package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.Field55;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.EmvLog;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

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
 * <p>Steps go through {@link #stepStream}: the next method runs once the current one returns
 * (queued, not nested inside it), on the same thread, and {@link #prepare} / {@link #start}
 * return only after every step they led to has run.
 */
public abstract class AbstractEmvBehavior implements EmvBehavior {

    protected final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Nullable
    protected EmvEngine engine;
    @Nullable
    protected TransactionConfig activeConfig;
    @Nullable
    protected CardPresence activeCard;

    /**
     * Every step change, in order. {@link #dispatchStepMethod} only emits here; one
     * {@link StepObserver} runs the step's {@code onXxx} method. Serialized, so steps run one at a
     * time, and a {@link #goToStep} made from inside a step method is queued until that method
     * returns. No scheduler: steps run on the thread that called {@link #prepare} /
     * {@link #start} — the PAX kernel's calls and callbacks stay on the thread that initialized
     * it and powered up the reader, and those two calls stay synchronous for {@link PosTerminal}.
     */
    private final Subject<EmvStep> stepStream = PublishSubject.<EmvStep>create().toSerialized();

    /** Held while {@link #prepare} or {@link #start} runs its steps — see {@link #runSteps}. */
    private final ReentrantLock stepLock = new ReentrantLock();

    /** The {@link StepObserver}'s subscription — live only while {@link #stepLock} is held. */
    @Nullable
    private Disposable stepSubscription;

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = null;
        cancelled.set(false);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        // Run init only — do NOT goToStep further; PosTerminal will searchCard next.
        runSteps(() -> enterStep(EmvStep.TERMINAL_INITIALIZATION));
        return !cancelled.get() && engine.isRunning();
    }

    @Override
    public void start(EmvEngine engine, TransactionConfig config, CardPresence card) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = card;
        cancelled.set(false);

        // First post-search step — vendor methods chain via goToStep from here.
        runSteps(() -> goToStep(firstStepAfterSearch(card)));
    }

    /**
     * Flags the transaction cancelled: {@link #goToStep} refuses every later step. A step method
     * already running isn't interrupted — it checks {@link #isCancelled} itself (PAX closes its
     * readers that way) — and the step stream is disposed when {@link #prepare} / {@link #start}
     * return, not here, so a step already queued still gets to clean up.
     */
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
     * attach anything else it read, and {@link EmvEngine#authorize} sends it.
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
        engine.notifyEmvStep(step, detail); // EmvStep observable → UI
        if (cancelled.get()) {
            return;
        }
        dispatchStepMethod(step);
    }

    /** Emits {@code step} on {@link #stepStream}; the {@link StepObserver} runs its method. */
    private void dispatchStepMethod(EmvStep step) {
        stepStream.onNext(step);
    }

    /**
     * Subscribes the {@link StepObserver}, runs {@code first} (which dispatches the first step),
     * then disposes the subscription. The outermost {@code onNext} drains the queue before it
     * returns, so by then every step {@code first} led to has run — {@link PosTerminal} reads the
     * init result right after {@link #prepare} and the pending retry right after {@link #start}.
     *
     * <p>{@link #stepLock} keeps that true across threads: a transaction started while a cancelled
     * one is still blocked inside a step (host, PIN pad) waits here for that chain to end, instead
     * of having its steps queued behind it — run later, on the other thread, after this call had
     * already returned. A nested call on the same thread reuses the live subscription; a second
     * observer would run every step twice.
     */
    private void runSteps(Runnable first) {
        stepLock.lock();
        boolean outermost = stepLock.getHoldCount() == 1;
        try {
            if (outermost) {
                stepStream.subscribe(new StepObserver());
            }
            first.run();
        } finally {
            if (outermost) {
                Disposable subscription = stepSubscription;
                stepSubscription = null;
                if (subscription != null) {
                    subscription.dispose();
                }
            }
            stepLock.unlock();
        }
    }

    /**
     * The one subscriber to {@link #stepStream}: runs each step's {@code onXxx} method with the
     * current {@link #engine}, {@link #activeConfig} and {@link #activeCard}. Never throws — an
     * observer that threw would leave the serialized stream stuck "emitting", silently queueing
     * every later step — so a failing step method ends the transaction through
     * {@link #finishError}, as before, and later steps still run.
     */
    private final class StepObserver implements Observer<EmvStep> {

        @Override
        public void onSubscribe(Disposable d) {
            stepSubscription = d;
        }

        @Override
        public void onNext(EmvStep step) {
            if (engine == null || activeConfig == null) {
                return;
            }
            try {
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
            } catch (Throwable t) {
                failStep(step, t);
            }
        }

        @Override
        public void onError(Throwable e) {
            // Never called: nothing terminates stepStream.
        }

        @Override
        public void onComplete() {
            // Never called: nothing terminates stepStream.
        }

        private void failStep(EmvStep step, Throwable t) {
            try {
                finishError(t.getMessage() != null ? t.getMessage() : step.getTitle());
            } catch (Throwable reportFailed) {
                // Swallowed, not rethrown: throwing here would leave stepStream stuck.
                EmvLog.d("EmvStep " + step + " failed (" + t + ") and reporting it failed: "
                        + reportFailed);
            }
        }
    }
}

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
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vendor base: each step method does vendor work, then calls {@link #goToStep}, which emits the
 * next step on {@link #steps}. The one observer of {@link #steps}, {@link #dispatchStepMethod},
 * publishes it to the UI and runs that step's {@code onXxx} method.
 *
 * <pre>
 *   onApplicationSelection(...) {
 *       // vendor work…
 *       goToStep(EmvStep.WAIT_APPLICATION_SELECTION);  // → steps → dispatchStepMethod → onXxx
 *   }
 * </pre>
 *
 * <p>{@link #steps} is serialized and observed on the emitting thread, with no scheduler: a step
 * emitted from inside a step method is queued and runs once that method returns, so the chain
 * runs as a loop on the transaction thread instead of one method calling the next. It still runs
 * to the end before {@link #start} returns, which {@code PosTerminal} relies on (it checks for a
 * retry right after), and the vendor kernel's blocking calls stay off Rx's shared pools.
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

    /** Steps to run, emitted by {@link #goToStep} and handled by {@link #dispatchStepMethod}. */
    private final Subject<EmvStepEvent> steps = PublishSubject.<EmvStepEvent>create().toSerialized();

    protected AbstractEmvBehavior() {
        steps.subscribe(this::dispatchStepMethod);
    }

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
        this.activeConfig = config;
        this.activeCard = null;
        cancelled.set(false);

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        // Run init only — onTerminalInitialization does NOT goToStep further; PosTerminal will
        // searchCard next.
        goToStep(EmvStep.TERMINAL_INITIALIZATION);
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
     * Vendor finished the current step → emit {@code step} on {@link #steps};
     * {@link #dispatchStepMethod} publishes it to the UI and runs its {@code onXxx} method.
     */
    protected final void goToStep(EmvStep step) {
        goToStep(step, null);
    }

    protected final void goToStep(EmvStep step, @Nullable String detail) {
        steps.onNext(new EmvStepEvent(step, detail));
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

    /**
     * Ends the transaction as cancelled, naming the step it was at — see
     * {@link EmvEngine#notifyCancelled}. Use it wherever a step notices the cancel itself.
     */
    protected final void finishCancelled() {
        if (engine == null) {
            return;
        }
        engine.notifyCancelled();
    }

    /**
     * The transaction was cancelled between two steps, so no later step will release what the
     * vendor holds open (card readers, kernel listeners, PIN pad): release it here. The cancel is
     * already reported — don't report a result. Default: nothing to release.
     */
    protected void onFlowCancelled() {
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

    /**
     * A cancel reached the step chain before {@code notRun}: report it at the step the
     * transaction was at, note in the APDU trace where the flow stopped, and let the vendor
     * release what it holds open. The chain ends here: no step method runs after this.
     */
    private void stopCancelled(EmvStep notRun) {
        EmvStep at = engine.currentEmvStep();
        String where = "EMV flow cancelled" + (at != null ? " at " + at.getTitle() : "")
                + " — " + notRun.getTitle() + " not run";
        EmvLog.d(where);
        engine.apduTrace().note("EMV", where);
        engine.notifyCancelled();
        try {
            onFlowCancelled();
        } catch (Exception e) {
            EmvLog.d("onFlowCancelled failed: " + e);
        }
    }

    /**
     * The observer of {@link #steps}: publishes the step on the engine's EmvStep observable (UI)
     * and runs its {@code onXxx} method. Nothing may escape it — a throw would leave the
     * serialized {@link #steps} stuck mid-emission and every later step queued forever — so any
     * failure ends the transaction as an error instead.
     */
    private void dispatchStepMethod(EmvStepEvent event) {
        EmvStep step = event.getStep();
        try {
            // engine and activeConfig are set by prepare()/start() before any goToStep.
            if (cancelled.get()) {
                stopCancelled(step);
                return;
            }
            engine.notifyEmvStep(step, event.getDetail()); // EmvStep observable → UI

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
        } catch (Throwable e) {
            EmvLog.d("EMV step " + step + " failed: " + e);
            finishError(e.getMessage() != null ? e.getMessage() : step.getTitle());
        }
    }
}

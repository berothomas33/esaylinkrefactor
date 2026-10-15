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

    @Override
    public boolean prepare(EmvEngine engine, TransactionConfig config) {
        this.engine = engine;
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

    protected final void goToStep(@Nullable EmvStep step, @Nullable String detail) {
        if (step == null) {
            unknownStep(null);
            return;
        }
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

    protected final void announceStep(@Nullable EmvStep step, @Nullable String detail) {
        if (step == null) {
            unknownStep(null);
            return;
        }
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

    // ─── EMV step methods — every vendor writes these ─────────────────────

    // Each EMV phase is a method, and — with the single exception of onSearchCard — every one of
    // them is abstract: a vendor class that doesn't write all of them fails to compile. This is
    // deliberate. A silently inherited no-op is indistinguishable from "the developer forgot
    // this phase"; a compile error is not. For a phase your kernel drives internally with no
    // callback, the correct body is a one-line comment saying so and announceStep — a real,
    // considered answer, not a stub.
    //
    // They are protected, not on EmvBehavior: only dispatchStepMethod runs them, for a step
    // entered with goToStep. Nothing holding the engine's EmvBehavior can run one.

    protected abstract void onTerminalInitialization(EmvEngine engine, TransactionConfig config);

    /**
     * The one step method that stays optional. {@link PosTerminal#searchCard} is already the
     * real, mandatory search for every vendor — this runs only if something
     * {@code goToStep(EmvStep.SEARCH_CARD)}s, which nothing does today. Requiring a body here
     * would force an empty one in every vendor for no benefit.
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

    private void enterStep(EmvStep step, @Nullable String detail) {
        engine.notifyEmvStep(step, detail); // EmvStep observable → UI
        // Ended (TransactionStep#isEnd): the engine dropped the step, and its method doesn't run.
        if (cancelled.get() || !engine.isRunning()) {
            return;
        }
        try {
            dispatchStepMethod(step);
        } catch (Exception e) {
            finishError(e.getMessage() != null ? e.getMessage() : step.getTitle());
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
                unknownStep(step);
                break;
        }
    }

    /**
     * A step with no method to run ends the transaction: its ERROR stops the engine publishing
     * steps ({@link EmvEngine#notifyEmvStep}) and this behavior running their methods
     * ({@link #enterStep}).
     */
    private void unknownStep(@Nullable EmvStep step) {
        if (engine == null || !engine.isRunning()) {
            return; // already ended: no second ERROR
        }
        finishError("Unknown EMV step: " + step);
    }
}

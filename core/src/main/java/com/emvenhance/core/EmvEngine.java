package com.emvenhance.core;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Abstract EMV engine — the single owner of both reactive subjects.
 *
 * <h3>Design contract</h3>
 *
 * <p>This class replaces the old {@code EmvBehavior} + {@code EmvBehavior.Callback} pair.
 * Instead of the vendor implementation <em>receiving</em> a callback object and pushing
 * events into it, the vendor implementation <em>extends</em> this class and pushes events
 * into the two protected subjects that already live here:
 *
 * <ul>
 *   <li>{@link #emitEmvStep} → feeds the <strong>EMV Steps Subject</strong>
 *       ({@code PublishSubject}). Fine-grained kernel phases: application selection,
 *       ODA, CVM, risk management, etc.  One-shot — not replayed to late subscribers.
 *
 *   <li>{@link #emitTransactionStep} → feeds the <strong>Transaction Steps Subject</strong>
 *       ({@code BehaviorSubject}). Coarse lifecycle milestones: card detected, approved,
 *       declined, completed.  Sticky — a late subscriber (or a rotated Activity) always
 *       sees the latest state.
 * </ul>
 *
 * <p>Because both subjects live inside the engine, there is <strong>no callback interface
 * </strong> to implement. Consumers subscribe to {@link #emvSteps()} and
 * {@link #transactionSteps()} and react to what arrives. This keeps the engine, the
 * orchestrator, and the UI fully decoupled — none of them knows the other's type.
 *
 * <h3>Threading</h3>
 *
 * <p>The lifecycle methods ({@code prepare}, {@code execute}, {@code complete}) are
 * blocking and are expected to be called on a background thread (typically
 * {@code Schedulers.io()}). The subjects emit on whatever thread calls the
 * {@code emit} methods; subscribers choose their own observation thread.
 *
 * <h3>Vendor implementation checklist</h3>
 *
 * <ol>
 *   <li>Override the three abstract methods.
 *   <li>In {@code prepare()}: initialize the kernel and emit
 *       {@link TransactionStep#TRANSACTION_STARTED} then
 *       {@link TransactionStep#WAITING_FOR_CARD}.
 *   <li>In {@code execute()}: run the kernel's transaction loop, calling
 *       {@link #emitEmvStep} at each kernel callback and
 *       {@link #emitTransactionStep} at the meaningful lifecycle boundaries.
 *   <li>In {@code complete()}: feed the {@link AuthResult} to the kernel and emit
 *       {@link TransactionStep#APPROVED} or {@link TransactionStep#DECLINED}.
 *   <li>Always emit {@link TransactionStep#COMPLETED} or
 *       {@link TransactionStep#ERROR} as the final event so the orchestrator knows
 *       the engine is idle again.
 * </ol>
 */
public abstract class EmvEngine {

    // ─── The two subjects ────────────────────────────────────────────────

    /**
     * Transaction steps — BehaviorSubject (sticky).
     *
     * <p>A late subscriber immediately receives the latest snapshot, which is exactly
     * what a freshly created Activity needs after rotation.
     */
    private final BehaviorSubject<TransactionStepEvent> transactionSteps =
            BehaviorSubject.createDefault(TransactionStepEvent.idle());

    /**
     * EMV steps — PublishSubject (fire-and-forget).
     *
     * <p>These are diagnostic / progress events. If nobody is listening when one fires,
     * it is simply lost — that is the correct behaviour for a progress indicator.
     */
    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    /** Prevents a second transaction from starting while one is running. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ─── Public observables (hidden subjects) ────────────────────────────

    /**
     * High-level transaction lifecycle.
     *
     * <p>Subscribers see: IDLE → TRANSACTION_STARTED → WAITING_FOR_CARD → CARD_DETECTED
     * → … → APPROVED/DECLINED → COMPLETED (or ERROR at any point).
     */
    public final Observable<TransactionStepEvent> transactionSteps() {
        return transactionSteps.hide();
    }

    /**
     * Fine-grained EMV kernel progress.
     *
     * <p>Subscribers see each {@link EmvStep} as the kernel reaches it: terminal init,
     * application selection, read data, ODA, CVM, risk management, online processing,
     * issuer auth, script processing, completion.
     */
    public final Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    /** Returns the most recent transaction step event without subscribing. */
    public final TransactionStepEvent currentTransactionState() {
        return transactionSteps.getValue();
    }

    // ─── Protected emit methods — vendor implementations call these ──────

    /**
     * Pushes a new high-level transaction milestone.
     *
     * <p>Call this from the abstract lifecycle methods whenever the transaction reaches
     * a new {@link TransactionStep}.
     */
    protected final void emitTransactionStep(TransactionStepEvent event) {
        transactionSteps.onNext(event);
    }

    /**
     * Pushes a fine-grained EMV kernel step.
     *
     * <p>Call this every time the vendor kernel announces a new processing phase.
     */
    protected final void emitEmvStep(EmvStep step, String detail) {
        emvSteps.onNext(new EmvStepEvent(step, detail));
    }

    /** Convenience overload without detail. */
    protected final void emitEmvStep(EmvStep step) {
        emitEmvStep(step, null);
    }

    // ─── Guard: one transaction at a time ────────────────────────────────

    /**
     * Attempts to acquire the running lock. Returns {@code true} if the engine is now
     * owned by this caller; {@code false} if another transaction is already in progress.
     */
    protected final boolean acquireRunning() {
        return running.compareAndSet(false, true);
    }

    /** Releases the running lock so a new transaction can start. */
    protected final void releaseRunning() {
        running.set(false);
    }

    /** Returns true if a transaction is currently in progress. */
    public final boolean isRunning() {
        return running.get();
    }

    // ─── Convenience: emit a TransactionStepEvent with card data ─────────

    protected final void emitCardDetected(String pan, String issuerName,
            String cardHolderName, String mode) {
        emitTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_PAN, pan)
                .put(TransactionStepEvent.KEY_ISSUER_NAME, issuerName)
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, cardHolderName)
                .put(TransactionStepEvent.KEY_MODE, mode)
                .build());
    }

    protected final void emitApproved(String result) {
        emitTransactionStep(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, result)
                .build());
    }

    protected final void emitDeclined(String reason) {
        emitTransactionStep(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, reason)
                .build());
    }

    protected final void emitError(String error) {
        emitTransactionStep(TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build());
    }

    // ─── Abstract lifecycle — vendor implementations fill these in ───────

    /**
     * Initializes the terminal and kernel for a new transaction.
     *
     * <p>Called on a background thread. Implementations should:
     * <ol>
     *   <li>Call {@link #acquireRunning()} and fail if it returns false.
     *   <li>Initialize the vendor kernel with the given config.
     *   <li>Emit {@link TransactionStep#TRANSACTION_STARTED} and
     *       {@link TransactionStep#WAITING_FOR_CARD}.
     * </ol>
     *
     * @return true if the kernel is ready; false on initialization failure.
     */
    public abstract boolean prepare(TransactionConfig config);

    /**
     * Runs the EMV flow until the kernel asks to go online, approves offline, or fails.
     *
     * <p>Called on a background thread. This is where most of the kernel interaction
     * happens. Implementations should call {@link #emitEmvStep} at every kernel callback
     * and {@link #emitTransactionStep} at lifecycle boundaries.
     *
     * <p>If the kernel decides to go online, the implementation emits
     * {@link TransactionStep#ONLINE_REQUIRED} and returns — the orchestrator will call
     * {@link #complete} with the host result. If the kernel approves or declines offline,
     * the implementation emits the outcome and {@link TransactionStep#COMPLETED} directly.
     */
    public abstract void execute();

    /**
     * Feeds the host's authorization result back to the kernel and finishes.
     *
     * <p>Called on a background thread after the communication behavior has returned.
     * Implementations should run issuer authentication, script processing, and the
     * second GENERATE AC, then emit {@link TransactionStep#APPROVED} or
     * {@link TransactionStep#DECLINED} followed by {@link TransactionStep#COMPLETED}.
     */
    public abstract void complete(AuthResult authResult);
}

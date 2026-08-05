package com.emvenhance.core.engine;

import androidx.annotation.Nullable;
import com.emvenhance.core.behavior.BehaviorBridge;
import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.behavior.EmvBehaviorRegistry;
import com.emvenhance.core.behavior.EmvError;
import com.emvenhance.core.behavior.EmvStepBehavior;
import com.emvenhance.core.behavior.EmvTransitionPolicy;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.port.EmvInteractionPort;
import com.emvenhance.core.port.EmvKernelPort;
import com.emvenhance.core.port.EmvSignal;
import com.emvenhance.core.port.InteractionRequest;
import com.emvenhance.core.terminal.EmvBehavior;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * EMV orchestrator + event bus for one in-flight transaction.
 *
 * <p>Legacy vendors may still drive steps via {@link #notifyEmvStep} from
 * {@link EmvBehavior}. Behavior-driven vendors attach a registry + policy + kernel
 * port and call {@link #runFlow(TransactionConfig, CardPresence)}.
 *
 * <p>{@link com.emvenhance.core.terminal.PosTerminal} attaches host/printer ports
 * at construction.
 */
public final class EmvEngine {

    private static final int MAX_STEP_RETRIES = 2;
    private static final long DEFAULT_WAIT_TIMEOUT_MS = 60_000L;

    private final BehaviorSubject<TransactionStepEvent> transactionSteps =
            BehaviorSubject.createDefault(TransactionStepEvent.idle());

    private final PublishSubject<EmvStepEvent> emvSteps = PublishSubject.create();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<EnginePhase> phase =
            new AtomicReference<>(EnginePhase.IDLE);
    private final EmvContext context = new EmvContext();
    private final Object waitLock = new Object();

    @Nullable
    private EmvBehavior behavior;

    @Nullable
    private CommunicationBehavior communication;

    @Nullable
    private PrinterBehavior printer;

    @Nullable
    private EmvBehaviorRegistry registry;

    @Nullable
    private EmvTransitionPolicy policy;

    @Nullable
    private EmvKernelPort kernelPort;

    @Nullable
    private EmvInteractionPort interactionPort;

    @Nullable
    private EmvCleanupHandler cleanupHandler;

    @Nullable
    private EmvStepBehavior pendingBehavior;

    @Nullable
    private volatile EmvSignal pendingSignal;

    @Nullable
    private CountDownLatch waitLatch;

    private final BehaviorBridge bridge = new EngineBehaviorBridge();

    // ─── Attachments ─────────────────────────────────────────────────────

    public void attachBehavior(EmvBehavior behavior) {
        this.behavior = behavior;
    }

    @Nullable
    public EmvBehavior getBehavior() {
        return behavior;
    }

    public void attachCommunication(CommunicationBehavior communication) {
        this.communication = communication;
    }

    public void attachPrinter(PrinterBehavior printer) {
        this.printer = printer;
    }

    /**
     * Configures behavior-driven orchestration. Call once from the vendor factory /
     * {@link EmvBehavior#prepare} before {@link #runFlow}.
     */
    public void attachFlow(EmvBehaviorRegistry registry,
            EmvTransitionPolicy policy,
            EmvKernelPort kernelPort,
            EmvInteractionPort interactionPort,
            @Nullable EmvCleanupHandler cleanupHandler) {
        this.registry = registry;
        this.policy = policy;
        this.kernelPort = kernelPort;
        this.interactionPort = interactionPort != null
                ? interactionPort
                : new EmvInteractionPort.Auto();
        this.cleanupHandler = cleanupHandler != null
                ? cleanupHandler
                : EmvCleanupHandler.NO_OP;
    }

    public EmvContext context() {
        return context;
    }

    public EnginePhase phase() {
        return phase.get();
    }

    // ─── Observables ─────────────────────────────────────────────────────

    public Observable<TransactionStepEvent> transactionSteps() {
        return transactionSteps.hide();
    }

    public Observable<EmvStepEvent> emvSteps() {
        return emvSteps.hide();
    }

    public TransactionStepEvent currentTransactionState() {
        return transactionSteps.getValue();
    }

    public boolean isRunning() {
        return running.get();
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────

    public boolean begin() {
        if (!running.compareAndSet(false, true)) {
            notifyError("A transaction is already running");
            return false;
        }
        phase.set(EnginePhase.RUNNING);
        return true;
    }

    public boolean begin(TransactionConfig config) {
        if (!begin()) {
            return false;
        }
        context.reset(config);
        return true;
    }

    /**
     * Runs {@link EmvStep#TERMINAL_INITIALIZATION} only (before card search).
     * Call from {@link EmvBehavior#prepare}.
     */
    public boolean prepareFlow(TransactionConfig config) {
        if (registry == null || policy == null || kernelPort == null) {
            notifyError("EMV flow not configured (registry/policy/kernel missing)");
            return false;
        }
        if (!running.get()) {
            notifyError("Call begin() before prepareFlow()");
            return false;
        }
        context.reset(config);
        context.setCurrentStep(EmvStep.TERMINAL_INITIALIZATION);
        phase.set(EnginePhase.RUNNING);

        EmvStepBehavior init = registry.resolve(EmvStep.TERMINAL_INITIALIZATION, context);
        if (init == null) {
            notifyError("No TerminalInitializationBehavior registered");
            return false;
        }
        pendingBehavior = init;
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        notifyEmvStep(EmvStep.TERMINAL_INITIALIZATION);
        BehaviorResult result = init.execute(context, bridge);
        result = awaitIfNeeded(init, result);
        pendingBehavior = null;

        if (result instanceof BehaviorResult.Failure) {
            finishFailed(((BehaviorResult.Failure) result).getError());
            return false;
        }
        if (!result.isSuccess()) {
            notifyError("Terminal initialization did not complete");
            return false;
        }
        return true;
    }

    /**
     * Runs the behavior-driven EMV loop for the already-selected card, starting at
     * {@link EmvStep#SEARCH_CARD} (usually Skip — search is owned by PosTerminal).
     * Blocking — intended for the IO thread used by {@code PosTerminal}.
     */
    public void runFlow(TransactionConfig config, CardPresence card) {
        if (registry == null || policy == null || kernelPort == null) {
            notifyError("EMV flow not configured (registry/policy/kernel missing)");
            return;
        }
        if (!running.get()) {
            if (!begin(config)) {
                return;
            }
            // begin without prepareFlow — run full graph including init
            context.setCard(card);
            context.setCancelled(false);
            context.setCurrentStep(EmvStep.TERMINAL_INITIALIZATION);
        } else {
            context.setConfig(config);
            context.setCard(card);
            context.setCancelled(false);
            // prepareFlow already ran init → continue from search
            if (context.getCurrentStep() == EmvStep.TERMINAL_INITIALIZATION
                    || context.getCurrentStep() == null) {
                context.setCurrentStep(EmvStep.SEARCH_CARD);
            }
        }
        phase.set(EnginePhase.RUNNING);

        try {
            loop();
        } catch (Exception e) {
            phase.set(EnginePhase.FAILED);
            cleanup();
            notifyError(e.getMessage() != null ? e.getMessage() : "EMV flow failed");
        }
    }

    /** Delivers an async signal to the waiting behavior (UI / kernel / host). */
    public void resume(EmvSignal signal) {
        synchronized (waitLock) {
            if (phase.get() != EnginePhase.WAITING) {
                return;
            }
            pendingSignal = signal;
            if (waitLatch != null) {
                waitLatch.countDown();
            }
        }
    }

    public void retryCurrent() {
        if (context.getCurrentStep() == null) {
            return;
        }
        context.incrementRetryCount();
        // loop() retries same step when Failure.retryable — exposed for external UI retry
    }

    public void cancel() {
        context.setCancelled(true);
        if (behavior != null) {
            behavior.cancel();
        }
        if (kernelPort != null) {
            kernelPort.abort();
        }
        if (pendingBehavior != null) {
            pendingBehavior.onCancel(context);
        }
        synchronized (waitLock) {
            pendingSignal = new EmvSignal.UserCancel();
            if (waitLatch != null) {
                waitLatch.countDown();
            }
        }
        if (running.get() && phase.get() != EnginePhase.COMPLETED
                && phase.get() != EnginePhase.FAILED) {
            phase.set(EnginePhase.CANCELLED);
            cleanup();
            notifyError("Transaction cancelled");
        }
    }

    // ─── Orchestrator loop ───────────────────────────────────────────────

    private void loop() {
        while (running.get()
                && !context.isCancelled()
                && phase.get() == EnginePhase.RUNNING) {

            EmvStep step = context.getCurrentStep();
            if (step == null) {
                finishFailed(EmvError.of("NO_STEP", "No current EMV step", null, false));
                return;
            }

            EmvStepBehavior stepBehavior = registry.resolve(step, context);
            if (stepBehavior == null) {
                finishFailed(EmvError.of("NO_BEHAVIOR",
                        "No behavior registered for " + step, step, false));
                return;
            }

            pendingBehavior = stepBehavior;
            notifyEmvStep(step);

            BehaviorResult result = stepBehavior.execute(context, bridge);
            result = awaitIfNeeded(stepBehavior, result);

            if (!running.get() || context.isCancelled()) {
                return;
            }

            if (result instanceof BehaviorResult.Success
                    || result instanceof BehaviorResult.Skip) {
                context.resetRetryCount();
                publishTransactionMilestone(step);
                EmvStep next = policy.next(step, result, context);
                if (next == null) {
                    finishFromContext();
                    return;
                }
                context.setCurrentStep(next);
                continue;
            }

            if (result instanceof BehaviorResult.Failure) {
                BehaviorResult.Failure failure = (BehaviorResult.Failure) result;
                EmvError error = failure.getError();
                if (error.isRetryable() && context.getRetryCount() < MAX_STEP_RETRIES) {
                    context.incrementRetryCount();
                    continue;
                }
                finishFailed(error);
                return;
            }

            finishFailed(EmvError.of("BAD_RESULT",
                    "Behavior returned unexpected result: " + result.getClass().getSimpleName(),
                    step, false));
            return;
        }
    }

    private BehaviorResult awaitIfNeeded(EmvStepBehavior stepBehavior, BehaviorResult result) {
        while (result.isWaiting() && running.get() && !context.isCancelled()) {
            long timeoutMs = DEFAULT_WAIT_TIMEOUT_MS;
            if (result instanceof BehaviorResult.WaitAsync) {
                long t = ((BehaviorResult.WaitAsync) result).getTimeoutMs();
                if (t > 0) {
                    timeoutMs = t;
                }
            }
            if (result instanceof BehaviorResult.WaitInteraction) {
                InteractionRequest req =
                        ((BehaviorResult.WaitInteraction) result).getRequest();
                dispatchInteraction(req);
            }

            EmvSignal signal = park(timeoutMs);
            if (signal == null) {
                result = stepBehavior.onSignal(context, bridge, new EmvSignal.Timeout());
            } else if (signal instanceof EmvSignal.UserCancel) {
                stepBehavior.onCancel(context);
                return BehaviorResult.failure(EmvError.cancelled(stepBehavior.step()));
            } else {
                result = stepBehavior.onSignal(context, bridge, signal);
            }
        }
        return result;
    }

    private void dispatchInteraction(InteractionRequest request) {
        EmvInteractionPort port = interactionPort != null
                ? interactionPort
                : new EmvInteractionPort.Auto();
        switch (request.getType()) {
            case SELECT_APPLICATION:
                port.onSelectApplication(request.getCandidates());
                break;
            case ENTER_PIN:
                Object pinType = request.getExtras().get("pinType");
                port.onEnterPin(pinType != null ? pinType.toString() : "PIN");
                break;
            case CONFIRM_AMOUNT:
                long amount = context.getConfig() != null
                        ? context.getConfig().getAmountMinor()
                        : 0L;
                port.onConfirmAmount(amount);
                break;
            case DISPLAY_MESSAGE:
            case SEE_PHONE:
            case CONFIRM_CARD:
            default:
                port.onDisplayMessage(request.getMessage() != null
                        ? request.getMessage()
                        : "");
                break;
        }
    }

    @Nullable
    private EmvSignal park(long timeoutMs) {
        CountDownLatch latch = new CountDownLatch(1);
        synchronized (waitLock) {
            pendingSignal = null;
            waitLatch = latch;
            phase.set(EnginePhase.WAITING);
        }
        try {
            boolean ok = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            synchronized (waitLock) {
                EmvSignal signal = pendingSignal;
                pendingSignal = null;
                waitLatch = null;
                if (phase.get() == EnginePhase.WAITING) {
                    phase.set(EnginePhase.RUNNING);
                }
                return ok ? signal : null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            phase.set(EnginePhase.RUNNING);
            return new EmvSignal.UserCancel();
        }
    }

    private void publishTransactionMilestone(EmvStep step) {
        if (policy == null) {
            return;
        }
        TransactionStep mapped = policy.mapToTransactionStep(step, context);
        if (mapped == null) {
            return;
        }
        TransactionStepEvent.Builder builder = TransactionStepEvent.builder(mapped);
        if (mapped == TransactionStep.CARDHOLDER_VERIFIED) {
            builder.put(TransactionStepEvent.KEY_ONLINE_PIN,
                            context.getBoolean(EmvContext.KEY_PIN_ONLINE, false))
                    .put(TransactionStepEvent.KEY_PIN_BYPASS,
                            context.getBoolean(EmvContext.KEY_PIN_BYPASS, false))
                    .put(TransactionStepEvent.KEY_PIN_TRIES_LEFT,
                            context.getInt(EmvContext.KEY_PIN_TRIES, 3));
        }
        if (mapped == TransactionStep.CARD_READ || mapped == TransactionStep.CARD_DETECTED) {
            String pan = context.getString(EmvContext.KEY_PAN);
            if (pan != null) {
                builder.put(TransactionStepEvent.KEY_PAN, pan);
            }
            String issuer = context.getString(EmvContext.KEY_ISSUER);
            if (issuer != null) {
                builder.put(TransactionStepEvent.KEY_ISSUER_NAME, issuer);
            }
            String holder = context.getString(EmvContext.KEY_CARDHOLDER);
            if (holder != null) {
                builder.put(TransactionStepEvent.KEY_CARDHOLDER_NAME, holder);
            }
            if (context.getCard() != null) {
                builder.put(TransactionStepEvent.KEY_MODE, context.getCard().getModeLabel());
            }
        }
        notifyTransactionStep(builder.build());
    }

    private void finishFromContext() {
        phase.set(EnginePhase.COMPLETED);
        boolean approved = context.getBoolean(EmvContext.KEY_APPROVED, false);
        String message = context.getString(EmvContext.KEY_RESULT_MESSAGE);
        if (approved) {
            notifyApproved(message != null ? message : "APPROVED");
        } else {
            AuthResult host = context.getHostResult();
            String reason = message;
            if (reason == null && host != null) {
                reason = host.getMessage();
            }
            notifyDeclined(reason != null ? reason : "DECLINED");
        }
        cleanup();
        notifyCompleted();
        phase.set(EnginePhase.IDLE);
    }

    private void finishFailed(EmvError error) {
        phase.set(EnginePhase.FAILED);
        cleanup();
        notifyError(error.getMessage() != null ? error.getMessage() : error.getCode());
        phase.set(EnginePhase.IDLE);
    }

    private void cleanup() {
        if (cleanupHandler != null) {
            cleanupHandler.cleanup(context);
        }
        if (kernelPort != null) {
            kernelPort.abort();
        }
        pendingBehavior = null;
    }

    // ─── Event bus (legacy + orchestrator) ───────────────────────────────

    public void notifyEmvStep(EmvStep step, @Nullable String detail) {
        EmvStepEvent event = new EmvStepEvent(step, detail);
        emvSteps.onNext(event);
        if (behavior != null) {
            behavior.dispatchEmvStep(event);
        }
    }

    public void notifyEmvStep(EmvStep step) {
        notifyEmvStep(step, null);
    }

    public void notifyTransactionStep(TransactionStepEvent event) {
        transactionSteps.onNext(event);
        if (behavior != null) {
            behavior.dispatchTransactionStep(event);
        }
    }

    public void notifyCardDetected(String pan, String issuerName,
            String cardHolderName, String mode) {
        context.put(EmvContext.KEY_PAN, pan);
        context.put(EmvContext.KEY_ISSUER, issuerName);
        context.put(EmvContext.KEY_CARDHOLDER, cardHolderName);
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_PAN, pan)
                .put(TransactionStepEvent.KEY_ISSUER_NAME, issuerName)
                .put(TransactionStepEvent.KEY_CARDHOLDER_NAME, cardHolderName)
                .put(TransactionStepEvent.KEY_MODE, mode)
                .build());
    }

    public void notifyApproved(String result) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.APPROVED)
                .put(TransactionStepEvent.KEY_RESULT, result)
                .build());
    }

    public void notifyDeclined(String reason) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.DECLINED)
                .put(TransactionStepEvent.KEY_ERROR, reason)
                .build());
    }

    public void notifyError(String error) {
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ERROR)
                .message(error)
                .put(TransactionStepEvent.KEY_ERROR, error)
                .build());
        running.set(false);
        if (phase.get() != EnginePhase.CANCELLED && phase.get() != EnginePhase.FAILED) {
            phase.set(EnginePhase.FAILED);
        }
    }

    public void notifyCompleted() {
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.COMPLETED));
        running.set(false);
        phase.set(EnginePhase.COMPLETED);
    }

    // ─── Host ports ──────────────────────────────────────────────────────

    public AuthResult authorize(TransactionConfig config) {
        if (communication == null) {
            throw new IllegalStateException("No CommunicationBehavior attached to EmvEngine");
        }
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        AuthResult result;
        try {
            result = communication.authorize(config).blockingGet();
        } catch (Exception e) {
            result = AuthResult.declined("96",
                    e.getMessage() != null ? e.getMessage() : "Online failed");
        }
        notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.ONLINE_COMPLETED)
                .put(TransactionStepEvent.KEY_RESULT, result.getMessage())
                .build());
        return result;
    }

    public void print(List<String> lines) {
        if (printer == null) {
            throw new IllegalStateException("No PrinterBehavior attached to EmvEngine");
        }
        printer.print(lines).blockingAwait();
    }

    // ─── Bridge ──────────────────────────────────────────────────────────

    private final class EngineBehaviorBridge implements BehaviorBridge {
        @Override
        public void notifyEmvStep(EmvStep step, @Nullable String detail) {
            EmvEngine.this.notifyEmvStep(step, detail);
        }

        @Override
        public void requestInteraction(InteractionRequest request) {
            dispatchInteraction(request);
        }

        @Override
        public EmvKernelPort kernel() {
            if (kernelPort == null) {
                throw new IllegalStateException("No EmvKernelPort attached");
            }
            return kernelPort;
        }

        @Override
        public AuthResult authorize() {
            TransactionConfig config = context.getConfig();
            if (config == null) {
                return AuthResult.declined("96", "No transaction config");
            }
            return EmvEngine.this.authorize(config);
        }

        @Override
        public boolean isCancelled() {
            return context.isCancelled();
        }
    }
}

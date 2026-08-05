package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.core.Single;
import java.util.concurrent.TimeUnit;

/**
 * Runs one transaction: resolve a behavior, guard it, execute it, apply retry, emit progress,
 * interpret the outcome — and nothing else. It holds no EMV domain knowledge and never
 * inspects a TLV.
 *
 * <p>Two properties are worth calling out because they are the point of the design:
 *
 * <ul>
 *   <li><b>No switch over {@link EmvStep}.</b> Behaviors come from a map; the next step comes
 *       from {@link FlowPlan}. The only branching is over the four {@link StepOutcome}
 *       variants, which is closed and total.
 *   <li><b>Progress is reported here, once.</b> Behaviors never call {@code notifyEmvStep},
 *       so the boilerplate that would otherwise be copied into seventeen classes exists in
 *       exactly one place.
 * </ul>
 */
public final class EmvFlowOrchestrator implements StepDispatcher {

    private final FlowPlan plan;
    private final StepRegistry registry;
    private final StepDriver driver;
    private final EmvEngine engine;
    private final Scheduler flowScheduler;
    private final FlowFinalizer finalizer;

    /**
     * @param flowScheduler must be single-threaded — a vendor kernel typically has to be
     *                      entered from one thread for the whole transaction
     */
    public EmvFlowOrchestrator(@NonNull FlowPlan plan,
            @NonNull StepRegistry registry,
            @NonNull StepDriver driver,
            @NonNull EmvEngine engine,
            @NonNull Scheduler flowScheduler,
            @NonNull FlowFinalizer finalizer) {
        this.plan = plan;
        this.registry = registry;
        this.driver = driver;
        this.engine = engine;
        this.flowScheduler = flowScheduler;
        this.finalizer = finalizer;
        // Boot-time contract check: no missing behaviors, no behavior on a SYNTHETIC step.
        registry.validateAgainst(plan);
    }

    /** Runs the transaction to completion. The returned Single never fails. */
    @NonNull
    public Single<TransactionResult> run(@NonNull EmvContext ctx) {
        return Single.defer(() -> driver.drive(plan, ctx, this))
                .onErrorReturn(error -> TransactionResult.aborted(EmvError.of(error)))
                .map(result -> {
                    finalize(ctx, result);
                    return result;
                })
                .subscribeOn(flowScheduler);
    }

    @NonNull
    @Override
    public Single<StepOutcome> dispatch(@NonNull EmvStep step, @NonNull EmvContext ctx) {
        return Single.defer(() -> attempt(step, ctx, 1));
    }

    private Single<StepOutcome> attempt(EmvStep step, EmvContext ctx, int attemptNo) {

        if (ctx.cancellation().isCancelled()) {
            return Single.just(StepOutcome.terminate(TransactionResult.aborted(
                    EmvError.fatal("CANCELLED", "Transaction cancelled"))));
        }

        if (plan.kindOf(step) == StepKind.SYNTHETIC) {
            // The kernel performs it internally; emit the marker so the UI ladder stays whole.
            engine.notifyEmvStep(step, null);
            return Single.just(StepOutcome.next());
        }

        EmvStepBehavior behavior = registry.resolve(step);
        if (behavior == null) {
            return Single.just(StepOutcome.fail(EmvError.fatal("NO_BEHAVIOR",
                    "No behavior registered for " + step)));
        }

        if (!behavior.isApplicable(ctx)) {
            return Single.just(StepOutcome.next());
        }

        engine.notifyEmvStep(step, attemptNo > 1 ? "retry " + attemptNo : null);

        return behavior.execute(ctx)
                .onErrorReturn(error -> StepOutcome.fail(EmvError.of(error)))
                .flatMap(outcome -> maybeRetry(step, ctx, attemptNo, outcome, behavior));
    }

    private Single<StepOutcome> maybeRetry(EmvStep step, EmvContext ctx, int attemptNo,
            StepOutcome outcome, EmvStepBehavior behavior) {

        if (!(outcome instanceof StepOutcome.Fail fail)) {
            return Single.just(outcome);
        }

        EmvError error = fail.getError();
        RetryPolicy policy = plan.retryPolicyFor(step);

        if (!error.isRecoverable() || attemptNo >= policy.getMaxAttempts()) {
            behavior.onAbort(ctx);
            return Single.just(outcome);
        }

        Single<StepOutcome> retry = Single.defer(() -> attempt(step, ctx, attemptNo + 1));
        return policy.getDelayMillis() > 0
                ? retry.delaySubscription(policy.getDelayMillis(), TimeUnit.MILLISECONDS,
                        flowScheduler)
                : retry;
    }

    /** Always runs, however the transaction ended. */
    private void finalize(EmvContext ctx, TransactionResult result) {
        try {
            finalizer.onFlowFinished(ctx, result);
        } catch (Exception e) {
            // Cleanup must never mask the result the cardholder is waiting for.
            engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION,
                    "finalizer failed: " + e.getMessage());
        }

        switch (result.getStatus()) {
            case APPROVED:
                engine.notifyApproved(result.getDetail());
                engine.notifyCompleted();
                break;
            case DECLINED:
                engine.notifyDeclined(result.getDetail());
                engine.notifyCompleted();
                break;
            case ABORTED:
            default:
                // notifyError already clears the engine's running flag.
                engine.notifyError(result.getDetail());
                break;
        }
    }
}

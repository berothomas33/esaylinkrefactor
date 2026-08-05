package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.event.EmvStep;
import io.reactivex.rxjava3.core.Single;

/**
 * Our loop holds control: walk the plan, dispatch each step, follow the outcome.
 *
 * <p>Used by software kernels and simulators. A vendor whose kernel drives the transaction
 * itself (PAX) supplies a callback-based {@link StepDriver} instead — the behaviors and the
 * plan are unchanged.
 */
public final class SequentialStepDriver implements StepDriver {

    /** Guards against a behavior that jumps in a cycle forever. */
    private static final int DEFAULT_MAX_TRANSITIONS = 200;

    private final int maxTransitions;

    public SequentialStepDriver() {
        this(DEFAULT_MAX_TRANSITIONS);
    }

    public SequentialStepDriver(int maxTransitions) {
        this.maxTransitions = maxTransitions;
    }

    @NonNull
    @Override
    public Single<TransactionResult> drive(@NonNull FlowPlan plan,
            @NonNull EmvContext ctx,
            @NonNull StepDispatcher dispatcher) {
        return Single.defer(() -> step(plan.entryStep(), plan, ctx, dispatcher, 0));
    }

    private Single<TransactionResult> step(EmvStep current, FlowPlan plan, EmvContext ctx,
            StepDispatcher dispatcher, int transitions) {

        if (transitions > maxTransitions) {
            return Single.just(TransactionResult.aborted(EmvError.fatal("FLOW_LOOP",
                    "Exceeded " + maxTransitions + " transitions — probable jumpTo cycle at "
                            + current)));
        }

        return dispatcher.dispatch(current, ctx).flatMap(outcome -> {

            if (outcome instanceof StepOutcome.Terminate terminate) {
                return Single.just(terminate.getResult());
            }
            if (outcome instanceof StepOutcome.Fail fail) {
                // Retries are already exhausted inside the dispatcher.
                return Single.just(TransactionResult.aborted(fail.getError()));
            }
            if (outcome instanceof StepOutcome.JumpTo jump) {
                return Single.defer(() ->
                        step(jump.getTarget(), plan, ctx, dispatcher, transitions + 1));
            }

            // StepOutcome.Next — ask the plan, never a switch on the step itself.
            return plan.successorOf(current)
                    .<Single<TransactionResult>>map(next -> Single.defer(() ->
                            step(next, plan, ctx, dispatcher, transitions + 1)))
                    .orElseGet(() -> Single.just(TransactionResult.aborted(
                            EmvError.fatal("PLAN_END", "Flow reached the end of the plan after "
                                    + current + " without a terminal outcome"))));
        });
    }
}

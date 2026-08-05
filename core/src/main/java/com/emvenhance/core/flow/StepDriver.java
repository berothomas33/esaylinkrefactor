package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import io.reactivex.rxjava3.core.Single;

/**
 * Who holds the thread of control during a transaction.
 *
 * <p>This is the abstraction that lets one set of behavior classes serve two very different
 * vendors:
 *
 * <ul>
 *   <li>{@link SequentialStepDriver} — our loop walks the plan. Software kernels, simulators.
 *   <li>A kernel-callback driver — the vendor binary drives and calls back at the points it
 *       chooses, and each callback is translated into a {@link StepDispatcher#dispatch} call.
 *       Required for PAX, whose {@code startTransProcess} runs the flow internally.
 * </ul>
 */
public interface StepDriver {

    @NonNull
    Single<TransactionResult> drive(@NonNull FlowPlan plan,
            @NonNull EmvContext ctx,
            @NonNull StepDispatcher dispatcher);
}

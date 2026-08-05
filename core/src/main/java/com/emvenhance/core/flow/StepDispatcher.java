package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.event.EmvStep;
import io.reactivex.rxjava3.core.Single;

/**
 * What a {@link StepDriver} is given so it can run one step without knowing how steps are
 * resolved, guarded, retried or reported.
 *
 * <p>Implemented by {@link EmvFlowOrchestrator}. Retry is handled inside {@code dispatch},
 * so a driver only ever observes a {@link StepOutcome.Fail} once attempts are exhausted.
 */
public interface StepDispatcher {

    @NonNull
    Single<StepOutcome> dispatch(@NonNull EmvStep step, @NonNull EmvContext ctx);
}

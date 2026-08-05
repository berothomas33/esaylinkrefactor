package com.emvenhance.core.behavior;

import androidx.annotation.Nullable;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;

/**
 * Pure transition rules: given current step + result + context, choose the next step
 * (or {@code null} to finish). No I/O.
 */
public interface EmvTransitionPolicy {

    /**
     * @return next {@link EmvStep}, or {@code null} when the flow is complete
     */
    @Nullable
    EmvStep next(EmvStep from, BehaviorResult result, EmvContext context);

    /** Maps a fine EMV step to a coarse UI transaction milestone when entering it. */
    @Nullable
    TransactionStep mapToTransactionStep(EmvStep emvStep, EmvContext context);
}

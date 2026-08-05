package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.event.EmvStep;
import java.util.Set;

/**
 * The step graph as data: where the flow starts, what follows what, who owns each step and
 * how often it may be retried.
 *
 * <p>This is the piece that removes the switch statement. The orchestrator asks the plan
 * "what comes after this step?" — it never branches on step identity itself. Adding an EMV
 * phase is a new behavior plus one edge here; no existing file changes.
 */
public interface FlowPlan {

    /** Where the flow begins. */
    @NonNull
    EmvStep entryStep();

    /** The next step, or empty when {@code current} is the end of the plan. */
    @NonNull
    java.util.Optional<EmvStep> successorOf(@NonNull EmvStep current);

    /** Who executes this step for this vendor. */
    @NonNull
    StepKind kindOf(@NonNull EmvStep step);

    /** How many attempts this step gets when it fails recoverably. */
    @NonNull
    RetryPolicy retryPolicyFor(@NonNull EmvStep step);

    /** Every step in the plan, in flow order. Used for startup validation. */
    @NonNull
    Set<EmvStep> steps();
}

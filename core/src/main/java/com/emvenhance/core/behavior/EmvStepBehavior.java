package com.emvenhance.core.behavior;

import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.port.EmvSignal;

/**
 * One EMV phase as an isolated, testable behavior.
 *
 * <p>Behaviors never call sibling behaviors. They return a {@link BehaviorResult}; the
 * engine advances via transition policy.
 */
public interface EmvStepBehavior {

    /** Fine-grained EMV step this behavior owns. */
    EmvStep step();

    /**
     * Run (or start) this phase. May return {@code Wait*} for async UI/kernel/host work.
     */
    BehaviorResult execute(EmvContext context, BehaviorBridge bridge);

    /**
     * Continue after {@link com.emvenhance.core.engine.EmvEngine#resume(EmvSignal)}.
     * Default: ignore unexpected signals.
     */
    default BehaviorResult onSignal(EmvContext context, BehaviorBridge bridge, EmvSignal signal) {
        return BehaviorResult.failure(EmvError.of(
                "UNEXPECTED_SIGNAL",
                "Unexpected signal " + signal.getClass().getSimpleName() + " for " + step(),
                step(),
                false));
    }

    /** Release resources when the engine cancels mid-step. */
    default void onCancel(EmvContext context) {
        // default no-op
    }
}

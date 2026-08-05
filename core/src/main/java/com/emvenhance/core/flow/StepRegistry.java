package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.emvenhance.core.event.EmvStep;
import java.util.EnumMap;
import java.util.Map;

/**
 * Lookup table from {@link EmvStep} to the behavior that owns it. A map, not a sequencer —
 * it knows nothing about order.
 *
 * <p>{@link #validateAgainst(FlowPlan)} turns the most dangerous possible mistake into a
 * boot-time failure: registering a behavior for a step the active plan marks
 * {@link StepKind#SYNTHETIC} means that logic would silently never run on the target
 * hardware, which is exactly the kind of defect that surfaces during certification instead of
 * development.
 */
public final class StepRegistry {

    private final Map<EmvStep, EmvStepBehavior> behaviors = new EnumMap<>(EmvStep.class);

    @NonNull
    public StepRegistry register(@NonNull EmvStepBehavior behavior) {
        EmvStep step = behavior.step();
        EmvStepBehavior existing = behaviors.put(step, behavior);
        if (existing != null) {
            throw new IllegalStateException("Two behaviors registered for " + step + ": "
                    + existing.getClass().getName() + " and " + behavior.getClass().getName());
        }
        return this;
    }

    @Nullable
    public EmvStepBehavior resolve(@NonNull EmvStep step) {
        return behaviors.get(step);
    }

    /**
     * Fails fast when the registry and the plan disagree.
     *
     * @throws IllegalStateException if an OWNED/DELEGATED step has no behavior, or a
     *                               SYNTHETIC step has one
     */
    public void validateAgainst(@NonNull FlowPlan plan) {
        for (EmvStep step : plan.steps()) {
            StepKind kind = plan.kindOf(step);
            boolean registered = behaviors.containsKey(step);

            if (kind == StepKind.SYNTHETIC && registered) {
                throw new IllegalStateException(step + " is SYNTHETIC in this plan — the vendor "
                        + "kernel performs it internally with no callback, so "
                        + behaviors.get(step).getClass().getName()
                        + " would never run. Remove it or change the plan.");
            }
            if (kind != StepKind.SYNTHETIC && !registered) {
                throw new IllegalStateException(step + " is " + kind
                        + " in this plan but no behavior is registered for it.");
            }
        }
    }
}

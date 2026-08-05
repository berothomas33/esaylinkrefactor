package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.event.EmvStep;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link FlowPlan} built as an ordered chain: each step's successor is the next one added.
 *
 * <p>Non-linear moves (fallback, second tap, PIN re-entry) are still fully supported — a
 * behavior expresses them with {@link StepOutcome#jumpTo}, so the plan only has to describe
 * the happy path.
 *
 * <pre>
 *   FlowPlan plan = LinearFlowPlan.builder()
 *           .owned(EmvStep.TERMINAL_INITIALIZATION)
 *           .delegated(EmvStep.CARDHOLDER_VERIFICATION, RetryPolicy.times(3))
 *           .synthetic(EmvStep.TERMINAL_RISK_MANAGEMENT)
 *           .build();
 * </pre>
 */
public final class LinearFlowPlan implements FlowPlan {

    private final Set<EmvStep> ordered;
    private final Map<EmvStep, EmvStep> successors;
    private final Map<EmvStep, StepKind> kinds;
    private final Map<EmvStep, RetryPolicy> retries;
    private final EmvStep entry;

    private LinearFlowPlan(Set<EmvStep> ordered, Map<EmvStep, EmvStep> successors,
            Map<EmvStep, StepKind> kinds, Map<EmvStep, RetryPolicy> retries, EmvStep entry) {
        this.ordered = ordered;
        this.successors = successors;
        this.kinds = kinds;
        this.retries = retries;
        this.entry = entry;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    @Override
    public EmvStep entryStep() {
        return entry;
    }

    @NonNull
    @Override
    public Optional<EmvStep> successorOf(@NonNull EmvStep current) {
        return Optional.ofNullable(successors.get(current));
    }

    @NonNull
    @Override
    public StepKind kindOf(@NonNull EmvStep step) {
        StepKind kind = kinds.get(step);
        if (kind == null) {
            throw new IllegalArgumentException(step + " is not part of this flow plan");
        }
        return kind;
    }

    @NonNull
    @Override
    public RetryPolicy retryPolicyFor(@NonNull EmvStep step) {
        return retries.getOrDefault(step, RetryPolicy.none());
    }

    @NonNull
    @Override
    public Set<EmvStep> steps() {
        return Collections.unmodifiableSet(ordered);
    }

    public static final class Builder {

        private final Set<EmvStep> ordered = new LinkedHashSet<>();
        private final Map<EmvStep, StepKind> kinds = new EnumMap<>(EmvStep.class);
        private final Map<EmvStep, RetryPolicy> retries = new EnumMap<>(EmvStep.class);

        /** We execute this step; a behavior must be registered. */
        @NonNull
        public Builder owned(@NonNull EmvStep step) {
            return add(step, StepKind.OWNED, RetryPolicy.none());
        }

        @NonNull
        public Builder owned(@NonNull EmvStep step, @NonNull RetryPolicy retry) {
            return add(step, StepKind.OWNED, retry);
        }

        /** The vendor kernel executes it but calls back; a behavior must be registered. */
        @NonNull
        public Builder delegated(@NonNull EmvStep step) {
            return add(step, StepKind.DELEGATED, RetryPolicy.none());
        }

        @NonNull
        public Builder delegated(@NonNull EmvStep step, @NonNull RetryPolicy retry) {
            return add(step, StepKind.DELEGATED, retry);
        }

        /** Progress marker only — the kernel performs it with no hook. No behavior allowed. */
        @NonNull
        public Builder synthetic(@NonNull EmvStep step) {
            return add(step, StepKind.SYNTHETIC, RetryPolicy.none());
        }

        private Builder add(EmvStep step, StepKind kind, RetryPolicy retry) {
            if (!ordered.add(step)) {
                throw new IllegalArgumentException(step + " added to the plan twice");
            }
            kinds.put(step, kind);
            retries.put(step, retry);
            return this;
        }

        @NonNull
        public FlowPlan build() {
            if (ordered.isEmpty()) {
                throw new IllegalStateException("A flow plan needs at least one step");
            }
            Map<EmvStep, EmvStep> successors = new EnumMap<>(EmvStep.class);
            EmvStep previous = null;
            EmvStep entry = null;
            for (EmvStep step : ordered) {
                if (entry == null) {
                    entry = step;
                }
                if (previous != null) {
                    successors.put(previous, step);
                }
                previous = step;
            }
            return new LinearFlowPlan(new LinkedHashSet<>(ordered), successors,
                    new EnumMap<>(kinds), new EnumMap<>(retries), entry);
        }
    }
}

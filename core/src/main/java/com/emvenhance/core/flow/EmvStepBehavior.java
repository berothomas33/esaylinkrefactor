package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.event.EmvStep;
import io.reactivex.rxjava3.core.Single;

/**
 * The business logic of exactly one EMV phase.
 *
 * <p>A behavior owns its phase and nothing else. It does not know the step order, does not
 * report its own progress (the orchestrator emits step events around it), does not choose a
 * thread, and holds no reference to any other behavior — which is what makes it independently
 * testable:
 *
 * <pre>
 *   new CardholderVerificationBehavior().execute(fakeContext)
 * </pre>
 *
 * <p>Implementations must be <b>stateless</b>. All per-transaction state belongs in
 * {@link EmvContext}.
 */
public interface EmvStepBehavior {

    /** Which EMV phase this behavior owns. Used for {@link StepRegistry} lookup. */
    @NonNull
    EmvStep step();

    /**
     * Guard deciding whether this step applies to the transaction in flight — for example,
     * offline PIN verification only when the CVM list selected it. Keeps conditionals out of
     * the orchestrator and out of the plan.
     */
    default boolean isApplicable(@NonNull EmvContext ctx) {
        return true;
    }

    /**
     * The single execution method. Async by construction: return {@code Single.just(...)}
     * when the work is synchronous, or a deferred {@code Single} when waiting on PIN entry,
     * host authorization or an issuer script.
     *
     * <p>Never call {@code subscribeOn} here — the orchestrator owns threading, because the
     * PAX kernel must be entered from one thread for the transaction's lifetime.
     */
    @NonNull
    Single<StepOutcome> execute(@NonNull EmvContext ctx);

    /** Best-effort cleanup when the flow is cancelled or aborted while this step is active. */
    default void onAbort(@NonNull EmvContext ctx) {
        // default: nothing to release
    }
}

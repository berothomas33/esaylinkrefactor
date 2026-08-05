package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import com.emvenhance.core.event.EmvStep;

/**
 * What a step reported when it finished. A closed set of four — the orchestrator branches
 * on <em>outcome</em>, never on which step produced it, which is what keeps a switch over
 * {@link EmvStep} out of the flow entirely.
 *
 * <p>There is deliberately no {@code Suspend} variant: asynchrony is carried by the
 * {@link io.reactivex.rxjava3.core.Single} a behavior returns, so a step that waits for PIN
 * entry or host authorization simply completes later.
 */
public sealed interface StepOutcome {

    /** Proceed to whatever {@link FlowPlan#successorOf} says comes next. */
    @NonNull
    static StepOutcome next() {
        return Next.INSTANCE;
    }

    /**
     * Transition somewhere other than the plan's successor. EMV is not a straight line:
     * fallback to contact, "try another interface", a second tap and PIN re-entry are all
     * real backward or lateral moves.
     */
    @NonNull
    static StepOutcome jumpTo(@NonNull EmvStep target) {
        return new JumpTo(target);
    }

    /** The step failed. {@link EmvError#isRecoverable()} decides whether retry applies. */
    @NonNull
    static StepOutcome fail(@NonNull EmvError error) {
        return new Fail(error);
    }

    /** The transaction is finished; run the finalizer and return this result. */
    @NonNull
    static StepOutcome terminate(@NonNull TransactionResult result) {
        return new Terminate(result);
    }

    final class Next implements StepOutcome {
        static final Next INSTANCE = new Next();

        private Next() {
        }

        @NonNull
        @Override
        public String toString() {
            return "Next";
        }
    }

    final class JumpTo implements StepOutcome {
        private final EmvStep target;

        private JumpTo(EmvStep target) {
            this.target = target;
        }

        @NonNull
        public EmvStep getTarget() {
            return target;
        }

        @NonNull
        @Override
        public String toString() {
            return "JumpTo(" + target + ")";
        }
    }

    final class Fail implements StepOutcome {
        private final EmvError error;

        private Fail(EmvError error) {
            this.error = error;
        }

        @NonNull
        public EmvError getError() {
            return error;
        }

        @NonNull
        @Override
        public String toString() {
            return "Fail(" + error + ")";
        }
    }

    final class Terminate implements StepOutcome {
        private final TransactionResult result;

        private Terminate(TransactionResult result) {
            this.result = result;
        }

        @NonNull
        public TransactionResult getResult() {
            return result;
        }

        @NonNull
        @Override
        public String toString() {
            return "Terminate(" + result + ")";
        }
    }
}

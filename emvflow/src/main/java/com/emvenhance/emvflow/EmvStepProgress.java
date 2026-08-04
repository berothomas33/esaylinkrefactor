package com.emvenhance.emvflow;

import androidx.annotation.Nullable;
import com.emvenhance.core.EmvStep;
import java.util.function.BiConsumer;

/**
 * Reports {@link EmvStep}s in order, filling gaps the PAX kernel never announces.
 */
public final class EmvStepProgress {

    private static final EmvStep[] STEPS = EmvStep.values();

    private final BiConsumer<EmvStep, String> reporter;
    private int reported = -1;

    public EmvStepProgress(BiConsumer<EmvStep, String> reporter) {
        this.reporter = reporter;
    }

    /** Reports every step after the last reported one, up to and including {@code target}. */
    public synchronized void advanceTo(EmvStep target, @Nullable String detail) {
        int last = target.ordinal();
        for (int i = reported + 1; i <= last; i++) {
            reporter.accept(STEPS[i], i == last ? detail : null);
        }
        if (last > reported) {
            reported = last;
        }
    }
}

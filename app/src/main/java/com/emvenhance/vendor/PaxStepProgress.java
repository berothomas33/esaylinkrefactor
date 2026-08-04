package com.emvenhance.vendor;

import androidx.annotation.Nullable;
import com.emvenhance.core.EmvEngine;
import com.emvenhance.core.EmvStep;

/**
 * Gap-filling EMV step reporter for PAX. When the PAX kernel reaches step N,
 * replays all earlier steps in order so observers never miss an intermediate step.
 *
 * <p>Package-private — only used by {@link PaxEmvBehavior}.
 */
final class PaxStepProgress {

    private static final EmvStep[] STEPS = EmvStep.values();

    private final EmvEngine engine;
    private int reported = -1;

    PaxStepProgress(EmvEngine engine) {
        this.engine = engine;
    }

    /**
     * Advance to {@code target}, emitting every step between the last reported step
     * and the target (inclusive). Only the target step carries the {@code detail} string;
     * intermediate gap-fill steps are emitted with {@code null} detail.
     */
    synchronized void advanceTo(EmvStep target, @Nullable String detail) {
        int last = target.ordinal();
        for (int i = reported + 1; i <= last; i++) {
            engine.notifyEmvStep(STEPS[i], i == last ? detail : null);
        }
        if (last > reported) {
            reported = last;
        }
    }
}

package com.emvenhance.emvflow;

import androidx.annotation.Nullable;
import com.emvenhance.core.EmvBehavior;
import com.emvenhance.core.EmvStep;

/**
 * Reports {@link EmvStep}s in order, filling the gaps the PAX kernel never announces.
 *
 * <p>The kernel only calls back at a handful of points, so steps it performs internally
 * (offline data authentication, processing restrictions, risk management, action analysis)
 * are never reported on their own. When a callback tells us the kernel has reached step N,
 * every earlier step must already have happened, so this replays them in order.
 *
 * <p>Progress is monotonic: a step is never reported twice, and never goes backwards. This
 * relies on {@link EmvStep} being declared in flow order, which it is.
 */
public final class EmvStepProgress {

    private static final EmvStep[] STEPS = EmvStep.values();

    private final EmvBehavior.Callback callback;
    private int reported = -1;

    public EmvStepProgress(EmvBehavior.Callback callback) {
        this.callback = callback;
    }

    /** Reports every step after the last reported one, up to and including {@code target}. */
    public synchronized void advanceTo(EmvStep target, @Nullable String detail) {
        int last = target.ordinal();
        for (int i = reported + 1; i <= last; i++) {
            callback.onStep(STEPS[i], i == last ? detail : null);
        }
        if (last > reported) {
            reported = last;
        }
    }
}

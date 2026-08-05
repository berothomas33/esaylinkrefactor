package com.emvenhance.core.flow;

import androidx.annotation.NonNull;

/**
 * How many times the orchestrator re-invokes a step that returned a recoverable failure.
 *
 * <p>Declared per step in the {@link FlowPlan}, so behaviors carry no retry counters — a PIN
 * behavior just reports "wrong PIN" and is asked again.
 */
public final class RetryPolicy {

    private static final RetryPolicy NONE = new RetryPolicy(1, 0L);

    private final int maxAttempts;
    private final long delayMillis;

    private RetryPolicy(int maxAttempts, long delayMillis) {
        this.maxAttempts = maxAttempts;
        this.delayMillis = delayMillis;
    }

    /** Execute once; a recoverable failure is treated as final. */
    @NonNull
    public static RetryPolicy none() {
        return NONE;
    }

    /** Execute at most {@code maxAttempts} times, with no delay between attempts. */
    @NonNull
    public static RetryPolicy times(int maxAttempts) {
        return new RetryPolicy(Math.max(1, maxAttempts), 0L);
    }

    /** Execute at most {@code maxAttempts} times, pausing {@code delayMillis} between them. */
    @NonNull
    public static RetryPolicy times(int maxAttempts, long delayMillis) {
        return new RetryPolicy(Math.max(1, maxAttempts), Math.max(0L, delayMillis));
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public long getDelayMillis() {
        return delayMillis;
    }
}

package com.emvenhance.core.behavior;

import androidx.annotation.Nullable;
import com.emvenhance.core.event.EmvStep;

/**
 * Stable EMV failure descriptor returned inside {@link BehaviorResult.Failure}.
 */
public final class EmvError {

    private final String code;
    private final String message;
    @Nullable
    private final EmvStep step;
    private final boolean retryable;
    @Nullable
    private final Throwable cause;

    public EmvError(String code, String message, @Nullable EmvStep step,
            boolean retryable, @Nullable Throwable cause) {
        this.code = code;
        this.message = message;
        this.step = step;
        this.retryable = retryable;
        this.cause = cause;
    }

    public static EmvError of(String code, String message, @Nullable EmvStep step,
            boolean retryable) {
        return new EmvError(code, message, step, retryable, null);
    }

    public static EmvError cancelled(@Nullable EmvStep step) {
        return of("USER_CANCEL", "Transaction cancelled", step, false);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Nullable
    public EmvStep getStep() {
        return step;
    }

    public boolean isRetryable() {
        return retryable;
    }

    @Nullable
    public Throwable getCause() {
        return cause;
    }
}

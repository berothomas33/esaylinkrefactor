package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A failure produced by a step. Errors are values, not exceptions — a behavior reports
 * failure by returning {@link StepOutcome#fail(EmvError)} rather than throwing.
 *
 * <p>{@link #isRecoverable()} decides whether the orchestrator consults the step's
 * {@link RetryPolicy} or goes straight to finalization.
 */
public final class EmvError {

    private final String code;
    private final String message;
    private final boolean recoverable;
    @Nullable
    private final Throwable cause;

    private EmvError(String code, String message, boolean recoverable, @Nullable Throwable cause) {
        this.code = code;
        this.message = message;
        this.recoverable = recoverable;
        this.cause = cause;
    }

    /** The step may be retried if its {@link RetryPolicy} still has attempts left. */
    @NonNull
    public static EmvError recoverable(@NonNull String code, @NonNull String message) {
        return new EmvError(code, message, true, null);
    }

    /** The transaction cannot continue; the orchestrator finalizes immediately. */
    @NonNull
    public static EmvError fatal(@NonNull String code, @NonNull String message) {
        return new EmvError(code, message, false, null);
    }

    /** Wraps a thrown exception escaping a behavior. Always fatal. */
    @NonNull
    public static EmvError of(@NonNull Throwable cause) {
        String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        return new EmvError("EXCEPTION", message, false, cause);
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    @Nullable
    public Throwable getCause() {
        return cause;
    }

    @NonNull
    @Override
    public String toString() {
        return code + ": " + message;
    }
}

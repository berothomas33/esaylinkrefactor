package com.emvenhance.core.terminal;

import androidx.annotation.Nullable;

/**
 * Outcome of one EMV step method on {@link EmvBehavior}.
 *
 * <ul>
 *   <li>{@link #continueResult()} — step succeeded; run the next step</li>
 *   <li>{@link #skip(String)} — step not needed for this entry method; run the next step</li>
 *   <li>{@link #fail(String)} — abort transaction with error</li>
 *   <li>{@link #approved(String)} — finish successfully (stops the remaining steps)</li>
 *   <li>{@link #declined(String)} — finish declined (stops the remaining steps)</li>
 * </ul>
 */
public final class EmvStepResult {

    public enum Status {
        CONTINUE,
        SKIP,
        FAIL,
        APPROVED,
        DECLINED
    }

    private final Status status;
    @Nullable
    private final String detail;

    private EmvStepResult(Status status, @Nullable String detail) {
        this.status = status;
        this.detail = detail;
    }

    public static EmvStepResult continueResult() {
        return new EmvStepResult(Status.CONTINUE, null);
    }

    public static EmvStepResult continueResult(@Nullable String detail) {
        return new EmvStepResult(Status.CONTINUE, detail);
    }

    public static EmvStepResult skip(@Nullable String reason) {
        return new EmvStepResult(Status.SKIP, reason);
    }

    public static EmvStepResult fail(String message) {
        return new EmvStepResult(Status.FAIL, message);
    }

    public static EmvStepResult approved(String message) {
        return new EmvStepResult(Status.APPROVED, message);
    }

    public static EmvStepResult declined(String message) {
        return new EmvStepResult(Status.DECLINED, message);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public String getDetail() {
        return detail;
    }

    public boolean shouldContinue() {
        return status == Status.CONTINUE || status == Status.SKIP;
    }

    public boolean isTerminal() {
        return status == Status.FAIL
                || status == Status.APPROVED
                || status == Status.DECLINED;
    }
}

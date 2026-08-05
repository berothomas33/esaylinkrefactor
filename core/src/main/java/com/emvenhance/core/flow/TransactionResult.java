package com.emvenhance.core.flow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The single outcome of one EMV transaction, returned by
 * {@link EmvFlowOrchestrator#run(EmvContext)}.
 */
public final class TransactionResult {

    public enum Status {
        APPROVED,
        DECLINED,
        ABORTED
    }

    private final Status status;
    private final String detail;
    @Nullable
    private final EmvError error;

    private TransactionResult(Status status, String detail, @Nullable EmvError error) {
        this.status = status;
        this.detail = detail;
        this.error = error;
    }

    @NonNull
    public static TransactionResult approved(@NonNull String detail) {
        return new TransactionResult(Status.APPROVED, detail, null);
    }

    @NonNull
    public static TransactionResult declined(@NonNull String detail) {
        return new TransactionResult(Status.DECLINED, detail, null);
    }

    /** Cancelled by the cardholder / operator, or failed with a fatal error. */
    @NonNull
    public static TransactionResult aborted(@NonNull EmvError error) {
        return new TransactionResult(Status.ABORTED, error.getMessage(), error);
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @NonNull
    public String getDetail() {
        return detail;
    }

    @Nullable
    public EmvError getError() {
        return error;
    }

    public boolean isApproved() {
        return status == Status.APPROVED;
    }

    @NonNull
    @Override
    public String toString() {
        return status + " (" + detail + ")";
    }
}

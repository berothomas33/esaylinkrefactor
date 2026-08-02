package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Immutable snapshot of the transaction, published on the state subject.
 *
 * <p>Every emission is a new instance, so a subscriber can hold on to one safely.
 */
public final class EmvState {

    private final boolean processing;
    private final EmvStep step;
    private final String mode;
    private final String pan;
    private final String issuerName;
    private final String result;
    private final String error;

    public EmvState(boolean processing, @Nullable EmvStep step, @Nullable String mode,
            @Nullable String pan, @Nullable String issuerName, @Nullable String result,
            @Nullable String error) {
        this.processing = processing;
        this.step = step;
        this.mode = mode;
        this.pan = pan;
        this.issuerName = issuerName;
        this.result = result;
        this.error = error;
    }

    public static EmvState idle() {
        return new EmvState(false, null, null, null, null, null, null);
    }

    public boolean isProcessing() {
        return processing;
    }

    /** Current EMV step, or null before the transaction starts. */
    @Nullable
    public EmvStep getStep() {
        return step;
    }

    /** "Contact" or "Contactless". */
    @Nullable
    public String getMode() {
        return mode;
    }

    @Nullable
    public String getPan() {
        return pan;
    }

    @Nullable
    public String getIssuerName() {
        return issuerName;
    }

    /** Kernel outcome label when approved. */
    @Nullable
    public String getResult() {
        return result;
    }

    @Nullable
    public String getError() {
        return error;
    }
}

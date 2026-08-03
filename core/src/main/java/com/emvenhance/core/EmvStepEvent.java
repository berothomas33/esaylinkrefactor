package com.emvenhance.core;

import androidx.annotation.Nullable;

/**
 * Immutable event emitted on the EMV-steps {@code PublishSubject}.
 *
 * <p>Each emission represents one fine-grained EMV kernel phase that has just started or
 * completed. The optional {@code detail} carries extra information the kernel provides
 * for that particular step (e.g. "DDA" for the ODA step, or "contactless" for application
 * selection).
 *
 * <p>These events are <em>not</em> replayed to late subscribers; if the UI attaches after
 * step 5, it will only see steps 6+.
 */
public final class EmvStepEvent {

    private final EmvStep step;
    private final String detail;

    public EmvStepEvent(EmvStep step, @Nullable String detail) {
        this.step = step;
        this.detail = detail;
    }

    public EmvStep getStep() {
        return step;
    }

    @Nullable
    public String getDetail() {
        return detail;
    }

    @Override
    public String toString() {
        return step.getNumber() + ". " + step.getTitle()
                + (detail != null ? " (" + detail + ")" : "");
    }
}

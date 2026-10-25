package com.emvenhance.core.event;

import androidx.annotation.Nullable;

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

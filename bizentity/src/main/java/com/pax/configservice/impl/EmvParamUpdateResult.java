package com.pax.configservice.impl;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * What {@link EmvParamUpdater#applyFromZip} actually did — one line per section it touched, plus
 * the first error hit (if any), so a caller (e.g. an "apply EMV params" UI button) can show the
 * real outcome instead of a bare success/fail flag.
 */
public final class EmvParamUpdateResult {

    private final List<String> appliedSections = new ArrayList<>();
    @Nullable
    private String errorMessage;

    void applied(String section, int rowCount) {
        appliedSections.add(section + " (" + rowCount + ")");
    }

    void failed(String message) {
        if (errorMessage == null) {
            errorMessage = message;
        }
    }

    public boolean isSuccess() {
        return errorMessage == null;
    }

    public List<String> getAppliedSections() {
        return appliedSections;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    /** One human-readable line: what applied, or the error, or "nothing to apply". */
    public String summarize() {
        if (errorMessage != null) {
            return "Failed: " + errorMessage;
        }
        if (appliedSections.isEmpty()) {
            return "Package had no recognized sections — nothing changed";
        }
        return "Applied " + String.join(", ", appliedSections);
    }
}

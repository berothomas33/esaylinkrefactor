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
    private final List<String> removedSections = new ArrayList<>();
    private final List<String> sectionsInFiles = new ArrayList<>();
    @Nullable
    private String errorMessage;

    void applied(String section, int rowCount) {
        appliedSections.add(section + " (" + rowCount + ")");
    }

    /** A kernel the package didn't bring: its rows were deleted. */
    void removed(String section, int rowCount) {
        removedSections.add(section + " (" + rowCount + ")");
    }

    /** The top-level sections one file of the package actually had, for checking the parsing. */
    void sectionsIn(String file, List<String> sections) {
        sectionsInFiles.add(file + ": " + (sections.isEmpty() ? "none" : String.join(", ", sections)));
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

    public List<String> getRemovedSections() {
        return removedSections;
    }

    /** The database changed — rows applied or removed: the parameter cache must reload. */
    public boolean changedDatabase() {
        return !appliedSections.isEmpty() || !removedSections.isEmpty();
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    /** One human-readable line: what applied, or the error, or "nothing to apply". */
    public String summarize() {
        StringBuilder text = new StringBuilder();
        if (errorMessage != null) {
            text.append("Failed: ").append(errorMessage);
        } else if (!changedDatabase()) {
            text.append("Package had no recognized sections — nothing changed");
        }
        if (!appliedSections.isEmpty()) {
            line(text).append("Applied ").append(String.join(", ", appliedSections));
        }
        if (!removedSections.isEmpty()) {
            line(text).append("Removed (not in the package) ")
                    .append(String.join(", ", removedSections));
        }
        for (String sections : sectionsInFiles) {
            line(text).append("In ").append(sections);
        }
        return text.toString();
    }

    private static StringBuilder line(StringBuilder text) {
        return text.length() == 0 ? text : text.append('\n');
    }
}

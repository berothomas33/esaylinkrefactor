package com.pax.configservice.impl;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;

public final class EmvParamUpdateResult {

    private final List<String> appliedSections = new ArrayList<>();
    private final List<String> removedSections = new ArrayList<>();
    private final List<String> sectionsInFiles = new ArrayList<>();
    @Nullable
    private String errorMessage;

    void applied(String section, int rowCount) {
        appliedSections.add(section + " (" + rowCount + ")");
    }

    void removed(String section, int rowCount) {
        removedSections.add(section + " (" + rowCount + ")");
    }

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

    public boolean changedDatabase() {
        return !appliedSections.isEmpty() || !removedSections.isEmpty();
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

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

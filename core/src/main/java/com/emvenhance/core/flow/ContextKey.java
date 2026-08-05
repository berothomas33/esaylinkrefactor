package com.emvenhance.core.flow;

import androidx.annotation.NonNull;

/**
 * Typed key for scratch state passed between steps via {@link EmvContext#put}/{@link
 * EmvContext#get}.
 *
 * <p>Using keys rather than fields on the context keeps {@link EmvContext} stable as new
 * steps introduce new intermediate data — a behavior declares its own key and no shared type
 * has to change.
 *
 * @param <T> type of the value stored under this key
 */
public final class ContextKey<T> {

    private final String name;

    private ContextKey(String name) {
        this.name = name;
    }

    @NonNull
    public static <T> ContextKey<T> of(@NonNull String name) {
        return new ContextKey<>(name);
    }

    @NonNull
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public String toString() {
        return "ContextKey(" + name + ")";
    }
}

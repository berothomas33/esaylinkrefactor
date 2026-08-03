package com.emvenhance.ui;

import androidx.annotation.Nullable;

/**
 * Wraps a payload that must be consumed at most once.
 *
 * <p>LiveData is sticky — it redelivers its last value to every new observer (including
 * the Activity instance a rotation just created). For the EMV-step progress events that
 * is wrong: they should fire once and disappear. {@link #consume()} returns the content
 * the first time, then null forever after.
 */
public final class Event<T> {

    private final T content;
    private boolean handled = false;

    public Event(T content) {
        this.content = content;
    }

    @Nullable
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }
}

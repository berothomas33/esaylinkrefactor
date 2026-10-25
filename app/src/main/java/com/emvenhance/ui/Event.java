package com.emvenhance.ui;

import androidx.annotation.Nullable;

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

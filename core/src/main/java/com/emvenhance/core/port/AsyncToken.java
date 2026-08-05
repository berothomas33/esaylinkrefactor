package com.emvenhance.core.port;

import java.util.UUID;

/** Correlates a {@link com.emvenhance.core.behavior.BehaviorResult.WaitAsync} with its resume signal. */
public final class AsyncToken {

    private final String id;

    public AsyncToken() {
        this(UUID.randomUUID().toString());
    }

    public AsyncToken(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AsyncToken)) {
            return false;
        }
        return id.equals(((AsyncToken) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AsyncToken(" + id + ")";
    }
}

package com.emvenhance.core.flow;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative cancellation for one transaction. Fed by
 * {@link com.emvenhance.core.terminal.PosTerminal#cancelTransaction()}.
 *
 * <p>The orchestrator checks this between steps; long-running behaviors should check it too
 * and return early. Anything already in flight is torn down by disposing the Rx chain.
 */
public final class CancellationSignal {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void reset() {
        cancelled.set(false);
    }
}

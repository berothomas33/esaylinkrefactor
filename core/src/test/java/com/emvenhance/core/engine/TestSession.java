package com.emvenhance.core.engine;

import com.emvenhance.core.terminal.EmvBehavior;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stands in for {@code PosTerminal} when a test drives an {@link EmvEngine} on its own: the
 * running and cancel state the engine reads, and the end it hands back. Like the terminal, the
 * first ERROR/COMPLETED ends the transaction, and a cancel ends it too.
 */
public final class TestSession {

    public final AtomicBoolean running = new AtomicBoolean();
    public final AtomicBoolean cancelled = new AtomicBoolean();

    public EmvEngine engine(EmvBehavior behavior, EmvEngine.Authorizer authorizer) {
        return new EmvEngine(behavior, authorizer, end -> running.set(false), running::get,
                cancelled::get);
    }

    /** As {@code PosTerminal.Transaction.cancel}: cancelled, and so ended — no step runs after it. */
    public void cancel() {
        cancelled.set(true);
        running.set(false);
    }

    /** A new transaction on {@code engine}, as {@code PosTerminal} begins one. */
    public void begin(EmvEngine engine) {
        running.set(true);
        cancelled.set(false);
        engine.begin();
    }
}

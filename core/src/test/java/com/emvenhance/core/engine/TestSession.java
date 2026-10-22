package com.emvenhance.core.engine;

import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.terminal.EmvBehavior;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stands in for {@code PosTerminal} when a test drives an {@link EmvEngine} on its own: the
 * running and cancel state the engine reads, and the end it hands back. Like the terminal, the
 * first ERROR/COMPLETED ends the transaction, and a cancel ends it too; unlike it, the end is
 * published at once (there is no transaction thread to free first).
 */
public final class TestSession {

    public final AtomicBoolean running = new AtomicBoolean();
    public final AtomicBoolean cancelled = new AtomicBoolean();

    private EmvEngine engine;

    public EmvEngine engine(EmvBehavior behavior, EmvEngine.Authorizer authorizer) {
        engine = new EmvEngine(behavior, authorizer, this::ended, running::get, cancelled::get);
        return engine;
    }

    /** The first ERROR/COMPLETED ends the transaction and is published at once (no thread to free). */
    private void ended(TransactionStepEvent end) {
        if (running.getAndSet(false)) {
            engine.publishEnd(end);
        }
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

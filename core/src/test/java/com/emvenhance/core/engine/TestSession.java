package com.emvenhance.core.engine;

import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.terminal.EmvBehavior;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stands in for {@code PosTerminal} when a test drives an {@link EmvEngine} on its own: the
 * running and cancel flags, and the transaction steps the engine hands over. Like the terminal,
 * nothing is kept outside a transaction and the first ERROR/COMPLETED ends it; unlike it, the end
 * is recorded at once (there is no transaction thread to free first).
 */
public final class TestSession {

    public final AtomicBoolean running = new AtomicBoolean();
    public final AtomicBoolean cancelled = new AtomicBoolean();
    public final List<TransactionStepEvent> steps = new CopyOnWriteArrayList<>();

    public EmvEngine engine(EmvBehavior behavior, EmvEngine.Authorizer authorizer) {
        return new EmvEngine(behavior, authorizer, this::publish, running::get, cancelled::get);
    }

    /** A new transaction on {@code engine}, as {@code PosTerminal.process} begins one. */
    public void begin(EmvEngine engine) {
        running.set(true);
        cancelled.set(false);
        engine.begin();
    }

    private void publish(TransactionStepEvent event) {
        if (!running.get()) {
            return;
        }
        if (event.getStep().isEnd()) {
            running.set(false);
        }
        steps.add(event);
    }
}

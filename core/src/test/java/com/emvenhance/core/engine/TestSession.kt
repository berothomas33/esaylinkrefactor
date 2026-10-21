package com.emvenhance.core.engine

import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.terminal.EmvBehavior
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stands in for `PosTerminal` when a test drives an [EmvEngine] on its own: the running and
 * cancel flags, and the transaction steps the engine hands over. Like the terminal, nothing is
 * kept outside a transaction and the first ERROR/COMPLETED ends it; unlike it, the end is recorded
 * at once (there is no transaction thread to free first).
 */
class TestSession {

    val running = AtomicBoolean()
    val cancelled = AtomicBoolean()
    val steps: MutableList<TransactionStepEvent> = CopyOnWriteArrayList()

    fun engine(behavior: EmvBehavior, authorizer: EmvEngine.Authorizer): EmvEngine =
        EmvEngine(behavior, authorizer, ::publish, running::get, cancelled::get)

    /** As `PosTerminal.Transaction.cancel`: cancelled, and so ended — no step runs after it. */
    fun cancel() {
        cancelled.set(true)
        running.set(false)
    }

    /** A new transaction on `engine`, as `PosTerminal.process` begins one. */
    fun begin(engine: EmvEngine) {
        running.set(true)
        cancelled.set(false)
        engine.begin()
    }

    private fun publish(event: TransactionStepEvent) {
        if (!running.get()) {
            return
        }
        if (event.step.isEnd) {
            running.set(false)
        }
        steps.add(event)
    }
}

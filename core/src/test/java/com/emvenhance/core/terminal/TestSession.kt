package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Stands in for `PosTerminal.process` when a test drives a behavior on its own, on the test's
 * thread: a real [PosTerminal.Transaction] of a terminal that never searches, handed to the
 * behavior as `process` hands it. Unlike `process`, the end is listed at once in [steps] (there is
 * no transaction thread to free first).
 */
internal class TestSession(
    private val behavior: EmvBehavior,
    authorizer: EmvEngine.Authorizer,
) {

    private val terminal = Terminal(behavior, authorizer)

    val engine: EmvEngine = terminal.engine()

    private var recorder: FlowRecorder<TransactionStepEvent>? = null

    lateinit var transaction: PosTerminal.Transaction
        private set

    /** A new transaction, as `PosTerminal.process` begins one. */
    fun begin(config: TransactionConfig): PosTerminal.Transaction {
        recorder?.cancel()
        transaction = terminal.Transaction(config)
        recorder = transaction.steps().recordIn(CoroutineScope(Dispatchers.Unconfined))
        engine.begin()
        return transaction
    }

    fun prepare(config: TransactionConfig): Boolean = behavior.prepare(config, transaction)

    fun start(config: TransactionConfig, card: CardPresence) {
        behavior.start(config, card, transaction)
    }

    /** As the screen's Cancel: [PosTerminal.Transaction.cancel]. */
    fun cancel() {
        transaction.cancel()
    }

    /** Every step the transaction reported, its end included once it has ended. */
    fun steps(): List<TransactionStepEvent> {
        val steps = ArrayList(recorder?.values.orEmpty())
        transaction.end?.let { steps.add(it) }
        return steps
    }

    /** Never searches; goes online through the test's authorizer. */
    private class Terminal(behavior: EmvBehavior, private val authorizer: EmvEngine.Authorizer) :
        PosTerminal(behavior, stub(), stub()) {

        fun engine(): EmvEngine = behavior.engine()

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? = null

        override fun cancelCardSearch() {}

        /** The behavior's way online goes to the test's host. */
        override fun authorize(config: TransactionConfig): AuthResult = authorizer.authorize(config)
    }
}

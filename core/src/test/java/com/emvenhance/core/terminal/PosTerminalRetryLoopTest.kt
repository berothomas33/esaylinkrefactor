package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.host.CommunicationBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * A retry the behavior asks for (fallback, try another interface, try again, See Phone) is the
 * next turn of `Transaction.run`'s loop: a new search after the previous one returned, never one
 * started from inside its callback, and TRANSACTION_STARTED only once.
 */
class PosTerminalRetryLoopTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val searchedWith = CopyOnWriteArrayList<EntryMethod>()
    private val searchesOnStack = AtomicInteger()
    private val deepestNesting = AtomicInteger()

    /** What the behavior's start() does: ask for a retry, maybe cancel first. */
    private val onStart = AtomicReference<(EmvEngine, PosTerminal.Transaction) -> Unit>()
    private val sale = AtomicReference<PosTerminal.Transaction>()

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun aRetryIsTheNextTurnOfTheLoopNotASearchInsideTheLastOne() {
        val starts = AtomicInteger()
        onStart.set { engine, transaction ->
            if (starts.incrementAndGet() == 1) {
                engine.requestRetry(
                    TransactionConfig(TransactionType.SALE, 1000, EntryMethod.MAGSTRIPE),
                    "Chip not readable — swipe the card",
                )
            } else {
                transaction.publish(TransactionStepEvent.of(TransactionStep.APPROVED))
                transaction.publish(TransactionStepEvent.of(TransactionStep.COMPLETED))
            }
        }
        val terminal = TestTerminal()
        val events = terminal.allSteps.flow.recordIn(scope)

        sale.set(terminal.acceptCard(TransactionType.SALE, 1000))
        assertTrue(events.await { it.step.isEnd })

        assertEquals(listOf(EntryMethod.ANY, EntryMethod.MAGSTRIPE), searchedWith)
        assertEquals("a search started inside another", 1, deepestNesting.get())
        assertEquals("once per transaction", 1, events.values.count { it.step == TransactionStep.TRANSACTION_STARTED })
        assertEquals(TransactionStep.COMPLETED, events.values.last().step)
    }

    @Test
    fun aRetryAskedForByACancelledTransactionNeverSearches() {
        val saleKnown = CountDownLatch(1)
        onStart.set { engine, _ ->
            saleKnown.await(5, TimeUnit.SECONDS)
            sale.get().cancel() // the screen cancels while EMV is running
            engine.requestRetry(TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP), "Insert the card")
        }
        val terminal = TestTerminal()
        val events = terminal.allSteps.flow.recordIn(scope)

        sale.set(terminal.acceptCard(TransactionType.SALE, 1000))
        saleKnown.countDown()
        assertTrue(events.await { it.step.isEnd })

        assertEquals("the retry never searched", listOf(EntryMethod.ANY), searchedWith)
        assertEquals(TransactionStep.CANCELLED, events.values.last().step)
        assertFalse(terminal.isSearchCancelled())
    }

    /** A search that finds a chip at once (or swipes, on a magstripe retry), on the transaction thread. */
    private inner class TestTerminal : PosTerminal(behavior(), CommunicationBehavior { AuthResult.failed("no host") }, stub()) {

        /** Every transaction's steps, for the test: each transaction has its own flow. */
        val allSteps = AllSteps()

        override fun startTransaction(config: TransactionConfig): Transaction =
            allSteps.relay(super.startTransaction(config))

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
            deepestNesting.accumulateAndGet(searchesOnStack.incrementAndGet(), Math::max)
            try {
                searchedWith.add(config.mode)
                val card = CardPresence.chip()
                listener.onChipDetected(card) // EMV runs right here, as on PAX
                return card
            } finally {
                searchesOnStack.decrementAndGet()
            }
        }

        override fun cancelCardSearch() {}
    }

    /** prepare() succeeds; start() does what the test set in [onStart]. */
    private fun behavior(): AbstractEmvBehavior = object : NoOpStepsBehavior() {
        override fun prepare(config: TransactionConfig, transaction: PosTerminal.Transaction) = true
        override fun start(config: TransactionConfig, card: CardPresence, transaction: PosTerminal.Transaction) {
            onStart.get()(engine(), transaction)
        }
    }
}

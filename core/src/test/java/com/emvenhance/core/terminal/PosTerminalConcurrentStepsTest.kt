package com.emvenhance.core.terminal

import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Every emit holds the terminal's one lock: a step reported from a second thread (a vendor SDK
 * callback, a cancel) while one is being delivered waits for it — a collector never gets two steps
 * at once.
 */
class PosTerminalConcurrentStepsTest {

    @Test
    fun aStepFromASecondThreadWaitsForTheStepBeingDelivered() = runTest {
        val terminal = TestTerminal()
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val firstInside = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String>()
        val sale = terminal.acceptCard(TransactionType.SALE, 1000)
        // Collects on the publishing thread, like an Rx subscriber with no observeOn.
        backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            sale.steps().collect { event ->
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
                delivered.add(event.step.toString() + " on " + Thread.currentThread().name)
                if (event.step == TransactionStep.ONLINE_PROCESSING) {
                    firstInside.countDown()
                    releaseFirst.await(5, TimeUnit.SECONDS) // still delivering when the second lands
                }
                inFlight.decrementAndGet()
            }
        }
        assertTrue(terminal.searching.await(5, TimeUnit.SECONDS))

        val first = Thread(
            { sale.publish(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING)) },
            "first",
        )
        first.start()
        assertTrue(firstInside.await(5, TimeUnit.SECONDS))
        val second = Thread(
            { sale.publish(TransactionStepEvent.of(TransactionStep.ONLINE_COMPLETED)) },
            "second",
        )
        second.start()
        second.join(300)
        assertTrue("the second waits for the first's delivery", second.isAlive)

        releaseFirst.countDown()
        second.join(5_000)
        first.join(5_000)
        terminal.release.countDown()

        assertEquals("two steps at once", 1, maxInFlight.get())
        assertEquals(listOf("ONLINE_PROCESSING on first", "ONLINE_COMPLETED on second"), delivered.toList().subList(1, 3)) // after TRANSACTION_STARTED; a snapshot: the end may still be landing
    }

    @Test
    fun aCollectorThatThrowsStopsNeitherTheFlowNorTheTransaction() = runTest {
        val terminal = TestTerminal()
        val seen = CopyOnWriteArrayList<TransactionStep>()
        val ended = CountDownLatch(1)
        val failures = CopyOnWriteArrayList<Throwable>()
        // A screen's bug: its collector throws on the publishing thread. It fails its own
        // coroutine only (caught here), never the emitter or the transaction thread.
        val screens = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, t -> failures.add(t) })
        val sale = terminal.acceptCard(TransactionType.SALE, 1000)
        screens.launch(start = CoroutineStart.UNDISPATCHED) {
            sale.steps().collect { throw IllegalStateException("a screen's bug") }
        }
        screens.launch(start = CoroutineStart.UNDISPATCHED) {
            sale.steps().collect {
                seen.add(it.step)
                if (it.step.isEnd) ended.countDown()
            }
        }
        assertTrue(terminal.searching.await(5, TimeUnit.SECONDS))

        sale.publish(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED))
        sale.publish(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING))
        terminal.release.countDown()

        assertTrue("the transaction never ended", ended.await(5, TimeUnit.SECONDS))
        assertEquals(
            listOf(
                TransactionStep.TRANSACTION_STARTED, TransactionStep.ONLINE_REQUIRED,
                TransactionStep.ONLINE_PROCESSING, TransactionStep.ERROR,
            ),
            seen,
        )
        assertEquals("the throwing collector failed alone", 1, failures.size)
        screens.cancel()
    }

    /** prepare() succeeds; the search is held open until released. */
    private class TestTerminal : PosTerminal(stubBehavior(prepared = true), stub(), stub()) {
        val searching = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun engine(): EmvEngine = behavior.engine()

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
            searching.countDown()
            release.await(5, TimeUnit.SECONDS)
            return null
        }

        override fun cancelCardSearch() {}
    }
}

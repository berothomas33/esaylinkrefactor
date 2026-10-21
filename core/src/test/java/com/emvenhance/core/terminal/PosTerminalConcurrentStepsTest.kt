package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
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
 * A step reported from a second thread (a vendor SDK callback) while one is being delivered waits
 * for it, and the collector already delivering delivers it next — a collector never gets two steps
 * at once.
 */
class PosTerminalConcurrentStepsTest {

    @Test
    fun aStepFromASecondThreadWaitsForTheStepBeingDelivered() = runTest {
        val terminal = TestTerminal()
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val firstInside = CountDownLatch(1)
        val secondReported = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String>()
        // Collects on the publishing thread, like an Rx subscriber with no observeOn.
        backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            terminal.transactionSteps().collect { event ->
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
                delivered.add(event.step.toString() + " on " + Thread.currentThread().name)
                if (event.step == TransactionStep.ONLINE_PROCESSING) {
                    firstInside.countDown()
                    secondReported.await(1, TimeUnit.SECONDS) // still delivering when it lands
                }
                inFlight.decrementAndGet()
            }
        }
        terminal.acceptCard(TransactionType.SALE, 1000)
        assertTrue(terminal.searching.await(5, TimeUnit.SECONDS))

        val first = Thread(
            { terminal.engine().notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING)) },
            "first",
        )
        first.start()
        assertTrue(firstInside.await(5, TimeUnit.SECONDS))
        terminal.engine().notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_COMPLETED))
        secondReported.countDown()
        first.join(5_000)
        terminal.release.countDown()

        assertEquals("two steps at once", 1, maxInFlight.get())
        assertEquals(listOf("ONLINE_PROCESSING on first", "ONLINE_COMPLETED on first"), delivered.subList(0, 2))
    }

    /** prepare() succeeds; the search is held open until released. */
    private class TestTerminal : PosTerminal(stubBehavior(prepared = true), stub(), stub()) {
        val searching = CountDownLatch(1)
        val release = CountDownLatch(1)

        fun engine() = engine

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
            searching.countDown()
            release.await(5, TimeUnit.SECONDS)
            return null
        }

        override fun cancelCardSearch() {}
    }
}

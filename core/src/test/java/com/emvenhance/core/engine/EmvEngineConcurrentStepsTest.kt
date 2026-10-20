package com.emvenhance.core.engine

import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.stubBehavior
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Transaction steps reported from two threads at once still reach a collector one at a time. */
class EmvEngineConcurrentStepsTest {

    @Test
    fun aCancelFromTheUiThreadWaitsForTheStepBeingDelivered() = runTest {
        val engine = EmvEngine(stubBehavior(), { AuthResult.failed("no host") }) { false }
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val firstInside = CountDownLatch(1)
        val cancelReported = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String>()
        engine.begin()
        // Collects on the publishing thread, like an Rx subscriber with no observeOn.
        backgroundScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            engine.transactionSteps.collect { event ->
                maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
                delivered.add(event.step.toString() + " on " + Thread.currentThread().name)
                if (event.step == TransactionStep.ONLINE_PROCESSING) {
                    firstInside.countDown()
                    cancelReported.await(1, TimeUnit.SECONDS) // still delivering when cancel lands
                }
                inFlight.decrementAndGet()
            }
        }

        val transaction = Thread(
            { engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING)) },
            "transaction",
        )
        transaction.start()
        assertTrue(firstInside.await(5, TimeUnit.SECONDS))
        engine.notifyError("Transaction cancelled") // what cancel() does, from this thread
        cancelReported.countDown()
        transaction.join(5_000)

        assertEquals("two deliveries at once", 1, maxInFlight.get())
        assertEquals(listOf("ONLINE_PROCESSING on transaction", "ERROR on transaction"), delivered)
        assertFalse("the cancel ended it", engine.isRunning())
    }
}

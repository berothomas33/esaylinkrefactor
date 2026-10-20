package com.emvenhance.core.engine

import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.stubBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Transaction steps reported from two threads at once still reach listeners one at a time. */
class EmvEngineConcurrentStepsTest {

    @Test
    fun aCancelFromTheUiThreadWaitsForTheStepBeingDelivered() {
        val engine = EmvEngine(stubBehavior()) { AuthResult.failed("no host") }
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val firstInside = CountDownLatch(1)
        val cancelReported = CountDownLatch(1)
        val delivered = CopyOnWriteArrayList<String>()
        engine.addTransactionStepListener { event ->
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
            delivered.add(event.step.toString() + " on " + Thread.currentThread().name)
            if (event.step == TransactionStep.ONLINE_PROCESSING) {
                firstInside.countDown()
                cancelReported.await(1, TimeUnit.SECONDS) // still delivering when cancel lands
            }
            inFlight.decrementAndGet()
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
        assertEquals(TransactionStep.ERROR, engine.currentTransactionState().step)
    }

    @Test
    fun aStepPublishedFromInsideADeliveryComesRightAfterIt() {
        val engine = EmvEngine(stubBehavior()) { AuthResult.failed("no host") }
        val delivered = ArrayList<TransactionStep>()
        engine.addTransactionStepListener { event ->
            delivered.add(event.step)
            if (event.step == TransactionStep.APPROVED) {
                engine.notifyCompleted()
                delivered.add(TransactionStep.IDLE) // marks "notifyCompleted returned"
            }
        }

        engine.notifyApproved("00")

        assertEquals(
            listOf(TransactionStep.APPROVED, TransactionStep.IDLE, TransactionStep.COMPLETED),
            delivered,
        )
    }
}

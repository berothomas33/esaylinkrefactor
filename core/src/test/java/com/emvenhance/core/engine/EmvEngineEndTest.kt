package com.emvenhance.core.engine

import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.recordIn
import com.emvenhance.core.terminal.stubBehavior
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The engine keeps no transaction state: its transaction steps go to the terminal as they are,
 * and whether a transaction runs or was cancelled is the terminal's — read through the hooks.
 */
class EmvEngineEndTest {

    private val running = AtomicBoolean(true)
    private val cancelled = AtomicBoolean()
    private val handedOver = CopyOnWriteArrayList<TransactionStepEvent>()
    private val engine = EmvEngine(
        stubBehavior(), { AuthResult.failed("no host") }, { handedOver.add(it) }, running::get, cancelled::get,
    )

    @Test
    fun transactionStepsGoToTheTerminalAsTheyAre() {
        engine.notifyError("Card search cancelled")
        engine.notifyCompleted()

        assertEquals(listOf(TransactionStep.ERROR, TransactionStep.COMPLETED), handedOver.map { it.step })
        assertEquals("Card search cancelled", handedOver[0].message)
    }

    @Test
    fun noEmvStepOnceTheTransactionHasEnded() = runTest {
        val published = engine.emvSteps.recordIn(backgroundScope)
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe")
        running.set(false)
        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION, "late")

        assertEquals(listOf(EmvStep.SEARCH_CARD), published.values.map { it.step })
        assertFalse(engine.isRunning())
    }

    @Test
    fun theCancelFlagIsTheTerminals() {
        assertFalse(engine.isCancelled())
        cancelled.set(true)
        assertTrue(engine.isCancelled())
    }
}

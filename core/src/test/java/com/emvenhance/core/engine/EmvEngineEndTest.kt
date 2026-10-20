package com.emvenhance.core.engine

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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A transaction ends exactly once: nothing is published outside begin()…the first
 * ERROR/COMPLETED, and the cancel flag the behavior reads is the terminal's.
 */
class EmvEngineEndTest {

    private val terminalCancelled = AtomicBoolean()
    private val engine = EmvEngine(stubBehavior(), { AuthResult.failed("no host") }) { terminalCancelled.get() }

    @Test
    fun aCancelledTransactionEndsOnceWhateverItsCodeReportsAfter() = runTest {
        val steps = engine.transactionSteps.recordIn(backgroundScope)
        engine.begin()
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING))

        engine.cancel()
        engine.notifyError("Transaction cancelled") // a stage noticing the cancel
        engine.notifyApproved("00") // the host answer still arriving
        engine.notifyCompleted()

        assertEquals(listOf(TransactionStep.ONLINE_PROCESSING, TransactionStep.ERROR), steps.values.map { it.step })
        assertFalse(engine.isRunning())
    }

    @Test
    fun nothingIsPublishedOutsideATransaction() = runTest {
        val steps = engine.transactionSteps.recordIn(backgroundScope)
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED))
        engine.cancel()

        assertTrue(steps.values.isEmpty())

        engine.begin()
        engine.notifyCompleted()
        assertEquals(listOf(TransactionStep.COMPLETED), steps.values.map { it.step })
    }

    @Test
    fun theCancelFlagIsTheTerminals() {
        assertFalse(engine.isCancelled())
        terminalCancelled.set(true)
        assertTrue(engine.isCancelled())
    }
}

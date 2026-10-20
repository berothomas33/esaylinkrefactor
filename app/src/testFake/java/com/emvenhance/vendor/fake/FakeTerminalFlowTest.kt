package com.emvenhance.vendor.fake

import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.terminal.TerminalBusyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The converted core contract driven end to end by a second vendor: a fake chip sale publishes
 * its steps on the engine's one stream, from TRANSACTION_STARTED to COMPLETED, and returns once
 * the terminal is free; a second sale while it runs is refused as busy.
 */
class FakeTerminalFlowTest {

    @Test
    fun aChipSaleRunsEveryStepAndReturnsOnceFree() = runBlocking {
        val terminal = FakeTerminal()
        val steps = CopyOnWriteArrayList<TransactionStep>()
        val emvSteps = CopyOnWriteArrayList<EmvStep>()
        val handles = listOf(
            record(terminal.transactionSteps()) { steps.add(it.step) },
            record(terminal.emvSteps()) { emvSteps.add(it.step) },
        )

        withTimeout(10_000) {
            terminal.acceptCard(TransactionType.SALE, 1000)
        }
        handles.forEach { it.close() }

        assertEquals(TransactionStep.TRANSACTION_STARTED, steps.first())
        assertEquals(TransactionStep.COMPLETED, steps.last())
        val online = listOf(
            TransactionStep.ONLINE_REQUIRED, TransactionStep.ONLINE_PROCESSING, TransactionStep.ONLINE_COMPLETED,
        )
        assertTrue("online steps in order: $steps", steps.windowed(3).contains(online))
        assertEquals(TransactionStep.APPROVED, steps[steps.size - 2])
        assertEquals(EmvStep.TERMINAL_INITIALIZATION, emvSteps.first())
        assertEquals(EmvStep.TRANSACTION_COMPLETION, emvSteps.last())
    }

    @Test
    fun aSecondSaleWhileTheFirstRunsIsRefusedAsBusy() = runBlocking {
        val terminal = FakeTerminal()
        val steps = CopyOnWriteArrayList<TransactionStep>()
        var refused: Throwable? = null
        // Started from inside a step, so the first sale is certainly still running.
        val handle = record(terminal.transactionSteps()) { event ->
            steps.add(event.step)
            if (event.step == TransactionStep.TRANSACTION_STARTED) {
                runBlocking {
                    try {
                        terminal.acceptCard(TransactionType.SALE, 2000)
                        fail("the second sale started")
                    } catch (e: TerminalBusyException) {
                        refused = e // expected: refused at once, nothing queued
                    }
                }
            }
        }

        withTimeout(10_000) {
            terminal.acceptCard(TransactionType.SALE, 1000)
        }
        handle.close()

        assertTrue("the second sale was not refused", refused is TerminalBusyException)
        assertEquals(TransactionStep.COMPLETED, steps.last())
    }

    /** Collects `flow` on the publishing thread, from now on. */
    private fun <T> record(flow: Flow<T>, into: (T) -> Unit): AutoCloseable {
        val job = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { into(it) }
        }
        return AutoCloseable { job.cancel() }
    }
}

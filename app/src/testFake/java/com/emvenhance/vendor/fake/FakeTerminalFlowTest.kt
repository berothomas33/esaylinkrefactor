package com.emvenhance.vendor.fake

import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.terminal.TerminalBusyException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The converted core contract driven end to end by a second vendor: a fake chip sale is one flow
 * of its own steps, from TRANSACTION_STARTED to COMPLETED, that completes once the terminal is
 * free; a second sale while it runs is refused as busy.
 */
class FakeTerminalFlowTest {

    @Test
    fun aChipSaleRunsEveryStepAndCompletesTheFlow() = runBlocking {
        val terminal = FakeTerminal()
        val emvSteps = CopyOnWriteArrayList<EmvStep>()
        val handle = terminalEngineSteps(terminal, emvSteps)

        val steps = withTimeout(10_000) {
            terminal.acceptCard(TransactionType.SALE, 1000).map { it.step }.toList()
        }
        handle.close()

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
        val first = terminal.acceptCard(TransactionType.SALE, 1000)
        val firstSteps = CopyOnWriteArrayList<TransactionStep>()

        withTimeout(10_000) {
            first.collect { event ->
                firstSteps.add(event.step)
                if (event.step == TransactionStep.TRANSACTION_STARTED) {
                    try {
                        terminal.acceptCard(TransactionType.SALE, 2000).collect { }
                        fail("the second sale started")
                    } catch (e: TerminalBusyException) {
                        // expected: refused at once, nothing queued
                    }
                }
            }
        }

        assertEquals(TransactionStep.COMPLETED, firstSteps.last())
    }

    /** Records every EMV step the engine publishes, through the flow the UI collects. */
    private fun terminalEngineSteps(terminal: FakeTerminal, into: MutableList<EmvStep>): AutoCloseable {
        val job = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            terminal.emvSteps().collect { into.add(it.step) }
        }
        return AutoCloseable { job.cancel() }
    }
}

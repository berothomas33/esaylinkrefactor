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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The converted core contract driven end to end by a second vendor: a fake chip sale publishes
 * its own steps, from TRANSACTION_STARTED to COMPLETED, the end once the terminal is free; a
 * second sale while it runs is refused as busy.
 */
class FakeTerminalFlowTest {

    @Test
    fun aChipSaleRunsEveryStepAndEndsOnceFree() {
        val terminal = FakeTerminal()
        val steps = CopyOnWriteArrayList<TransactionStep>()
        val emvSteps = CopyOnWriteArrayList<EmvStep>()
        val ended = CountDownLatch(1)
        val emv = record(terminal.emvSteps()) { emvSteps.add(it.step) }

        val sale = terminal.acceptCard(TransactionType.SALE, 1000)
        val handles = listOf(
            emv,
            record(sale.steps()) {
                steps.add(it.step)
                if (it.step.isEnd) ended.countDown()
            },
        )
        assertTrue("the sale never ended", ended.await(10, TimeUnit.SECONDS))
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
    fun aSecondSaleWhileTheFirstRunsIsRefusedAsBusy() {
        val terminal = FakeTerminal()
        val steps = CopyOnWriteArrayList<TransactionStep>()
        val ended = CountDownLatch(1)
        var refused: Throwable? = null
        // Started from inside an EMV step, on the transaction thread: the first sale is certainly
        // still running.
        val emv = record(terminal.emvSteps()) { event ->
            if (event.step == EmvStep.TERMINAL_INITIALIZATION) {
                try {
                    terminal.acceptCard(TransactionType.SALE, 2000)
                    fail("the second sale started")
                } catch (e: TerminalBusyException) {
                    refused = e // expected: refused at once, nothing queued
                }
            }
        }

        val sale = terminal.acceptCard(TransactionType.SALE, 1000)
        val handle = record(sale.steps()) { event ->
            steps.add(event.step)
            if (event.step.isEnd) ended.countDown()
        }
        assertTrue("the sale never ended", ended.await(10, TimeUnit.SECONDS))
        handle.close()
        emv.close()

        assertTrue("the second sale was not refused", refused is TerminalBusyException)
        assertEquals(TransactionStep.COMPLETED, steps.last())
    }

    /** Collects `flow` on the publishing thread (a transaction's steps: from its first). */
    private fun <T> record(flow: Flow<T>, into: (T) -> Unit): AutoCloseable {
        val job = CoroutineScope(Dispatchers.Unconfined).launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { into(it) }
        }
        return AutoCloseable { job.cancel() }
    }
}

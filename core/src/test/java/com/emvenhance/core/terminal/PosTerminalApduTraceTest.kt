package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** PosTerminal is the UI's single surface for the trace, and owns its on/off switch. */
class PosTerminalApduTraceTest {

    @Test
    fun exposesTheEnginesTrace() {
        val terminal = TestTerminal()

        assertSame(terminal.engine().apduTrace(), terminal.apduTrace())
    }

    @Test
    fun loggingSwitchControlsTheEnginesTrace() {
        val terminal = TestTerminal()
        val trace = terminal.apduTrace()
        assumeTrue("trace records in debug builds only", trace.isEnabled())
        assertTrue(terminal.isApduLoggingEnabled())

        terminal.setApduLoggingEnabled(false)
        terminal.engine().begin()
        trace.note("ICC", "not recorded")

        assertFalse(terminal.isApduLoggingEnabled())
        assertFalse(trace.isEnabled())
        assertEquals("", trace.text())

        terminal.setApduLoggingEnabled(true)
        trace.note("ICC", "recorded")
        assertTrue(trace.text().contains("recorded"))
    }

    @Test
    fun aTransactionsStepsAndItsEndReachTheTrace() = runTest {
        val terminal = TestTerminal() // its prepare() fails: the sale ends at once
        val trace = terminal.apduTrace()
        assumeTrue("trace records in debug builds only", trace.isEnabled())
        val steps = terminal.transactionSteps().recordIn(backgroundScope)

        terminal.acceptCard(TransactionType.SALE, 1000)

        assertTrue(steps.await { it.step.isEnd })
        assertTrue(trace.text(), trace.text().contains("--- ERROR: Terminal initialization failed"))
    }

    private class TestTerminal : PosTerminal(stubBehavior(), stub(), stub()) {
        fun engine() = engine

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? = null

        override fun cancelCardSearch() {}
    }
}

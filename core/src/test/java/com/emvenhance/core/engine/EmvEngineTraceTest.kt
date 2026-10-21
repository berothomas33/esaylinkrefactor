package com.emvenhance.core.engine

import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.terminal.stubBehavior
import com.emvenhance.core.util.ApduTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** The engine owns the APDU trace: one per engine, started by begin(), fed by its own steps. */
class EmvEngineTraceTest {

    private val session = TestSession()
    private lateinit var engine: EmvEngine
    private lateinit var trace: ApduTrace

    @Before
    fun setUp() {
        engine = newEngine(session)
        trace = engine.apduTrace()
    }

    @Test
    fun ownsOneTracePerEngine() {
        assertNotNull(trace)
        assertSame(trace, engine.apduTrace())
        assertNotSame(trace, newEngine(TestSession()).apduTrace())
    }

    @Test
    fun beginStartsAFreshTrace() {
        assumeTrue("trace records in debug builds only", trace.isEnabled())
        session.begin(engine)
        trace.note("ICC", "first transaction")
        engine.notifyError("done") // ends the transaction

        session.begin(engine)

        assertFalse(trace.text().contains("first transaction"))
        assertTrue(trace.text().startsWith("===== EMV TRANSACTION ====="))
    }

    @Test
    fun recordsKernelSteps() {
        assumeTrue("trace records in debug builds only", trace.isEnabled())
        session.begin(engine)

        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe")

        assertTrue(trace.text().contains("### KERNEL STEP 1. Search card (Insert, tap, or swipe)"))
    }

    @Test
    fun aStepAfterTheEndIsNotedAsDroppedNotAsAKernelStep() {
        assumeTrue("trace records in debug builds only", trace.isEnabled())
        session.begin(engine)
        engine.notifyCompleted()

        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION, "late")

        val text = trace.text()
        assertFalse(text.contains("### KERNEL STEP 16."))
        assertTrue(
            text.contains("!!! EMV step dropped, the transaction has ended: 16. Transaction completion (late)"),
        )
    }

    @Test
    fun retryKeepsTheFailedAttemptAndMarksIt() {
        assumeTrue("trace records in debug builds only", trace.isEnabled())
        session.begin(engine)
        trace.note("PICC", "card communication error -2")

        engine.requestRetry(config(), "Card not read — tap again")
        assertNotNull(engine.consumePendingRetry())

        val text = trace.text()
        assertTrue(text.contains("card communication error -2"))
        assertTrue(text.contains("--- RETRY #1"))
        assertTrue(text.indexOf("communication error") < text.indexOf("RETRY #1"))
    }

    @Test
    fun countsRetriesAndHandsThePromptOverOnce() {
        session.begin(engine)
        assertEquals(0, engine.getRetryCount())

        engine.requestRetry(config(), "Card not read — tap again")
        engine.consumePendingRetry()

        assertEquals(1, engine.getRetryCount())
        assertEquals("Card not read — tap again", engine.consumeRetryPrompt())
        assertNull(engine.consumeRetryPrompt())
        assertNull("no retry pending", engine.consumePendingRetry())
        assertEquals(1, engine.getRetryCount())
    }

    @Test
    fun beginResetsRetryState() {
        session.begin(engine)
        engine.requestRetry(config(), "Insert the card")
        engine.consumePendingRetry()
        engine.notifyError("done")

        session.begin(engine)

        assertEquals(0, engine.getRetryCount())
        assertNull(engine.consumeRetryPrompt())
    }

    private companion object {
        fun config() = TransactionConfig(TransactionType.SALE, 100, EntryMethod.ANY)

        /** An engine whose behavior does nothing and that never reaches a host. */
        fun newEngine(session: TestSession) = session.engine(stubBehavior()) { AuthResult.failed("no host") }
    }
}

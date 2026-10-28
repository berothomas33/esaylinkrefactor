package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import com.emvenhance.core.host.AuthResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.Test

/**
 * The behavior's step handler consumes every step published on the engine — by
 * [AbstractEmvBehavior.goToStep] or by anyone else — and runs its `onXxx` method, on the
 * publishing thread, before the publish returns. Observing lasts one transaction: once it ends,
 * `goToStep` publishes and runs nothing more.
 */
class EmvStepObserverTest {

    private lateinit var engine: EmvEngine
    private lateinit var vendor: VendorBehavior
    private lateinit var session: TestSession
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        vendor = VendorBehavior()
        session = TestSession(vendor) { AuthResult.failed("no host") }
        engine = session.engine
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun everyStepRunsItsMethodOnce() {
        session.begin(CONFIG)
        session.prepare(CONFIG)
        session.start(CONFIG, CardPresence.chip())

        assertEquals(
            listOf(
                EmvStep.TERMINAL_INITIALIZATION, EmvStep.APPLICATION_SELECTION,
                EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION,
            ),
            vendor.ran,
        )
    }

    @Test
    fun aStepPublishedByAnyoneRunsItsMethod() {
        val seen = record(engine)
        session.begin(CONFIG)
        session.prepare(CONFIG)
        vendor.ran.clear()

        vendor.go(EmvStep.SEARCH_CARD)

        assertEquals(listOf(EmvStep.SEARCH_CARD), vendor.ran)
        assertTrue(seen.contains(EmvStep.SEARCH_CARD))
    }

    @Test
    fun stepsRunOnThePublishingThreadBeforeStartReturns() {
        val caller = Thread.currentThread()
        session.begin(CONFIG)

        session.start(CONFIG, CardPresence.chip())

        assertEquals(3, vendor.ran.size)
        assertEquals(listOf(caller, caller, caller), vendor.threads)
    }

    @Test
    fun aStepPublishedAfterCancelDoesNotRun() {
        session.begin(CONFIG)
        session.prepare(CONFIG)
        vendor.ran.clear()

        session.cancel()
        vendor.go(EmvStep.SEARCH_CARD)

        assertTrue(vendor.ran.isEmpty())
    }

    @Test
    fun disposeStopsTheObserver() {
        session.begin(CONFIG)
        session.start(CONFIG, CardPresence.chip())
        vendor.ran.clear()

        vendor.dispose()
        vendor.go(EmvStep.TRANSACTION_COMPLETION)

        assertTrue(vendor.ran.isEmpty())
    }

    @Test
    fun subscribersSeeNestedStepsInOrder() {
        val seen = record(engine)
        session.begin(CONFIG)

        session.start(CONFIG, CardPresence.chip())

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            seen,
        )
    }

    @Test
    fun subscribersSeeNestedStepsInOrderWhenTheyCameAfterAnEarlierTransaction() {
        session.begin(CONFIG)
        session.start(CONFIG, CardPresence.chip())
        session.transaction.publish(TransactionStepEvent.of(TransactionStep.COMPLETED))
        val seen = record(engine) // e.g. a new MainViewModel

        session.begin(CONFIG)
        session.start(CONFIG, CardPresence.chip())

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            seen,
        )
    }

    @Test
    fun cancelledStepsAreNotRun() {
        session.begin(CONFIG)
        session.start(CONFIG, CardPresence.chip())
        vendor.ran.clear()

        session.cancel()
        vendor.go(EmvStep.TRANSACTION_COMPLETION)

        assertTrue(vendor.ran.isEmpty())
    }

    // ─── The end of a transaction ─────────────────────────────────────────

    @Test
    fun aCompletedTransactionStopsObservingAfterItsLastStepReachedTheUi() {
        vendor.approveOnCompletion = true
        val seen = record(engine)
        session.begin(CONFIG)
        session.start(CONFIG, CardPresence.chip())

        assertEquals(listOf(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps())
        assertEquals(EmvStep.TRANSACTION_COMPLETION, seen[seen.size - 1])

        vendor.ran.clear()
        seen.clear()
        vendor.go(EmvStep.SEARCH_CARD) // after the end

        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty())
        assertTrue("published after the end: $seen", seen.isEmpty())
    }

    @Test
    fun aThrowingStepEndsTheTransactionStopsObservingAndTheNextOneObservesAgain() {
        session.begin(CONFIG)
        vendor.failOn = EmvStep.READ_APPLICATION_DATA

        session.start(CONFIG, CardPresence.chip())

        assertEquals(listOf(TransactionStep.ERROR), steps())
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA), vendor.ran)

        vendor.failOn = null
        vendor.ran.clear()
        vendor.go(EmvStep.SEARCH_CARD) // after the end
        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty())

        session.begin(CONFIG)
        session.prepare(CONFIG)
        vendor.go(EmvStep.SEARCH_CARD)
        assertEquals(listOf(EmvStep.TERMINAL_INITIALIZATION, EmvStep.SEARCH_CARD), vendor.ran)
    }

    @Test
    fun aStepFromCodeStillUnwindingAfterTheEndIsNeitherPublishedNorRun() {
        val seen = record(engine)
        session.begin(CONFIG)
        vendor.errorOn = EmvStep.APPLICATION_SELECTION // finishError, then carries on to READ

        session.start(CONFIG, CardPresence.chip())

        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), vendor.ran)
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), seen)
        assertEquals(listOf(TransactionStep.ERROR), steps())
        assertFalse(session.transaction.isRunning())
    }

    @Test
    fun anUnknownStepEndsTheTransactionAndNothingIsPublishedOrRunAfterIt() {
        val seen = record(engine)
        session.begin(CONFIG)
        vendor.afterApplicationSelection = null // a step with no method to run

        session.start(CONFIG, CardPresence.chip())
        vendor.go(EmvStep.TRANSACTION_COMPLETION) // code still unwinding

        assertEquals(listOf(TransactionStep.ERROR), steps())
        assertEquals("Unknown EMV step: null", session.steps()[0].message)
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), vendor.ran)
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), seen)
        assertFalse(session.transaction.isRunning())
    }

    @Test
    fun aStepAfterTheEndIsNotedInTheTraceAsDroppedNotAsAKernelStep() {
        assumeTrue("trace records in debug builds only", engine.apduTrace().isEnabled())
        session.begin(CONFIG)
        session.prepare(CONFIG)
        session.transaction.publish(TransactionStepEvent.of(TransactionStep.COMPLETED))

        vendor.go(EmvStep.TRANSACTION_COMPLETION, "late")

        val text = engine.apduTrace().text()
        assertFalse(text.contains("### KERNEL STEP 16."))
        assertTrue(text.contains("!!! EMV step dropped, the transaction has ended: 16. Transaction completion (late)"))
    }

    @Test
    fun aRetryInTheSameTransactionKeepsObserving() {
        session.begin(CONFIG)
        session.start(CONFIG, CardPresence.chip())
        vendor.ran.clear()

        engine.requestRetry(CONFIG, "Card not read — tap again") // no ERROR, no COMPLETED
        session.start(engine.consumePendingRetry()!!, CardPresence.chip()) // as PosTerminal does

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            vendor.ran,
        )
        assertTrue(steps().isEmpty())
    }

    private fun steps(): List<TransactionStep> = session.steps().map { it.step }

    /** The EMV steps a collector of the flow sees, in order — like the UI. */
    private fun record(engine: EmvEngine): MutableList<EmvStep> {
        val seen = java.util.concurrent.CopyOnWriteArrayList<EmvStep>()
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            engine.emvSteps.collect { seen.add(it.step) }
        }
        return seen
    }

    /** Chains APPLICATION_SELECTION → READ_APPLICATION_DATA → TRANSACTION_COMPLETION. */
    private class VendorBehavior : NoOpStepsBehavior() {
        val ran = ArrayList<EmvStep>()
        val threads = ArrayList<Thread>()

        /** Throws from this step's method. */
        var failOn: EmvStep? = null

        /** Ends the transaction with an error in this step's method, then carries on. */
        var errorOn: EmvStep? = null
        var afterApplicationSelection: EmvStep? = EmvStep.READ_APPLICATION_DATA
        var approveOnCompletion = false

        fun go(step: EmvStep, detail: String? = null): Boolean = goToStep(step, detail)

        private fun ran(step: EmvStep) {
            ran.add(step)
            threads.add(Thread.currentThread())
            if (step == failOn) {
                throw IllegalStateException("kernel error")
            }
            if (step == errorOn) {
                finishError("kernel error")
            }
        }

        override fun onSearchCard(config: TransactionConfig, card: CardPresence?) {
            ran(EmvStep.SEARCH_CARD)
        }

        override fun onTerminalInitialization(config: TransactionConfig) {
            ran(EmvStep.TERMINAL_INITIALIZATION)
        }

        override fun onApplicationSelection(config: TransactionConfig, card: CardPresence) {
            ran(EmvStep.APPLICATION_SELECTION)
            goToStep(afterApplicationSelection)
        }

        override fun onReadApplicationData(config: TransactionConfig, card: CardPresence) {
            ran(EmvStep.READ_APPLICATION_DATA)
            goToStep(EmvStep.TRANSACTION_COMPLETION)
        }

        override fun onTransactionCompletion(config: TransactionConfig, card: CardPresence) {
            ran(EmvStep.TRANSACTION_COMPLETION)
            if (approveOnCompletion) {
                finishApproved("APPROVED")
            }
        }
    }

    private companion object {
        val CONFIG = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP)
    }
}

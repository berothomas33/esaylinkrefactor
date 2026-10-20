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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The behavior's step handler consumes every step published on the engine — by
 * [AbstractEmvBehavior.goToStep] or by anyone else — and runs its `onXxx` method, on the
 * publishing thread, before the publish returns. Observing lasts one transaction: once it ends,
 * nothing more is published or run.
 */
class EmvStepObserverTest {

    private lateinit var engine: EmvEngine
    private lateinit var vendor: VendorBehavior
    private val transaction = ArrayList<TransactionStepEvent>()

    @Before
    fun setUp() {
        vendor = VendorBehavior()
        engine = EmvEngine(vendor) { AuthResult.failed("no host") }
        engine.addTransactionStepListener { transaction.add(it) }
    }

    @Test
    fun everyStepRunsItsMethodOnce() {
        engine.begin()
        engine.prepare(CONFIG)
        engine.start(CONFIG, CardPresence.chip())

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
        engine.begin()
        engine.prepare(CONFIG)
        vendor.ran.clear()

        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe") // PosTerminal's prompt

        assertEquals(listOf(EmvStep.SEARCH_CARD), vendor.ran)
        assertTrue(seen.contains(EmvStep.SEARCH_CARD))
    }

    @Test
    fun stepsRunOnThePublishingThreadBeforeStartReturns() {
        val caller = Thread.currentThread()
        engine.begin()

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(3, vendor.ran.size)
        assertEquals(listOf(caller, caller, caller), vendor.threads)
    }

    @Test
    fun aStepPublishedAfterCancelDoesNotRun() {
        engine.begin()
        engine.prepare(CONFIG)
        vendor.ran.clear()

        vendor.cancel()
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe")

        assertTrue(vendor.ran.isEmpty())
    }

    @Test
    fun disposeStopsTheObserver() {
        engine.begin()
        engine.start(CONFIG, CardPresence.chip())
        vendor.ran.clear()

        engine.dispose()
        vendor.go(EmvStep.TRANSACTION_COMPLETION)

        assertTrue(vendor.ran.isEmpty())
    }

    @Test
    fun subscribersSeeNestedStepsInOrder() {
        val seen = record(engine)
        engine.begin()

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            seen,
        )
    }

    @Test
    fun subscribersSeeNestedStepsInOrderWhenTheyCameAfterAnEarlierTransaction() {
        engine.begin()
        engine.start(CONFIG, CardPresence.chip())
        engine.notifyCompleted()
        val seen = record(engine) // e.g. a new MainViewModel

        engine.begin()
        engine.start(CONFIG, CardPresence.chip())

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            seen,
        )
    }

    @Test
    fun flowCollectorsSeeNestedStepsInOrder() {
        val seen = ArrayList<EmvStep>()
        // Like the UI: a collector of the flow, not a listener.
        val ui = GlobalScope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            engine.emvSteps.map { it.step }.toList(seen)
        }
        engine.begin()

        engine.start(CONFIG, CardPresence.chip())
        ui.cancel()

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            seen,
        )
    }

    @Test
    fun cancelledStepsAreNotRun() {
        engine.begin()
        engine.start(CONFIG, CardPresence.chip())
        vendor.ran.clear()

        vendor.cancel()
        vendor.go(EmvStep.TRANSACTION_COMPLETION)

        assertTrue(vendor.ran.isEmpty())
    }

    // ─── The end of a transaction ─────────────────────────────────────────

    @Test
    fun aCompletedTransactionStopsObservingAfterItsLastStepReachedTheUi() {
        vendor.approveOnCompletion = true
        val seen = record(engine)
        engine.begin()
        engine.start(CONFIG, CardPresence.chip())

        assertEquals(listOf(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps())
        assertEquals(EmvStep.TRANSACTION_COMPLETION, seen[seen.size - 1])

        vendor.ran.clear()
        seen.clear()
        engine.begin() // the next transaction, before its prepare registers again
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe")

        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty())
        assertEquals(listOf(EmvStep.SEARCH_CARD), seen)
    }

    @Test
    fun aThrowingStepEndsTheTransactionStopsObservingAndTheNextOneObservesAgain() {
        engine.begin()
        vendor.failOn = EmvStep.READ_APPLICATION_DATA

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(listOf(TransactionStep.ERROR), steps())
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA), vendor.ran)

        vendor.failOn = null
        vendor.ran.clear()
        engine.begin()
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe")
        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty())

        engine.prepare(CONFIG)
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe")
        assertEquals(listOf(EmvStep.TERMINAL_INITIALIZATION, EmvStep.SEARCH_CARD), vendor.ran)
    }

    @Test
    fun aStepFromCodeStillUnwindingAfterTheEndIsNeitherPublishedNorRun() {
        val seen = record(engine)
        engine.begin()
        vendor.errorOn = EmvStep.APPLICATION_SELECTION // finishError, then carries on to READ

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), vendor.ran)
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), seen)
        assertEquals(listOf(TransactionStep.ERROR), steps())
        assertFalse(engine.isRunning())
    }

    @Test
    fun anUnknownStepEndsTheTransactionAndNothingIsPublishedOrRunAfterIt() {
        val seen = record(engine)
        engine.begin()
        vendor.afterApplicationSelection = null // a step with no method to run

        engine.start(CONFIG, CardPresence.chip())
        vendor.go(EmvStep.TRANSACTION_COMPLETION) // code still unwinding

        assertEquals(listOf(TransactionStep.ERROR), steps())
        assertEquals("Unknown EMV step: null", transaction[0].message)
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), vendor.ran)
        assertEquals(listOf(EmvStep.APPLICATION_SELECTION), seen)
        assertFalse(engine.isRunning())
    }

    @Test
    fun aRetryInTheSameTransactionKeepsObserving() {
        engine.begin()
        engine.start(CONFIG, CardPresence.chip())
        vendor.ran.clear()

        engine.requestRetry(CONFIG, "Card not read — tap again") // no ERROR, no COMPLETED
        engine.start(engine.consumePendingRetry()!!, CardPresence.chip()) // as PosTerminal does

        assertEquals(
            listOf(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION),
            vendor.ran,
        )
        assertTrue(steps().isEmpty())
    }

    private fun steps(): List<TransactionStep> = transaction.map { it.step }

    private fun record(engine: EmvEngine): MutableList<EmvStep> {
        val seen = ArrayList<EmvStep>()
        engine.addEmvStepListener { seen.add(it.step) }
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

        fun go(step: EmvStep) {
            goToStep(step)
        }

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

        override fun onSearchCard(engine: EmvEngine, config: TransactionConfig, card: CardPresence?) {
            ran(EmvStep.SEARCH_CARD)
        }

        override fun onTerminalInitialization(engine: EmvEngine, config: TransactionConfig) {
            ran(EmvStep.TERMINAL_INITIALIZATION)
        }

        override fun onApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
            ran(EmvStep.APPLICATION_SELECTION)
            goToStep(afterApplicationSelection)
        }

        override fun onReadApplicationData(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
            ran(EmvStep.READ_APPLICATION_DATA)
            goToStep(EmvStep.TRANSACTION_COMPLETION)
        }

        override fun onTransactionCompletion(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
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

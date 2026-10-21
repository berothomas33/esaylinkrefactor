package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.EmvStep
import com.emvenhance.core.host.AuthResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Before
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import com.emvenhance.core.engine.TestSession
import org.junit.Test

/**
 * [AbstractEmvBehavior.askStep]: a kernel callback waiting on the cardholder publishes a step,
 * that step's method does the UI work and answers, and the answer comes back to the callback.
 * With no answer the kernel still gets one: the fail-closed value.
 */
class AskStepTest {

    private lateinit var engine: EmvEngine
    private lateinit var vendor: Vendor

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Before
    fun setUp() {
        vendor = Vendor()
        engine = vendor.session.engine(vendor) { AuthResult.failed("no host") }
        vendor.session.begin(engine)
    }

    @Test
    fun theStepMethodsAnswerReachesTheCallback() {
        val seen = engine.emvSteps.recordIn(scope)
        vendor.selectionAnswer = 1

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(1, vendor.callbackGot)
        assertEquals(Thread.currentThread(), vendor.answeredOn)
        assertTrue(seen.values.any { it.step == EmvStep.WAIT_APPLICATION_SELECTION })
    }

    @Test
    fun noAnswerGivesTheFailClosedValue() {
        vendor.selectionAnswer = null

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(USER_CANCEL, vendor.callbackGot)
    }

    @Test
    fun noAnswerIsNotedInTheTrace() {
        assumeTrue("trace records in debug builds only", engine.apduTrace().isEnabled())
        vendor.selectionAnswer = null

        engine.start(CONFIG, CardPresence.chip())

        assertTrue(engine.apduTrace().text().contains("no answer for"))
    }

    @Test
    fun aCancelledTransactionGetsTheFailClosedValue() {
        vendor.selectionAnswer = 1
        vendor.cancelBeforeAsking = true

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(USER_CANCEL, vendor.callbackGot)
        assertEquals(0, vendor.selectionRuns)
    }

    @Test
    fun aStepMethodThatThrowsGetsTheFailClosedValue() {
        vendor.selectionAnswer = 1
        vendor.throwInSelection = true

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(USER_CANCEL, vendor.callbackGot)
    }

    @Test
    fun aNestedAskKeepsEachAnswerApart() {
        vendor.selectionAnswer = 1
        vendor.askFinalInsideSelection = true

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(7, vendor.innerGot)
        assertEquals(1, vendor.callbackGot)
    }

    @Test
    fun anAnswerWithNobodyAskingIsIgnored() {
        vendor.selectionAnswer = 1
        vendor.goWithoutAsking = true

        engine.start(CONFIG, CardPresence.chip())

        assertEquals(USER_CANCEL, vendor.callbackGot)
    }

    /** APPLICATION_SELECTION plays the kernel calling back for the cardholder's choice. */
    private class Vendor : NoOpStepsBehavior() {
        /** The terminal's cancel flag (PosTerminal owns it in the app). */
        /** Stands in for the terminal: its cancel flag (PosTerminal owns it in the app). */
        val session = TestSession()
        var selectionAnswer: Int? = null
        var cancelBeforeAsking = false
        var throwInSelection = false
        var askFinalInsideSelection = false
        var goWithoutAsking = false
        var callbackGot = Int.MIN_VALUE
        var innerGot = Int.MIN_VALUE
        var selectionRuns = 0
        var answeredOn: Thread? = null

        override fun onApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
            if (cancelBeforeAsking) {
                session.cancel()
            }
            if (goWithoutAsking) {
                goToStep(EmvStep.WAIT_APPLICATION_SELECTION)
                callbackGot = USER_CANCEL
                return
            }
            // The kernel's callback: it waits for this answer.
            callbackGot = askStep(EmvStep.WAIT_APPLICATION_SELECTION, null, USER_CANCEL)
        }

        override fun onWaitApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
            selectionRuns++
            if (throwInSelection) {
                throw IllegalStateException("dialog failed")
            }
            if (askFinalInsideSelection) {
                innerGot = askStep(EmvStep.FINAL_APPLICATION_SELECTION, null, USER_CANCEL)
            }
            selectionAnswer?.let {
                answeredOn = Thread.currentThread()
                answerStep(it)
            }
        }

        override fun onFinalApplicationSelection(engine: EmvEngine, config: TransactionConfig, card: CardPresence) {
            answerStep(7)
        }
    }

    private companion object {
        val CONFIG = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP)
        const val USER_CANCEL = -100
    }
}

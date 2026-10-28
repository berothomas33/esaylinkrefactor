package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * WAITING_FOR_CARD carries KEY_RETRY_PROMPT only when the card has to be presented again, so the
 * search screen can show the reason on top of the card UI.
 */
class PosTerminalRetryPromptTest {

    private lateinit var engine: EmvEngine
    private lateinit var terminal: CapturingTerminal
    private lateinit var events: FlowRecorder<TransactionStepEvent>
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        terminal = CapturingTerminal()
        engine = terminal.engine()
        events = terminal.allSteps.flow.recordIn(scope)
        terminal.acceptCard(TransactionType.SALE, 1000)
        assertTrue("search never started", terminal.searching.await(5, TimeUnit.SECONDS))
        events.values.clear()
    }

    @After
    fun tearDown() {
        terminal.release.countDown()
        scope.cancel()
    }

    @Test
    fun firstSearchHasNoRetryPrompt() {
        terminal.listener!!.onSearchStarted(config())

        val waiting = lastWaiting()
        assertEquals("Insert, tap, or swipe", waiting.message)
        assertNull(waiting.get<Any>(TransactionStepEvent.KEY_RETRY_PROMPT))
    }

    @Test
    fun searchAfterARetryCarriesThePrompt() {
        engine.requestRetry(config(), "See phone — verify on your phone, then tap again")

        terminal.listener!!.onSearchStarted(config())

        val waiting = lastWaiting()
        assertEquals("See phone — verify on your phone, then tap again", waiting.message)
        assertEquals(
            "See phone — verify on your phone, then tap again",
            waiting.get<String>(TransactionStepEvent.KEY_RETRY_PROMPT),
        )
    }

    @Test
    fun readerRetryDuringSearchCarriesThePrompt() {
        terminal.listener!!.onSearchRetry("Card not read — tap again and hold the card still")

        val waiting = lastWaiting()
        assertEquals(
            "Card not read — tap again and hold the card still",
            waiting.get<String>(TransactionStepEvent.KEY_RETRY_PROMPT),
        )
    }

    private fun lastWaiting(): TransactionStepEvent {
        val last = events.values.lastOrNull { it.step == TransactionStep.WAITING_FOR_CARD }
        assertNotNull("no WAITING_FOR_CARD event", last)
        return last!!
    }

    private fun config() = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.ANY)

    /** Holds card search open and hands the test the terminal's own listener. */
    private class CapturingTerminal : PosTerminal(stubBehavior(prepared = true), stub(), stub()) {

        /** Every transaction's steps, for the test: each transaction has its own flow. */
        val allSteps = AllSteps()

        override fun startTransaction(config: TransactionConfig): Transaction =
            allSteps.relay(super.startTransaction(config))

        val searching = CountDownLatch(1)
        val release = CountDownLatch(1)

        @Volatile
        var listener: CardSearchListener? = null

        fun engine(): EmvEngine = behavior.engine()

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
            this.listener = listener
            searching.countDown()
            try {
                release.await(5, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return null
        }

        override fun cancelCardSearch() {}
    }
}

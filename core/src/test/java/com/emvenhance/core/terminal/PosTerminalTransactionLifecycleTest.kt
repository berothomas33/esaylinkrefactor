package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * One transaction at a time: a start while the previous one hasn't finished — even cancelled,
 * with its search still stopping — is refused as "terminal busy", never queued. And a search that
 * outlives its transaction can't fail, or start EMV for, the next one.
 *
 * Each transaction is its own flow: collecting starts it, cancelling the collector cancels that
 * one only, its steps are its own, and it completes once the terminal is free.
 */
class PosTerminalTransactionLifecycleTest {

    private lateinit var terminal: TestTerminal
    private val events = CopyOnWriteArrayList<TransactionStepEvent>()
    private val emvStartedWithAmount = CopyOnWriteArrayList<Long>()

    @Before
    fun setUp() {
        terminal = TestTerminal()
        terminal.engine().addTransactionStepListener { events.add(it) }
    }

    @After
    fun tearDown() {
        terminal.releaseAll()
    }

    @Test
    fun lateCallbacksFromAnEarlierSearchDoNotEndTheNextTransaction() {
        val first = cancelledSearchThenNextTransaction()

        first.listener.onSearchCancelled()
        first.listener.onSearchTimeout()
        first.listener.onReaderError("PICC: reader error")
        first.listener.onSearchStarted(first.config)

        assertTrue("next transaction got $events", events.isEmpty())
        assertTrue(terminal.engine().isRunning())
    }

    @Test
    fun aCardCaughtByAnEarlierSearchNeverStartsEmv() {
        val first = cancelledSearchThenNextTransaction()
        val second = terminal.nextSearch()

        first.listener.onChipDetected(CardPresence.chip())
        assertTrue(emvStartedWithAmount.isEmpty())
        assertFalse(hasStep(TransactionStep.CARD_DETECTED))

        second.listener.onChipDetected(CardPresence.chip())
        assertEquals(listOf(2000L), emvStartedWithAmount)
    }

    @Test
    fun aStartWhileTheCancelledSearchIsStillStoppingIsRefusedAsBusy() {
        val sale = terminal.acceptCard(TransactionType.SALE, 1000).record()
        val first = terminal.nextSearch()
        sale.cancel()
        events.clear()

        terminal.acceptCard(TransactionType.SALE, 2000).record()
            .assertError(TerminalBusyException::class.java)
        assertTrue("busy is not reported on the old transaction's steps: $events", events.isEmpty())

        first.release.countDown() // the old search finally stops
        assertTrue("old search kept searching", awaitTrue { first.cancelledWhenReleased })
        val second = startWhenFree(3000)
        assertEquals(3000L, second.config.amountMinor)
        assertSame("one transaction thread", first.thread, second.thread)
        assertNull("the refused start never ran", terminal.searches.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun aStartWhileSearchingIsRefusedAndTheFirstCarriesOn() {
        terminal.acceptCard(TransactionType.SALE, 1000).record()
        val first = terminal.nextSearch()
        events.clear()

        val refused = terminal.acceptCard(TransactionType.SALE, 2000).record()
        refused.assertError(TerminalBusyException::class.java)
        refused.cancel() // leaving the refused screen cancels nothing

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS))
        assertTrue("first transaction got $events", events.isEmpty())
        assertTrue(terminal.engine().isRunning())
        first.listener.onChipDetected(CardPresence.chip())
        assertEquals(listOf(1000L), emvStartedWithAmount)
    }

    @Test
    fun cancelCancelsItsOwnTransactionAndEndsItsFlow() {
        val sale = terminal.acceptCard(TransactionType.SALE, 1000).record()
        val first = terminal.nextSearch()
        first.listener.onSearchStarted(first.config)
        assertEquals(1, sale.values.size)

        sale.cancel()

        assertTrue(terminal.isSearchCancelled())
        assertTrue("the cancel reached the engine", hasStep(TransactionStep.ERROR))
        assertEquals("nothing after cancel, the cancel's ERROR included", 1, sale.values.size)
        first.release.countDown()
        assertTrue("old search kept searching", awaitTrue { first.cancelledWhenReleased })
    }

    @Test
    fun aTransactionCancelledBeforeItRanStillFreesTheTerminal() {
        val hold = CountDownLatch(1)
        terminal.transactionExecutor().execute { hold.await(5, TimeUnit.SECONDS) }
        val sale = terminal.acceptCard(TransactionType.SALE, 1000).record()
        sale.cancel() // its run() is still queued behind the held thread

        hold.countDown()

        val next = startWhenFree(2000)
        assertEquals("the cancelled one never searched", 2000L, next.config.amountMinor)
    }

    @Test
    fun completesOnlyOnceTheTerminalIsFree() {
        val sale = terminal.acceptCard(TransactionType.SALE, 1000).record()
        val first = terminal.nextSearch()
        sale.assertNotComplete()

        first.listener.onSearchTimeout() // like a vendor: one callback, then the search returns
        first.release.countDown()

        assertTrue(sale.await(5))
        sale.assertComplete().assertNoErrors()
        assertEquals("finishing is not a cancel", 0, terminal.searchCancels.get())
        terminal.acceptCard(TransactionType.SALE, 2000).record().assertNoErrors()
        assertEquals(2000L, terminal.nextSearch().config.amountMinor)
    }

    @Test
    fun aNewTransactionNeverSeesThePreviousOnesSteps() {
        val first = terminal.acceptCard(TransactionType.SALE, 1000).record()
        val firstSearch = terminal.nextSearch()
        firstSearch.listener.onSearchTimeout() // ERROR: the engine's latest step
        firstSearch.release.countDown()
        assertTrue(first.await(5))
        assertEquals(TransactionStep.ERROR, terminal.currentState().step)

        val second = terminal.acceptCard(TransactionType.SALE, 2000).record()
        val secondSearch = terminal.nextSearch()
        second.assertNoValues()

        secondSearch.listener.onSearchStarted(secondSearch.config)
        assertTrue("never delivered", awaitTrue { second.values.size == 1 })
        second.assertValueCount(1)
        assertEquals(TransactionStep.WAITING_FOR_CARD, second.values[0].step)
    }

    @Test
    fun theTerminalWideFlowReplaysTheLatestStep() {
        val first = terminal.acceptCard(TransactionType.SALE, 1000).record()
        val firstSearch = terminal.nextSearch()
        firstSearch.listener.onSearchTimeout()
        firstSearch.release.countDown()
        assertTrue(first.await(5))

        val status = terminal.transactionSteps().record() // e.g. MainActivity, created later

        assertEquals(listOf(TransactionStep.ERROR), status.values.map { it.step })
        status.cancel()
    }

    @Test
    fun engineRefusesASecondBeginWithoutEndingTheFirst() {
        assertTrue(terminal.engine().begin())
        events.clear()

        assertFalse(terminal.engine().begin())

        assertTrue(terminal.engine().isRunning())
        assertTrue("got $events", events.isEmpty())
    }

    /**
     * A transaction for 1000, cancelled, whose listener is still held (like a vendor reporting
     * late, from its own thread), then the next transaction for 2000, searching.
     */
    private fun cancelledSearchThenNextTransaction(): Search {
        val sale = terminal.acceptCard(TransactionType.SALE, 1000).record()
        val first = terminal.nextSearch()
        sale.cancel()
        first.release.countDown()
        val second = startWhenFree(2000)
        terminal.searches.add(second) // hand it back for tests that need it
        events.clear()
        return first
    }

    /** Starts a sale as soon as the terminal stops being busy, and returns its search. */
    private fun startWhenFree(amount: Long): Search {
        assertTrue("terminal stayed busy", awaitTrue { started(amount) })
        return terminal.nextSearch()
    }

    /** Collects a sale; `false` if it was refused as busy. */
    private fun started(amount: Long): Boolean =
        terminal.acceptCard(TransactionType.SALE, amount).record().error == null

    private fun awaitTrue(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }

    private fun hasStep(step: TransactionStep): Boolean = events.any { it.step == step }

    /** One `searchCard` call, held open until the test releases it. */
    private class Search(val config: TransactionConfig, val listener: CardSearchListener) {
        val release = CountDownLatch(1)
        val thread: Thread = Thread.currentThread()

        @Volatile
        var cancelledWhenReleased = false
    }

    private inner class TestTerminal : PosTerminal(
        stubBehavior(prepared = true) { config -> emvStartedWithAmount.add(config.amountMinor) },
        stub(),
        stub(),
    ) {
        val searches = LinkedBlockingQueue<Search>()
        val searchCancels = AtomicInteger()
        private val all = CopyOnWriteArrayList<Search>()

        fun engine(): EmvEngine = engine

        /** The terminal's one transaction thread, to hold it busy from a test. */
        fun transactionExecutor(): ExecutorService {
            val field = PosTerminal::class.java.getDeclaredField("transactionExecutor")
            field.isAccessible = true
            return field.get(this) as ExecutorService
        }

        fun nextSearch(): Search {
            val search = searches.poll(5, TimeUnit.SECONDS)
            assertNotNull("search never started", search)
            return search!!
        }

        fun releaseAll() {
            for (search in all) {
                search.release.countDown()
            }
        }

        /** Like a vendor poll that stops only when released, and reports nothing on its own. */
        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? {
            val search = Search(config, listener)
            all.add(search)
            searches.add(search)
            try {
                search.release.await(10, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            search.cancelledWhenReleased = isSearchCancelled()
            return null
        }

        override fun cancelCardSearch() {
            searchCancels.incrementAndGet()
        }
    }
}

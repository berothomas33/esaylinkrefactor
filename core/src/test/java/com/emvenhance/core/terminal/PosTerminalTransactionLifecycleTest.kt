package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
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
 * Each transaction is one call of the suspending start: calling starts it, cancelling the caller
 * cancels that one only, and it returns once the terminal is free.
 */
class PosTerminalTransactionLifecycleTest {

    private lateinit var terminal: TestTerminal
    private lateinit var events: FlowRecorder<TransactionStepEvent>
    private val emvStartedWithAmount = CopyOnWriteArrayList<Long>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        terminal = TestTerminal()
        events = terminal.transactionSteps().recordIn(scope)
    }

    @After
    fun tearDown() {
        terminal.releaseAll()
        scope.cancel()
    }

    @Test
    fun lateCallbacksFromAnEarlierSearchDoNotEndTheNextTransaction() = runTest {
        val first = cancelledSearchThenNextTransaction(backgroundScope)

        first.listener.onSearchCancelled()
        first.listener.onSearchTimeout()
        first.listener.onReaderError("PICC: reader error")
        first.listener.onSearchStarted(first.config)

        assertTrue("next transaction got ${events.values}", events.values.isEmpty())
        assertTrue(terminal.engine().isRunning())
    }

    @Test
    fun aCardCaughtByAnEarlierSearchNeverStartsEmv() = runTest {
        val first = cancelledSearchThenNextTransaction(backgroundScope)
        val second = terminal.nextSearch()

        first.listener.onChipDetected(CardPresence.chip())
        assertTrue(emvStartedWithAmount.isEmpty())
        assertFalse(hasStep(TransactionStep.CARD_DETECTED))

        second.listener.onChipDetected(CardPresence.chip())
        assertEquals(listOf(2000L), emvStartedWithAmount)
    }

    @Test
    fun aStartWhileTheCancelledSearchIsStillStoppingIsRefusedAsBusy() = runTest {
        val sale = sale(backgroundScope, 1000)
        val first = terminal.nextSearch()
        sale.cancel()
        events.values.clear()

        sale(backgroundScope, 2000).assertError(TerminalBusyException::class.java)
        assertTrue("busy is not reported on the old transaction's steps: ${events.values}", events.values.isEmpty())

        first.release.countDown() // the old search finally stops
        assertTrue("old search kept searching", awaitTrue { first.cancelledWhenReleased })
        val second = startWhenFree(backgroundScope, 3000)
        assertEquals(3000L, second.config.amountMinor)
        assertSame("one transaction thread", first.thread, second.thread)
        assertNull("the refused start never ran", terminal.searches.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun aStartWhileSearchingIsRefusedAndTheFirstCarriesOn() = runTest {
        sale(backgroundScope, 1000)
        val first = terminal.nextSearch()
        events.values.clear()

        val refused = sale(backgroundScope, 2000)
        refused.assertError(TerminalBusyException::class.java)
        refused.cancel() // leaving the refused screen cancels nothing

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS))
        assertTrue("first transaction got ${events.values}", events.values.isEmpty())
        assertTrue(terminal.engine().isRunning())
        first.listener.onChipDetected(CardPresence.chip())
        assertEquals(listOf(1000L), emvStartedWithAmount)
    }

    @Test
    fun disposeCancelsItsOwnTransaction() = runTest {
        val sale = sale(backgroundScope, 1000)
        val first = terminal.nextSearch()

        sale.cancel()

        assertTrue(terminal.isSearchCancelled())
        assertEquals(1, terminal.searchCancels.get())
        assertTrue("the cancel reached the engine", hasStep(TransactionStep.ERROR))
        first.release.countDown()
        assertTrue("old search kept searching", awaitTrue { first.cancelledWhenReleased })
    }

    @Test
    fun aTransactionDisposedBeforeItRanStillFreesTheTerminal() = runTest {
        val hold = CountDownLatch(1)
        terminal.transactionExecutor().execute { hold.await(5, TimeUnit.SECONDS) }
        val sale = sale(backgroundScope, 1000)
        sale.cancel() // its work is still queued behind the held thread

        hold.countDown()

        val next = startWhenFree(backgroundScope, 2000)
        assertEquals("the cancelled one never searched", 2000L, next.config.amountMinor)
    }

    @Test
    fun completesOnlyOnceTheTerminalIsFree() = runTest {
        val sale = sale(backgroundScope, 1000)
        val first = terminal.nextSearch()
        sale.assertNotComplete()

        first.listener.onSearchTimeout() // like a vendor: one callback, then the search returns
        first.release.countDown()

        assertTrue(sale.await(5))
        sale.assertComplete().assertNoErrors()
        sale.cancel() // a screen cancelling a finished sale
        assertEquals("finishing is not a cancel", 0, terminal.searchCancels.get())
        sale(backgroundScope, 2000).assertNoErrors()
        assertEquals(2000L, terminal.nextSearch().config.amountMinor)
    }

    @Test
    fun aSearchThatStopsWithNoOutcomeStillEndsItsTransaction() = runTest {
        val sale = sale(backgroundScope, 1000)
        val first = terminal.nextSearch()

        first.release.countDown() // returns without any listener callback
        assertTrue(sale.await(5))

        sale.assertComplete()
        assertFalse("the engine stayed claimed", terminal.engine().isRunning())
        assertTrue(hasStep(TransactionStep.ERROR))
        assertEquals("the next sale runs", 2000L, startWhenFree(backgroundScope, 2000).config.amountMinor)
    }

    @Test
    fun aSecondDisposeCancelsNothingMore() = runTest {
        val sale = sale(backgroundScope, 1000)
        val first = terminal.nextSearch()

        sale.cancel()
        sale.cancel()
        terminal.dispose() // the terminal going away cancels its transaction, once

        assertEquals(1, terminal.searchCancels.get())
        first.release.countDown()
    }

    @Test
    fun engineRefusesASecondBeginWithoutEndingTheFirst() {
        assertTrue(terminal.engine().begin())
        events.values.clear()

        assertFalse(terminal.engine().begin())

        assertTrue(terminal.engine().isRunning())
        assertTrue("got ${events.values}", events.values.isEmpty())
    }

    /** A sale for `amount`, started on the calling thread like the UI does. */
    private fun sale(scope: CoroutineScope, amount: Long) =
        TestSale(scope) { terminal.acceptCard(TransactionType.SALE, amount) }

    /**
     * A transaction for 1000, cancelled, whose listener is still held (like a vendor reporting
     * late, from its own thread), then the next transaction for 2000, searching.
     */
    private fun cancelledSearchThenNextTransaction(scope: CoroutineScope): Search {
        val sale = sale(scope, 1000)
        val first = terminal.nextSearch()
        sale.cancel()
        first.release.countDown()
        val second = startWhenFree(scope, 2000)
        terminal.searches.add(second) // hand it back for tests that need it
        events.values.clear()
        return first
    }

    /** Starts a sale as soon as the terminal stops being busy, and returns its search. */
    private fun startWhenFree(scope: CoroutineScope, amount: Long): Search {
        assertTrue("terminal stayed busy", awaitTrue { sale(scope, amount).error == null })
        return terminal.nextSearch()
    }

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

    private fun hasStep(step: TransactionStep): Boolean = events.values.any { it.step == step }

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

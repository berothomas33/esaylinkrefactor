package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.event.TransactionStepEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
 * A start returns its transaction at once; its cancel cancels that one only, and its end
 * (COMPLETED / ERROR) is published once, after the terminal is free.
 */
class PosTerminalTransactionLifecycleTest {

    private lateinit var terminal: TestTerminal
    private lateinit var events: FlowRecorder<TransactionStepEvent>
    private lateinit var ends: FlowRecorder<TransactionStepEvent>
    private val emvStartedWithAmount = CopyOnWriteArrayList<Long>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        terminal = TestTerminal()
        events = terminal.allSteps.flow.recordIn(scope)
        ends = terminal.allSteps.flow.recordIn(scope)
    }

    @After
    fun tearDown() {
        terminal.releaseAll()
        scope.cancel()
    }

    @Test
    fun lateCallbacksFromAnEarlierSearchDoNotEndTheNextTransaction() {
        val first = cancelledSearchThenNextTransaction()

        first.listener.onSearchCancelled()
        first.listener.onSearchTimeout()
        first.listener.onReaderError("PICC: reader error")
        first.listener.onSearchStarted(first.config)

        assertTrue("next transaction got ${events.values}", events.values.isEmpty())
        assertTrue(terminal.last!!.isRunning())
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
        val sale = sale(1000)
        val first = terminal.nextSearch()
        sale.cancel()
        events.values.clear()

        assertThrows(TerminalBusyException::class.java) { sale(2000) }
        assertTrue("busy is not reported on the old transaction's steps: ${events.values}", events.values.isEmpty())

        first.release.countDown() // the old search finally stops
        assertTrue("old search kept searching", awaitTrue { first.cancelledWhenReleased })
        val second = startWhenFree(3000)
        assertEquals(3000L, second.config.amountMinor)
        assertSame("one transaction thread", first.thread, second.thread)
        assertNull("the refused start never ran", terminal.searches.poll(300, TimeUnit.MILLISECONDS))
    }

    @Test
    fun aStartWhileSearchingIsRefusedAndTheFirstCarriesOn() {
        sale(1000)
        val first = terminal.nextSearch()
        events.values.clear()

        assertThrows(TerminalBusyException::class.java) { sale(2000) }

        assertNull(terminal.searches.poll(300, TimeUnit.MILLISECONDS))
        assertTrue("first transaction got ${events.values}", events.values.isEmpty())
        assertTrue(terminal.last!!.isRunning())
        first.listener.onChipDetected(CardPresence.chip())
        assertEquals(listOf(1000L), emvStartedWithAmount)
    }

    @Test
    fun aCancelStopsItsTransactionAndItsErrorComesOnceTheTerminalIsFree() {
        val sale = sale(1000)
        val first = terminal.nextSearch()
        events.values.clear()

        sale.cancel()

        assertTrue(terminal.isSearchCancelled())
        assertFalse("the EMV winds down", sale.isRunning())
        assertTrue(sale.isCancelled())
        assertEquals(1, terminal.searchCancels.get())
        assertEquals("only CANCELLING before the transaction thread stops", listOf(TransactionStep.CANCELLING), events.values.map { it.step })

        first.listener.onSearchCancelled() // what the vendor reports as its poll stops
        first.release.countDown()
        val end = awaitEnd()
        assertEquals(TransactionStep.CANCELLED, end.step)
        assertEquals("one end", 1, events.values.count { it.step.isEnd })
    }

    @Test
    fun aTransactionCancelledBeforeItRanStillFreesTheTerminal() {
        val hold = CountDownLatch(1)
        terminal.transactionExecutor().execute { hold.await(5, TimeUnit.SECONDS) }
        val sale = sale(1000)
        sale.cancel() // its work is still queued behind the held thread

        hold.countDown()

        val next = startWhenFree(2000)
        assertEquals("the cancelled one never searched", 2000L, next.config.amountMinor)
        assertEquals("nothing searching to stop", 1, terminal.searchCancels.get())
    }

    @Test
    fun theEndIsPublishedOnceTheTerminalIsFree() {
        val startOnEnd = CopyOnWriteArrayList<Throwable>()
        // On the publishing thread, like a screen's "Retry" right away.
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            terminal.allSteps.flow.first { it.step.isEnd }
            try {
                sale(2000)
            } catch (t: Throwable) {
                startOnEnd.add(t)
            }
        }
        sale(1000)
        val first = terminal.nextSearch()

        first.listener.onSearchTimeout() // like a vendor: one callback, then the search returns
        first.release.countDown()

        assertEquals(TransactionStep.ERROR, awaitEnd().step)
        assertEquals(2000L, terminal.nextSearch().config.amountMinor)
        assertTrue("busy on the end: $startOnEnd", startOnEnd.isEmpty())
        assertEquals("finishing is not a cancel", 0, terminal.searchCancels.get())
    }

    @Test
    fun aTransactionEndsOnceWhateverItsCodeReportsAfter() {
        val sale = sale(1000)
        val first = terminal.nextSearch()
        events.values.clear()

        first.listener.onSearchTimeout() // the end
        sale.publish(TransactionStepEvent.of(TransactionStep.APPROVED)) // code still unwinding
        sale.publish(TransactionStepEvent.of(TransactionStep.COMPLETED))
        sale.publish(PosTerminal.errorEvent("Transaction cancelled"))
        first.release.countDown()

        val end = awaitEnd()
        assertEquals("No card presented — card search timed out", end.message)
        assertEquals(listOf(TransactionStep.ERROR), events.values.map { it.step })
    }

    @Test
    fun aSearchThatStopsWithNoOutcomeStillEndsItsTransaction() {
        sale(1000)
        val first = terminal.nextSearch()

        first.release.countDown() // returns without any listener callback

        val end = awaitEnd()
        assertEquals("Transaction ended without an outcome", end.message)
        assertFalse(terminal.last!!.isRunning())
        assertEquals("the next sale runs", 2000L, startWhenFree(2000).config.amountMinor)
    }

    @Test
    fun aCancelAfterTheEndAndASecondCancelDoNothing() {
        val sale = sale(1000)
        val first = terminal.nextSearch()

        sale.cancel()
        sale.cancel()
        terminal.dispose() // the terminal going away cancels its transaction, once
        assertEquals(1, terminal.searchCancels.get())

        first.release.countDown()
        awaitEnd()
        sale.cancel() // a screen cancelling a finished sale
        assertEquals(1, terminal.searchCancels.get())
    }

    @Test
    fun nothingIsPublishedOutsideATransaction() {
        terminal.authorize(TransactionConfig(TransactionType.SALE, 1000, EntryMethod.ANY))

        assertTrue("ONLINE_* with no transaction: ${events.values}", events.values.isEmpty())
        assertFalse(terminal.isBusy())
    }

    /** A sale for `amount`, started on the calling thread like the UI does. */
    private fun sale(amount: Long): PosTerminal.Transaction = terminal.acceptCard(TransactionType.SALE, amount)

    /** The next end published (each one once), within 5 s. */
    private fun awaitEnd(): TransactionStepEvent {
        assertTrue("no end published", ends.await(endsSeen + 1) { it.step.isEnd })
        return ends.values.filter { it.step.isEnd }[endsSeen++]
    }

    private var endsSeen = 0

    /**
     * A transaction for 1000, cancelled, whose listener is still held (like a vendor reporting
     * late, from its own thread), then the next transaction for 2000, searching.
     */
    private fun cancelledSearchThenNextTransaction(): Search {
        val sale = sale(1000)
        val first = terminal.nextSearch()
        sale.cancel()
        first.release.countDown()
        val second = startWhenFree(2000)
        terminal.searches.add(second) // hand it back for tests that need it
        events.values.clear()
        return first
    }

    /** Starts a sale as soon as the terminal stops being busy, and returns its search. */
    private fun startWhenFree(amount: Long): Search {
        assertTrue("terminal stayed busy", awaitTrue { runCatching { sale(amount) }.isSuccess })
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
        CommunicationBehavior { AuthResult.failed("no host") },
        stub(),
    ) {
        val searches = LinkedBlockingQueue<Search>()
        val searchCancels = AtomicInteger()
        private val all = CopyOnWriteArrayList<Search>()

        fun engine(): EmvEngine = behavior.engine()

        /** Every transaction's steps, for the test: each transaction has its own flow. */
        val allSteps = AllSteps()

        /** The last transaction started. */
        @Volatile
        var last: Transaction? = null

        override fun startTransaction(config: TransactionConfig): Transaction =
            allSteps.relay(super.startTransaction(config)).also { last = it }

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

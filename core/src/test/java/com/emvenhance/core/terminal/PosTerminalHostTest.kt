package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.CardSearchListener
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.host.AuthResult
import com.emvenhance.core.host.CommunicationBehavior
import com.emvenhance.core.host.PrinterBehavior
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The terminal owns the host and the printer: a behavior's online request reaches the host
 * through `engine.authorize → PosTerminal.authorize`, and receipts go to the terminal's own
 * printer.
 */
class PosTerminalHostTest {

    private val sentToHost = ArrayList<TransactionConfig>()
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }
    private val printed = CopyOnWriteArrayList<List<String>>()
    private val printDone = CountDownLatch(1)

    @Test
    fun behaviorsOnlineRequestGoesThroughTheTerminalsHost() {
        val terminal = TestTerminal(CommunicationBehavior { config ->
            sentToHost.add(config)
            AuthResult.approved("123456", "00", null, null)
        })
        val steps = terminal.transactionSteps().recordIn(scope)
        terminal.engine().begin() // a vendor goes online inside a transaction

        val result = terminal.engine().authorize(CONFIG)

        assertTrue(result.isApproved)
        assertEquals(1, sentToHost.size)
        assertSame(CONFIG, sentToHost[0])
        assertEquals(
            listOf(TransactionStep.ONLINE_REQUIRED, TransactionStep.ONLINE_PROCESSING, TransactionStep.ONLINE_COMPLETED),
            steps.values.map { it.step },
        )
    }

    @Test
    fun noHostAnswerIsOnlineFailedNotADecline() {
        val terminal = TestTerminal(CommunicationBehavior { throw RuntimeException("timeout") })

        val result = terminal.engine().authorize(CONFIG)

        assertTrue(result.isFailed)
        assertEquals("timeout", result.message)
    }

    @Test(timeout = 5_000)
    fun aHostThatNeverAnswersIsOnlineFailedAfterTheTimeoutAndTheRequestIsCancelled() {
        val requestsCancelled = AtomicInteger()
        val terminal = TestTerminal(CommunicationBehavior {
            try {
                awaitCancellation()
            } finally {
                requestsCancelled.incrementAndGet()
            }
        })
        terminal.onlineTimeoutMs = 200
        val steps = terminal.transactionSteps().recordIn(scope)
        terminal.engine().begin()

        val started = System.currentTimeMillis()
        val result = terminal.engine().authorize(CONFIG)

        assertTrue(result.isFailed)
        assertEquals("Online failed: the host didn't answer in time", result.message)
        assertTrue(
            "waited " + (System.currentTimeMillis() - started) + " ms",
            System.currentTimeMillis() - started < 2_000,
        )
        assertEquals(1, requestsCancelled.get())
        assertEquals(TransactionStep.ONLINE_COMPLETED, steps.values.last().step)
    }

    @Test
    fun aHostCallRunsOnTheCallersThread() {
        val hostThread = arrayOfNulls<Thread>(1)
        val terminal = TestTerminal(CommunicationBehavior {
            hostThread[0] = Thread.currentThread()
            AuthResult.approved("123456", "00", null, null)
        })

        terminal.engine().authorize(CONFIG)

        assertSame("runBlocking keeps the transaction thread", Thread.currentThread(), hostThread[0])
    }

    @Test
    fun receiptsGoToTheTerminalsPrinter() {
        val terminal = TestTerminal(CommunicationBehavior { awaitCancellation() })

        terminal.printReceipt(listOf("APPROVED"))

        assertTrue("never printed", printDone.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(listOf("APPROVED")), printed)
    }

    @Test
    fun receiptsPrintOneAtATimeInTheOrderAsked() {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val order = CopyOnWriteArrayList<String>()
        val threads = CopyOnWriteArrayList<Thread>()
        val allPrinted = CountDownLatch(3)
        val terminal = TestTerminal(CommunicationBehavior { awaitCancellation() }) { lines ->
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
            Thread.sleep(50) // a real printer is slow
            order.add(lines[0])
            threads.add(Thread.currentThread())
            inFlight.decrementAndGet()
            allPrinted.countDown()
        }

        terminal.printReceipt(listOf("first"))
        terminal.printReceipt(listOf("second"))
        terminal.printReceipt(listOf("third"))

        assertTrue("not all printed", allPrinted.await(5, TimeUnit.SECONDS))
        assertEquals("printer driven from two threads at once", 1, maxInFlight.get())
        assertEquals(listOf("first", "second", "third"), order)
        assertSame("one print thread", threads[0], threads[1])
        assertSame("one print thread", threads[1], threads[2])
        assertNotSame("not the caller's thread", Thread.currentThread(), threads[0])
    }

    @Test
    fun aSuspendingPrinterStillPrintsOneReceiptAtATime() {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val order = CopyOnWriteArrayList<String>()
        val allPrinted = CountDownLatch(3)
        val terminal = TestTerminal(CommunicationBehavior { awaitCancellation() }) { lines ->
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max)
            delay(50) // suspends: the print thread is free meanwhile
            order.add(lines[0])
            inFlight.decrementAndGet()
            allPrinted.countDown()
        }

        terminal.printReceipt(listOf("first"))
        terminal.printReceipt(listOf("second"))
        terminal.printReceipt(listOf("third"))

        assertTrue("not all printed", allPrinted.await(5, TimeUnit.SECONDS))
        assertEquals("two receipts at once", 1, maxInFlight.get())
        assertEquals(listOf("first", "second", "third"), order)
    }

    private inner class TestTerminal(
        host: CommunicationBehavior,
        printer: PrinterBehavior = PrinterBehavior { lines ->
            printed.add(lines)
            printDone.countDown()
        },
    ) : PosTerminal(stubBehavior(), host, printer) {

        var onlineTimeoutMs = 90_000L

        fun engine() = engine

        override fun searchCard(config: TransactionConfig, listener: CardSearchListener): CardPresence? = null

        override fun cancelCardSearch() {}

        override fun onlineTimeoutMs(): Long = onlineTimeoutMs
    }

    private companion object {
        val CONFIG = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP)
    }
}

package com.emvenhance.core.terminal;

import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.ApduTrace;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.transaction.TransactionStepEvent;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * The transaction runs on the transaction thread from TRANSACTION_STARTED, once; its EMV owns the
 * retries: one the kernel asks for (fallback, try another interface, try again, See Phone) is the
 * next turn of the behavior's run loop — SEARCH_CARD again after the previous search returned,
 * never one started from inside its callback. Whatever happens, the end is published once, with
 * the terminal already free.
 */
public class PosTerminalRetryLoopTest {

    private final List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();
    private final List<EntryMethod> searchedWith = new CopyOnWriteArrayList<>();
    private final AtomicInteger searchesOnStack = new AtomicInteger();
    private final AtomicInteger deepestNesting = new AtomicInteger();
    private final CountDownLatch ended = new CountDownLatch(1);
    /** What the EMV on the card found does: ask for a retry, maybe cancel first. */
    private final AtomicReference<java.util.function.BiConsumer<AbstractEmvBehavior, PosTerminal.Transaction>>
            onStart = new AtomicReference<>();
    private final AtomicReference<PosTerminal.Transaction> sale = new AtomicReference<>();
    private final List<Thread> searchThreads = new CopyOnWriteArrayList<>();
    private final List<Boolean> busyAtTheEnd = new CopyOnWriteArrayList<>();
    /** Thrown by the search when set. */
    private volatile RuntimeException searchFails;

    @Test
    public void aRetryIsTheNextTurnOfTheLoopNotASearchInsideTheLastOne() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        onStart.set((behavior, transaction) -> {
            if (starts.incrementAndGet() == 1) {
                behavior.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
                        EntryMethod.MAGSTRIPE), "Chip not readable — swipe the card");
            } else {
                transaction.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
                transaction.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
            }
        });
        TestTerminal terminal = new TestTerminal();

        sale.set(terminal.acceptCard(TransactionType.SALE, 1000));
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        assertEquals(Arrays.asList(EntryMethod.ANY, EntryMethod.MAGSTRIPE), searchedWith);
        assertEquals("a search started inside another", 1, deepestNesting.get());
        assertEquals("once per transaction", 1, count(TransactionStep.TRANSACTION_STARTED));
        assertEquals("a search per attempt", 2, count(TransactionStep.WAITING_FOR_CARD));
        TransactionStepEvent retrySearch = events.get(lastIndexOf(TransactionStep.WAITING_FOR_CARD));
        assertEquals("the retry's own prompt", "Chip not readable — swipe the card",
                retrySearch.get(TransactionStepEvent.KEY_RETRY_PROMPT));
        assertTrue("the retry's search before its outcome",
                lastIndexOf(TransactionStep.WAITING_FOR_CARD) < lastIndexOf(TransactionStep.APPROVED));
        assertEquals(TransactionStep.COMPLETED, events.get(events.size() - 1).getStep());
    }

    @Test
    public void everyAttemptRunsOnTheTransactionThreadNotTheCaller() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        onStart.set((behavior, transaction) -> {
            if (starts.incrementAndGet() == 1) {
                behavior.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
                        EntryMethod.MAGSTRIPE), "Chip not readable — swipe the card");
            } else {
                transaction.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
            }
        });
        TestTerminal terminal = new TestTerminal();

        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        assertEquals(2, searchThreads.size());
        assertTrue("not the caller's thread", searchThreads.get(0) != Thread.currentThread());
        assertTrue("one transaction thread", searchThreads.get(0) == searchThreads.get(1));
    }

    @Test
    public void aSearchThatThrowsEndsWithAnErrorAndFreesTheTerminal() throws Exception {
        searchFails = new IllegalStateException("reader gone");
        TestTerminal terminal = new TestTerminal();

        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        TransactionStepEvent end = events.get(events.size() - 1);
        assertEquals(TransactionStep.ERROR, end.getStep());
        assertEquals("reader gone", end.getMessage());
        assertEquals("free before the end is seen", Arrays.asList(false), busyAtTheEnd);
    }

    @Test
    public void anAttemptWithNoOutcomeEndsWithAnError() throws Exception {
        onStart.set((behavior, transaction) -> { }); // EMV reports nothing
        TestTerminal terminal = new TestTerminal();

        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        assertEquals("Transaction ended without an outcome",
                events.get(events.size() - 1).getMessage());
        assertEquals(Arrays.asList(false), busyAtTheEnd);
        assertEquals("the end once", 1, countEnds());
    }

    @Test
    public void aRetryAskedForByACancelledTransactionNeverSearches() throws Exception {
        CountDownLatch saleKnown = new CountDownLatch(1);
        onStart.set((behavior, transaction) -> {
            awaitQuietly(saleKnown);
            sale.get().cancel(); // the screen cancels while EMV is running
            behavior.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
                    EntryMethod.CHIP), "Insert the card");
        });
        TestTerminal terminal = new TestTerminal();

        sale.set(terminal.acceptCard(TransactionType.SALE, 1000));
        saleKnown.countDown();
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        assertEquals("the retry never searched", Arrays.asList(EntryMethod.ANY), searchedWith);
        assertEquals("one search", 1, count(TransactionStep.WAITING_FOR_CARD));
        TransactionStepEvent end = events.get(events.size() - 1);
        assertEquals("Transaction cancelled", end.getMessage());
        assertFalse(terminal.isBusy());
    }

    @Test
    public void aRetryIsMarkedInTheTraceAndItsGenerateAcIsAFirstOneAgain() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        AtomicReference<ApduTrace> trace = new AtomicReference<>();
        onStart.set((behavior, transaction) -> {
            trace.set(behavior.engine().apduTrace());
            generateAc(trace.get());
            if (starts.incrementAndGet() == 1) {
                trace.get().note("PICC", "card communication error -2");
                behavior.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
                        EntryMethod.CONTACTLESS), "Card not read — tap again");
            } else {
                transaction.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
            }
        });
        TestTerminal terminal = new TestTerminal();
        assumeTrue("trace records in debug builds only",
                terminal.behavior.engine().apduTrace().isEnabled());

        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        String text = trace.get().text();
        assertTrue("the failed attempt stays", text.contains("card communication error -2"));
        assertTrue(text.contains("--- RETRY #1"));
        assertTrue(text.indexOf("communication error") < text.indexOf("RETRY #1"));
        assertFalse("the retry's GENERATE AC is a 1st one", text.contains("2nd GENERATE AC"));
    }

    @Test
    public void theRetryCountStartsAtZeroInEachTransaction() throws Exception {
        List<Integer> counts = new CopyOnWriteArrayList<>();
        onStart.set((behavior, transaction) -> {
            counts.add(behavior.retryCount());
            if (counts.size() == 1) {
                behavior.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
                        EntryMethod.MAGSTRIPE), "Chip not readable — swipe the card");
            } else {
                transaction.emvFinished(TransactionStepEvent.of(TransactionStep.DECLINED));
            }
        });
        TestTerminal terminal = new TestTerminal();

        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        terminal.acceptCard(TransactionType.SALE, 1000);
        long deadline = System.currentTimeMillis() + 5_000;
        while (countEnds() < 2 && System.currentTimeMillis() < deadline) {
            Thread.yield();
        }

        assertEquals("first transaction: 0 then 1; the next starts again at 0",
                Arrays.asList(0, 1, 0), counts);
    }

    private static void generateAc(ApduTrace trace) {
        trace.command("PICC", new byte[] {(byte) 0x80, (byte) 0xAE, (byte) 0x80, 0x00}, 0, null, 0);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int lastIndexOf(TransactionStep step) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).getStep() == step) {
                return i;
            }
        }
        return -1;
    }

    private int countEnds() {
        int n = 0;
        for (TransactionStepEvent e : events) {
            if (e.getStep().isEnd()) {
                n++;
            }
        }
        return n;
    }

    private int count(TransactionStep step) {
        int n = 0;
        for (TransactionStepEvent e : events) {
            if (e.getStep() == step) {
                n++;
            }
        }
        return n;
    }

    private final class TestTerminal extends PosTerminal {

        /** Every transaction's steps, for the test: each transaction has its own stream. */
        final PublishSubject<TransactionStepEvent> allSteps = PublishSubject.create();

        @Override
        public Transaction startTransaction(TransactionConfig config) {
            Transaction transaction = super.startTransaction(config);
            transaction.steps().subscribe(allSteps::onNext);
            return transaction;
        }

        TestTerminal() {
            super(behavior(), stub(CommunicationBehavior.class), stub(PrinterBehavior.class));
            allSteps.subscribe(e -> {
                events.add(e);
                if (e.getStep().isEnd()) {
                    busyAtTheEnd.add(isBusy());
                    ended.countDown();
                }
            });
        }
    }

    /**
     * The search finds a chip at once (or the mode a retry asked for), on the transaction thread,
     * and the EMV on it does what the test set in {@link #onStart}.
     */
    private AbstractEmvBehavior behavior() {
        AtomicReference<AbstractEmvBehavior> self = new AtomicReference<>();
        self.set(StubBehavior.of((name, args) -> {
            if (name.equals("search")) {
                search((TransactionConfig) args[0], (CardSearchListener) args[1]);
            }
            if (name.equals("start")) {
                onStart.get().accept(self.get(), (PosTerminal.Transaction) args[2]);
            }
            return null;
        }));
        return self.get();
    }

    private void search(TransactionConfig config, CardSearchListener listener) {
        deepestNesting.accumulateAndGet(searchesOnStack.incrementAndGet(), Math::max);
        searchThreads.add(Thread.currentThread());
        try {
            if (searchFails != null) {
                throw searchFails;
            }
            listener.onSearchStarted(config);
            searchedWith.add(config.getMode());
            listener.onChipDetected(CardPresence.chip()); // EMV runs right here, as on PAX
        } finally {
            searchesOnStack.decrementAndGet();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }
}

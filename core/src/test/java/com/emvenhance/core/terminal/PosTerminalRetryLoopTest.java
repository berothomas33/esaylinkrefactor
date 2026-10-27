package com.emvenhance.core.terminal;

import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
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
 * The transaction runs from its own steps, on the transaction thread: TRANSACTION_STARTED runs
 * the first attempt; a retry the behavior asks for (fallback, try another interface, try again,
 * See Phone) is NEXT_ATTEMPT — a new search after the previous one returned, never one started
 * from inside its callback — and TRANSACTION_STARTED comes only once. Whatever happens, the end
 * is published once, with the terminal already free.
 */
public class PosTerminalRetryLoopTest {

    private final List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();
    private final List<EntryMethod> searchedWith = new CopyOnWriteArrayList<>();
    private final AtomicInteger searchesOnStack = new AtomicInteger();
    private final AtomicInteger deepestNesting = new AtomicInteger();
    private final CountDownLatch ended = new CountDownLatch(1);
    /** What the behavior's start() does: ask for a retry, maybe cancel first. */
    private final AtomicReference<java.util.function.BiConsumer<EmvEngine, PosTerminal.Transaction>>
            onStart = new AtomicReference<>();
    private final AtomicReference<PosTerminal.Transaction> sale = new AtomicReference<>();
    private final List<Thread> searchThreads = new CopyOnWriteArrayList<>();
    private final List<Boolean> busyAtTheEnd = new CopyOnWriteArrayList<>();
    /** Thrown by the search when set. */
    private volatile RuntimeException searchFails;

    @Test
    public void aRetryIsTheNextTurnOfTheLoopNotASearchInsideTheLastOne() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        onStart.set((engine, transaction) -> {
            if (starts.incrementAndGet() == 1) {
                engine.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
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
        assertEquals("once per retry", 1, count(TransactionStep.NEXT_ATTEMPT));
        assertTrue("NEXT_ATTEMPT before the second search's steps",
                indexOf(TransactionStep.NEXT_ATTEMPT) < lastIndexOf(TransactionStep.APPROVED));
        assertEquals(TransactionStep.COMPLETED, events.get(events.size() - 1).getStep());
    }

    @Test
    public void everyAttemptRunsOnTheTransactionThreadNotTheCaller() throws Exception {
        AtomicInteger starts = new AtomicInteger();
        onStart.set((engine, transaction) -> {
            if (starts.incrementAndGet() == 1) {
                engine.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
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
        assertEquals(0, count(TransactionStep.NEXT_ATTEMPT));
    }

    @Test
    public void anAttemptWithNoOutcomeEndsWithAnError() throws Exception {
        onStart.set((engine, transaction) -> { }); // EMV reports nothing
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
        onStart.set((engine, transaction) -> {
            awaitQuietly(saleKnown);
            sale.get().cancel(); // the screen cancels while EMV is running
            engine.requestRetry(new TransactionConfig(TransactionType.SALE, 1000,
                    EntryMethod.CHIP), "Insert the card");
        });
        TestTerminal terminal = new TestTerminal();

        sale.set(terminal.acceptCard(TransactionType.SALE, 1000));
        saleKnown.countDown();
        assertTrue(ended.await(5, TimeUnit.SECONDS));

        assertEquals("the retry never searched", Arrays.asList(EntryMethod.ANY), searchedWith);
        assertEquals("no NEXT_ATTEMPT once cancelled", 0, count(TransactionStep.NEXT_ATTEMPT));
        TransactionStepEvent end = events.get(events.size() - 1);
        assertEquals("Transaction cancelled", end.getMessage());
        assertFalse(terminal.isSearchCancelled());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int indexOf(TransactionStep step) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getStep() == step) {
                return i;
            }
        }
        return -1;
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

    /** A search that finds a chip at once (or swipes, on a magstripe retry), on the transaction thread. */
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

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            deepestNesting.accumulateAndGet(searchesOnStack.incrementAndGet(), Math::max);
            searchThreads.add(Thread.currentThread());
            try {
                if (searchFails != null) {
                    throw searchFails;
                }
                searchedWith.add(config.getMode());
                CardPresence card = CardPresence.chip();
                listener.onChipDetected(card); // EMV runs right here, as on PAX
                return card;
            } finally {
                searchesOnStack.decrementAndGet();
            }
        }

        @Override
        protected void cancelCardSearch() {
        }
    }

    /** prepare() succeeds; start() does what the test set in {@link #onStart}. */
    private AbstractEmvBehavior behavior() {
        return StubBehavior.of((proxy, method, args) -> {
            if (method.getName().equals("prepare")) {
                return Boolean.TRUE;
            }
            if (method.getName().equals("start")) {
                onStart.get().accept(((AbstractEmvBehavior) proxy).engine(), (PosTerminal.Transaction) args[2]);
            }
            return method.getReturnType() == boolean.class ? false : null;
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }
}

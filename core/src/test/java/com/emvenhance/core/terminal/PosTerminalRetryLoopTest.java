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
 * A retry the behavior asks for (fallback, try another interface, try again, See Phone) is the
 * next turn of {@code PosTerminal.process}'s loop: a new search after the previous one
 * returned, never one started from inside its callback, and TRANSACTION_STARTED only once.
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
        assertEquals(TransactionStep.COMPLETED, events.get(events.size() - 1).getStep());
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
                    ended.countDown();
                }
            });
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            deepestNesting.accumulateAndGet(searchesOnStack.incrementAndGet(), Math::max);
            try {
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
    private EmvBehavior behavior() {
        return (EmvBehavior) Proxy.newProxyInstance(EmvBehavior.class.getClassLoader(),
                new Class<?>[] {EmvBehavior.class}, (proxy, method, args) -> {
                    if (method.getName().equals("prepare")) {
                        return Boolean.TRUE;
                    }
                    if (method.getName().equals("start")) {
                        onStart.get().accept((EmvEngine) args[0], (PosTerminal.Transaction) args[3]);
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

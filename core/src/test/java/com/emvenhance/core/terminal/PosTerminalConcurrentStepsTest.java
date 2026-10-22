package com.emvenhance.core.terminal;

import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
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
import org.junit.Test;

/**
 * Every publish holds the terminal's one lock: a step reported from a second thread (a vendor
 * SDK callback, a cancel) while one is being delivered waits for it — subscribers never get two
 * {@code onNext} calls at once.
 */
public class PosTerminalConcurrentStepsTest {

    @Test
    public void aStepFromASecondThreadWaitsForTheStepBeingDelivered() throws Exception {
        TestTerminal terminal = new TestTerminal();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<String> delivered = new CopyOnWriteArrayList<>();
        terminal.allSteps.subscribe(event -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            delivered.add(event.getStep() + " on " + Thread.currentThread().getName());
            if (event.getStep() == TransactionStep.ONLINE_PROCESSING) {
                firstInside.countDown();
                releaseFirst.await(5, TimeUnit.SECONDS); // still delivering when the second lands
            }
            inFlight.decrementAndGet();
        });
        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(terminal.searching.await(5, TimeUnit.SECONDS));

        Thread first = new Thread(() -> terminal.engine.notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING)), "first");
        first.start();
        assertTrue(firstInside.await(5, TimeUnit.SECONDS));
        Thread second = new Thread(() -> terminal.engine.notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_COMPLETED)), "second");
        second.start();
        second.join(300);
        assertTrue("the second waits for the first's delivery", second.isAlive());

        releaseFirst.countDown();
        first.join(5_000);
        second.join(5_000);
        terminal.release.countDown();

        assertEquals("two onNext calls at once", 1, maxInFlight.get());
        assertEquals(Arrays.asList("ONLINE_PROCESSING on first", "ONLINE_COMPLETED on second"),
                delivered.subList(1, 3)); // after TRANSACTION_STARTED
    }

    @Test
    public void aSubscriberThatThrowsStopsNeitherTheStreamNorTheTransaction() throws Exception {
        TestTerminal terminal = new TestTerminal();
        List<TransactionStep> seen = new CopyOnWriteArrayList<>();
        CountDownLatch ended = new CountDownLatch(1);
        PosTerminal.Transaction sale = terminal.acceptCard(TransactionType.SALE, 1000);
        sale.steps().subscribe(new io.reactivex.rxjava3.core.Observer<TransactionStepEvent>() {
            @Override public void onSubscribe(io.reactivex.rxjava3.disposables.Disposable d) { }
            @Override public void onNext(TransactionStepEvent e) {
                throw new IllegalStateException("a screen's bug");
            }
            @Override public void onError(Throwable e) { }
            @Override public void onComplete() { }
        });
        sale.steps().subscribe(e -> {
            seen.add(e.getStep());
            if (e.getStep().isEnd()) {
                ended.countDown();
            }
        });
        assertTrue(terminal.searching.await(5, TimeUnit.SECONDS));

        terminal.engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        terminal.engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));
        terminal.release.countDown();

        assertTrue("the transaction never ended", ended.await(5, TimeUnit.SECONDS));
        assertEquals(Arrays.asList(TransactionStep.TRANSACTION_STARTED, TransactionStep.ONLINE_REQUIRED,
                TransactionStep.ONLINE_PROCESSING, TransactionStep.ERROR), seen);
    }

    /** prepare() succeeds; the search is held open until released. */
    private static final class TestTerminal extends PosTerminal {

        /** Every transaction's steps, for the test: each transaction has its own stream. */
        final PublishSubject<TransactionStepEvent> allSteps = PublishSubject.create();

        @Override
        public Transaction startTransaction(TransactionConfig config) {
            Transaction transaction = super.startTransaction(config);
            transaction.steps().subscribe(allSteps::onNext);
            return transaction;
        }

        final CountDownLatch searching = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        TestTerminal() {
            super(behavior(), stub(CommunicationBehavior.class), stub(PrinterBehavior.class));
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            searching.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }

        private static EmvBehavior behavior() {
            return (EmvBehavior) Proxy.newProxyInstance(EmvBehavior.class.getClassLoader(),
                    new Class<?>[] {EmvBehavior.class},
                    (proxy, method, args) -> method.getName().equals("prepare") ? Boolean.TRUE
                            : method.getReturnType() == boolean.class ? false : null);
        }

        @SuppressWarnings("unchecked")
        private static <T> T stub(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        }
    }
}

package com.emvenhance.core.terminal;

import com.emvenhance.core.event.TransactionStepEvent;
import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * The terminal owns the host and the printer: a behavior's online request reaches the host
 * through {@code engine.authorize → PosTerminal.authorize}, and receipts go to the terminal's
 * own printer.
 */
public class PosTerminalHostTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private final List<TransactionConfig> sentToHost = new ArrayList<>();
    private final List<List<String>> printed = new CopyOnWriteArrayList<>();
    private final CountDownLatch printDone = new CountDownLatch(1);

    @Test
    public void behaviorsOnlineRequestGoesThroughTheTerminalsHost() {
        TestTerminal terminal = new TestTerminal(config -> {
            sentToHost.add(config);
            return Single.just(AuthResult.approved("123456", "00", null, null));
        });
        List<TransactionStep> steps = new CopyOnWriteArrayList<>();
        terminal.allSteps.subscribe(event -> steps.add(event.getStep()));

        AuthResult result = terminal.authorizeInsideATransaction();

        assertTrue(result.isApproved());
        assertEquals(1, sentToHost.size());
        assertSame(CONFIG, sentToHost.get(0));
        assertEquals(Arrays.asList(TransactionStep.TRANSACTION_STARTED, TransactionStep.ONLINE_REQUIRED,
                TransactionStep.ONLINE_PROCESSING, TransactionStep.ONLINE_COMPLETED),
                steps.subList(0, 4));
    }

    @Test
    public void noHostAnswerIsOnlineFailedNotADecline() {
        TestTerminal terminal = new TestTerminal(
                config -> Single.error(new RuntimeException("timeout")));

        AuthResult result = terminal.engine.authorize(CONFIG);

        assertTrue(result.isFailed());
        assertEquals("timeout", result.getMessage());
    }

    @Test(timeout = 5_000)
    public void aHostThatNeverAnswersIsOnlineFailedAfterTheTimeoutAndTheRequestIsCancelled() {
        AtomicInteger requestsCancelled = new AtomicInteger();
        TestTerminal terminal = new TestTerminal(
                config -> Single.<AuthResult>never().doOnDispose(requestsCancelled::incrementAndGet));
        terminal.onlineTimeoutMs = 200;
        List<TransactionStep> steps = new CopyOnWriteArrayList<>();
        terminal.allSteps.subscribe(event -> steps.add(event.getStep()));

        long started = System.currentTimeMillis();
        AuthResult result = terminal.authorizeInsideATransaction();

        assertTrue(result.isFailed());
        assertEquals("Online failed: the host didn't answer in time", result.getMessage());
        assertTrue("waited " + (System.currentTimeMillis() - started) + " ms",
                System.currentTimeMillis() - started < 2_000);
        assertEquals(1, requestsCancelled.get());
        assertEquals("the host call's last step, before the end",
                TransactionStep.ONLINE_COMPLETED, steps.get(steps.size() - 2));
    }

    @Test
    public void receiptsGoToTheTerminalsPrinter() throws InterruptedException {
        TestTerminal terminal = new TestTerminal(config -> Single.never());

        terminal.printReceipt(Collections.singletonList("APPROVED"));

        assertTrue("never printed", printDone.await(5, TimeUnit.SECONDS));
        assertEquals(Collections.singletonList(Collections.singletonList("APPROVED")), printed);
    }

    @Test
    public void receiptsPrintOneAtATimeInTheOrderAsked() throws InterruptedException {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        List<String> order = new CopyOnWriteArrayList<>();
        List<Thread> threads = new CopyOnWriteArrayList<>();
        CountDownLatch allPrinted = new CountDownLatch(3);
        TestTerminal terminal = new TestTerminal(config -> Single.never(),
                lines -> Completable.fromAction(() -> {
                    maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                    Thread.sleep(50); // a real printer is slow
                    order.add(lines.get(0));
                    threads.add(Thread.currentThread());
                    inFlight.decrementAndGet();
                    allPrinted.countDown();
                }));

        terminal.printReceipt(Collections.singletonList("first"));
        terminal.printReceipt(Collections.singletonList("second"));
        terminal.printReceipt(Collections.singletonList("third"));

        assertTrue("not all printed", allPrinted.await(5, TimeUnit.SECONDS));
        assertEquals("printer driven from two threads at once", 1, maxInFlight.get());
        assertEquals(Arrays.asList("first", "second", "third"), order);
        assertSame("one print thread", threads.get(0), threads.get(1));
        assertSame("one print thread", threads.get(1), threads.get(2));
        assertNotSame("not the caller's thread", Thread.currentThread(), threads.get(0));
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

        long onlineTimeoutMs = 90_000;

        TestTerminal(CommunicationBehavior host) {
            this(host, lines -> Completable.fromAction(() -> {
                printed.add(lines);
                printDone.countDown();
            }));
        }

        private final java.util.concurrent.BlockingQueue<AuthResult> online =
                new java.util.concurrent.LinkedBlockingQueue<>();
        private final CountDownLatch ended = new CountDownLatch(1);

        TestTerminal(CommunicationBehavior host, PrinterBehavior printer) {
            super(preparingBehavior(), host, printer);
            allSteps.filter(e -> e.getStep().isEnd()).subscribe(e -> ended.countDown());
        }

        /** A sale whose "card search" is the vendor going online, as EMV does mid-transaction. */
        AuthResult authorizeInsideATransaction() {
            acceptCard(TransactionType.SALE, 1000);
            try {
                AuthResult result = online.poll(5, TimeUnit.SECONDS);
                assertTrue("the transaction never ended", ended.await(5, TimeUnit.SECONDS));
                return result;
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            online.add(engine.authorize(CONFIG));
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }

        @Override
        protected long onlineTimeoutMs() {
            return onlineTimeoutMs;
        }
    }

    /** prepare() succeeds, so the terminal goes on to "search"; everything else is a no-op. */
    private static EmvBehavior preparingBehavior() {
        return (EmvBehavior) Proxy.newProxyInstance(EmvBehavior.class.getClassLoader(),
                new Class<?>[] {EmvBehavior.class},
                (proxy, method, args) -> method.getName().equals("prepare") ? Boolean.TRUE
                        : method.getReturnType() == boolean.class ? false : null);
    }

    /** Interface stub whose methods do nothing and return a default value. */
    @SuppressWarnings("unchecked")
    private static <T> T stub(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
    }
}

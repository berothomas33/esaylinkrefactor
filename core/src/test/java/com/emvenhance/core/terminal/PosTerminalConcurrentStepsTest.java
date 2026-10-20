package com.emvenhance.core.terminal;

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
 * The terminal's steps subject is serialized: a step reported from a second thread (a vendor
 * SDK callback) while one is being delivered waits for it, and the thread already delivering
 * delivers it next — subscribers never get two {@code onNext} calls at once.
 */
public class PosTerminalConcurrentStepsTest {

    @Test
    public void aStepFromASecondThreadWaitsForTheStepBeingDelivered() throws Exception {
        TestTerminal terminal = new TestTerminal();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch secondReported = new CountDownLatch(1);
        List<String> delivered = new CopyOnWriteArrayList<>();
        terminal.transactionSteps().subscribe(event -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            delivered.add(event.getStep() + " on " + Thread.currentThread().getName());
            if (event.getStep() == TransactionStep.ONLINE_PROCESSING) {
                firstInside.countDown();
                secondReported.await(1, TimeUnit.SECONDS); // still delivering when it lands
            }
            inFlight.decrementAndGet();
        });
        terminal.acceptCard(TransactionType.SALE, 1000);
        assertTrue(terminal.searching.await(5, TimeUnit.SECONDS));

        Thread first = new Thread(() -> terminal.engine.notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING)), "first");
        first.start();
        assertTrue(firstInside.await(5, TimeUnit.SECONDS));
        terminal.engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_COMPLETED));
        secondReported.countDown();
        first.join(5_000);
        terminal.release.countDown();

        assertEquals("two onNext calls at once", 1, maxInFlight.get());
        assertEquals(Arrays.asList("ONLINE_PROCESSING on first", "ONLINE_COMPLETED on first"),
                delivered.subList(0, 2));
    }

    /** prepare() succeeds; the search is held open until released. */
    private static final class TestTerminal extends PosTerminal {

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

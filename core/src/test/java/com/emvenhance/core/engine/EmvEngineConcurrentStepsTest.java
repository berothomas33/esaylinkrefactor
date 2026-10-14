package com.emvenhance.core.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.EmvBehavior;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/** Transaction steps reported from two threads at once still reach subscribers one at a time. */
public class EmvEngineConcurrentStepsTest {

    @Test
    public void aCancelFromTheUiThreadWaitsForTheStepBeingDelivered() throws Exception {
        EmvEngine engine = newEngine();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CountDownLatch firstInside = new CountDownLatch(1);
        CountDownLatch cancelReported = new CountDownLatch(1);
        List<String> delivered = new CopyOnWriteArrayList<>();
        engine.transactionSteps().skip(1).subscribe(event -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            delivered.add(event.getStep() + " on " + Thread.currentThread().getName());
            if (event.getStep() == TransactionStep.ONLINE_PROCESSING) {
                firstInside.countDown();
                cancelReported.await(1, TimeUnit.SECONDS); // still delivering when cancel lands
            }
            inFlight.decrementAndGet();
        });

        Thread transaction = new Thread(() -> engine.notifyTransactionStep(
                TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING)), "transaction");
        transaction.start();
        assertTrue(firstInside.await(5, TimeUnit.SECONDS));
        engine.notifyError("Transaction cancelled"); // what cancel() does, from this thread
        cancelReported.countDown();
        transaction.join(5_000);

        assertEquals("two onNext calls at once", 1, maxInFlight.get());
        assertEquals(Arrays.asList("ONLINE_PROCESSING on transaction", "ERROR on transaction"),
                delivered);
        assertEquals(TransactionStep.ERROR, engine.currentTransactionState().getStep());
    }

    private static EmvEngine newEngine() {
        EmvBehavior behavior = (EmvBehavior) Proxy.newProxyInstance(
                EmvBehavior.class.getClassLoader(), new Class<?>[] {EmvBehavior.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        return new EmvEngine(behavior, config -> AuthResult.failed("no host"));
    }
}

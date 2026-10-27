package com.emvenhance.core.terminal;

import com.emvenhance.core.event.TransactionStepEvent;
import io.reactivex.rxjava3.subjects.PublishSubject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.util.ApduTrace;
import java.lang.reflect.Proxy;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** PosTerminal is the UI's single surface for the trace, and owns its on/off switch. */
public class PosTerminalApduTraceTest {

    @Test
    public void exposesTheEnginesTrace() {
        PosTerminal terminal = new TestTerminal();

        assertSame(terminal.behavior.engine().apduTrace(), terminal.apduTrace());
    }

    @Test
    public void loggingSwitchControlsTheEnginesTrace() {
        PosTerminal terminal = new TestTerminal();
        ApduTrace trace = terminal.apduTrace();
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        assertTrue(terminal.isApduLoggingEnabled());

        terminal.setApduLoggingEnabled(false);
        terminal.behavior.engine().begin();
        trace.note("ICC", "not recorded");

        assertFalse(terminal.isApduLoggingEnabled());
        assertFalse(trace.isEnabled());
        assertEquals("", trace.text());

        terminal.setApduLoggingEnabled(true);
        trace.note("ICC", "recorded");
        assertTrue(trace.text().contains("recorded"));
    }

    @Test
    public void aTransactionsStepsAndItsEndReachTheTrace() throws InterruptedException {
        TestTerminal terminal = new TestTerminal(); // its prepare() fails: the sale ends at once
        ApduTrace trace = terminal.apduTrace();
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        CountDownLatch ended = new CountDownLatch(1);
        terminal.allSteps.filter(e -> e.getStep().isEnd()).subscribe(e -> ended.countDown());

        terminal.acceptCard(TransactionType.SALE, 1000);

        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertTrue("the new trace starts with the start step: " + trace.text(),
                trace.text().contains("--- TRANSACTION STARTED"));
        assertTrue(trace.text(), trace.text().contains("--- ERROR: Terminal initialization failed"));
    }

    private static final class TestTerminal extends PosTerminal {

        /** Every transaction's steps, for the test: each transaction has its own stream. */
        final PublishSubject<TransactionStepEvent> allSteps = PublishSubject.create();

        @Override
        public Transaction startTransaction(TransactionConfig config) {
            Transaction transaction = super.startTransaction(config);
            transaction.steps().subscribe(allSteps::onNext);
            return transaction;
        }

        TestTerminal() {
            super(StubBehavior.of((proxy, method, args) ->
                            method.getReturnType() == boolean.class ? false : null),
                    stub(CommunicationBehavior.class),
                    stub(PrinterBehavior.class));
        }

        @Override
        public CardPresence searchCard(TransactionConfig config, CardSearchListener listener) {
            return null;
        }

        @Override
        protected void cancelCardSearch() {
        }

        /** Interface stub whose methods do nothing and return a default value. */
        @SuppressWarnings("unchecked")
        private static <T> T stub(Class<T> type) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> {
                        Class<?> r = method.getReturnType();
                        if (r == boolean.class) {
                            return false;
                        }
                        if (r.isPrimitive() && r != void.class) {
                            return 0;
                        }
                        return null;
                    });
        }
    }
}

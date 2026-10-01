package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.CardSearchListener;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.host.CommunicationBehavior;
import com.emvenhance.core.host.PrinterBehavior;
import com.emvenhance.core.util.ApduTrace;
import java.lang.reflect.Proxy;
import org.junit.Test;

/** PosTerminal is the UI's single surface for the trace, and owns its on/off switch. */
public class PosTerminalApduTraceTest {

    @Test
    public void exposesTheEnginesTrace() {
        EmvEngine engine = new EmvEngine();
        PosTerminal terminal = new TestTerminal(engine);

        assertSame(engine.apduTrace(), terminal.apduTrace());
    }

    @Test
    public void loggingSwitchControlsTheEnginesTrace() {
        EmvEngine engine = new EmvEngine();
        PosTerminal terminal = new TestTerminal(engine);
        ApduTrace trace = terminal.apduTrace();
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        assertTrue(terminal.isApduLoggingEnabled());

        terminal.setApduLoggingEnabled(false);
        engine.begin();
        trace.note("ICC", "not recorded");

        assertFalse(terminal.isApduLoggingEnabled());
        assertFalse(trace.isEnabled());
        assertEquals("", trace.text());

        terminal.setApduLoggingEnabled(true);
        trace.note("ICC", "recorded");
        assertTrue(trace.text().contains("recorded"));
    }

    private static final class TestTerminal extends PosTerminal {

        TestTerminal(EmvEngine engine) {
            super(engine, stub(EmvBehavior.class), stub(CommunicationBehavior.class),
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

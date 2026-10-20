package com.emvenhance.core.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.EmvBehavior;
import com.emvenhance.core.util.ApduTrace;
import java.lang.reflect.Proxy;
import org.junit.Before;
import org.junit.Test;

/** The engine owns the APDU trace: one per engine, started by begin(), fed by its own steps. */
public class EmvEngineTraceTest {

    private EmvEngine engine;
    private ApduTrace trace;

    @Before
    public void setUp() {
        engine = newEngine();
        trace = engine.apduTrace();
    }

    @Test
    public void ownsOneTracePerEngine() {
        assertNotNull(trace);
        assertSame(trace, engine.apduTrace());
        assertNotSame(trace, newEngine().apduTrace());
    }

    @Test
    public void beginStartsAFreshTrace() {
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        assertTrue(engine.begin());
        trace.note("ICC", "first transaction");
        engine.notifyError("done"); // ends the transaction

        assertTrue(engine.begin());

        assertFalse(trace.text().contains("first transaction"));
        assertTrue(trace.text().startsWith("===== EMV TRANSACTION ====="));
    }

    @Test
    public void recordsKernelAndTransactionSteps() {
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        engine.begin();

        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.TRANSACTION_STARTED));
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe");
        engine.notifyTransactionStep(TransactionStepEvent.builder(TransactionStep.CARD_DETECTED)
                .put(TransactionStepEvent.KEY_MODE, "CONTACTLESS").build());

        String text = trace.text();
        assertTrue(text.contains("--- TRANSACTION STARTED"));
        assertFalse("label repeated as message", text.contains("STARTED: Transaction started"));
        assertTrue(text.contains("### KERNEL STEP 1. Search card (Insert, tap, or swipe)"));
        assertTrue(text.contains("--- CARD DETECTED (CONTACTLESS)"));
    }

    @Test
    public void aStepAfterTheEndIsNotedAsDroppedNotAsAKernelStep() {
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        engine.begin();
        engine.notifyCompleted();

        engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION, "late");

        String text = trace.text();
        assertFalse(text.contains("### KERNEL STEP 16."));
        assertTrue(text.contains(
                "!!! EMV step dropped, the transaction has ended: 16. Transaction completion (late)"));
    }

    @Test
    public void retryKeepsTheFailedAttemptAndMarksIt() {
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        engine.begin();
        trace.note("PICC", "card communication error -2");

        engine.requestRetry(config(), "Card not read — tap again");
        assertNotNull(engine.consumePendingRetry());

        String text = trace.text();
        assertTrue(text.contains("card communication error -2"));
        assertTrue(text.contains("--- RETRY #1"));
        assertTrue(text.indexOf("communication error") < text.indexOf("RETRY #1"));
    }

    @Test
    public void countsRetriesAndHandsThePromptOverOnce() {
        engine.begin();
        assertEquals(0, engine.getRetryCount());

        engine.requestRetry(config(), "Card not read — tap again");
        engine.consumePendingRetry();

        assertEquals(1, engine.getRetryCount());
        assertEquals("Card not read — tap again", engine.consumeRetryPrompt());
        assertNull(engine.consumeRetryPrompt());
        assertNull("no retry pending", engine.consumePendingRetry());
        assertEquals(1, engine.getRetryCount());
    }

    @Test
    public void beginResetsRetryState() {
        engine.begin();
        engine.requestRetry(config(), "Insert the card");
        engine.consumePendingRetry();
        engine.notifyError("done");

        engine.begin();

        assertEquals(0, engine.getRetryCount());
        assertNull(engine.consumeRetryPrompt());
    }

    private static TransactionConfig config() {
        return new TransactionConfig(TransactionType.SALE, 100, EntryMethod.ANY);
    }

    /** An engine whose behavior does nothing and that never reaches a host. */
    private static EmvEngine newEngine() {
        EmvBehavior behavior = (EmvBehavior) Proxy.newProxyInstance(
                EmvBehavior.class.getClassLoader(), new Class<?>[] {EmvBehavior.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        return new EmvEngine(behavior, config -> AuthResult.failed("no host"));
    }
}

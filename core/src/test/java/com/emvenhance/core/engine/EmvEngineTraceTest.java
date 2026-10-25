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
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.util.ApduTrace;
import org.junit.Before;
import org.junit.Test;

/**
 * The engine owns the APDU trace: one per engine, started by begin(), fed by its own steps. A
 * step dropped after the end is the behavior's to note ({@code EmvStepObserverTest}).
 */
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
        engine.begin();
        trace.note("ICC", "first transaction");

        engine.begin();

        assertFalse(trace.text().contains("first transaction"));
        assertTrue(trace.text().startsWith("===== EMV TRANSACTION ====="));
    }

    @Test
    public void recordsKernelSteps() {
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        engine.begin();

        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe");

        assertTrue(trace.text().contains("### KERNEL STEP 1. Search card (Insert, tap, or swipe)"));
    }

    @Test
    public void aRetryNamesTheNextGenerateAcTheFirstAgain() {
        assumeTrue("trace records in debug builds only", trace.isEnabled());
        engine.begin();
        generateAc(); // the failed attempt's 1st GENERATE AC

        engine.requestRetry(config(), "Card not read — tap again");
        assertNotNull(engine.consumePendingRetry());
        generateAc(); // the retry's

        assertFalse(trace.text().contains("2nd GENERATE AC"));
    }

    private void generateAc() {
        trace.command("PICC", new byte[] {(byte) 0x80, (byte) 0xAE, (byte) 0x80, 0x00}, 0, null, 0);
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

        engine.begin();

        assertEquals(0, engine.getRetryCount());
        assertNull(engine.consumeRetryPrompt());
    }

    private static TransactionConfig config() {
        return new TransactionConfig(TransactionType.SALE, 100, EntryMethod.ANY);
    }

    /** An engine that never reaches a host. */
    private static EmvEngine newEngine() {
        return new EmvEngine(config -> AuthResult.failed("no host"));
    }
}

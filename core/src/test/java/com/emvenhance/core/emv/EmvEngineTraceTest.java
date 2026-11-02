package com.emvenhance.core.emv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.host.AuthResult;
import org.junit.Before;
import org.junit.Test;

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

    private static EmvEngine newEngine() {
        return new EmvEngine();
    }
}

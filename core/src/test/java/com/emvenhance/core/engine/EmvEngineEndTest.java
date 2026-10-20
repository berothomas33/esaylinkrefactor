package com.emvenhance.core.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.EmvBehavior;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;

/**
 * The running flag guards transaction steps too: published only between {@link EmvEngine#begin()}
 * and the first ERROR/COMPLETED, so a transaction ends exactly once; the cancel flag is the
 * terminal's, read through {@link EmvEngine#isCancelled()}.
 */
public class EmvEngineEndTest {

    private final AtomicBoolean terminalCancelled = new AtomicBoolean();
    private final List<TransactionStep> steps = new ArrayList<>();
    private EmvEngine engine;

    @Before
    public void setUp() {
        EmvBehavior behavior = (EmvBehavior) Proxy.newProxyInstance(
                EmvBehavior.class.getClassLoader(), new Class<?>[] {EmvBehavior.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        engine = new EmvEngine(behavior, config -> AuthResult.failed("no host"),
                terminalCancelled::get);
        engine.transactionSteps().subscribe(event -> steps.add(event.getStep()));
    }

    @Test
    public void aCancelledTransactionEndsOnceWhateverItsCodeReportsAfter() {
        engine.begin();
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_PROCESSING));

        engine.cancel();
        engine.notifyError("Transaction cancelled"); // a stage noticing the cancel
        engine.notifyApproved("00");                 // the host answer still arriving
        engine.notifyCompleted();

        assertEquals(Arrays.asList(TransactionStep.ONLINE_PROCESSING, TransactionStep.ERROR), steps);
        assertFalse(engine.isRunning());
    }

    @Test
    public void nothingIsPublishedOutsideATransaction() {
        engine.notifyTransactionStep(TransactionStepEvent.of(TransactionStep.ONLINE_REQUIRED));
        engine.cancel();

        assertTrue(steps.isEmpty());

        engine.begin();
        engine.notifyCompleted();
        assertEquals(Collections.singletonList(TransactionStep.COMPLETED), steps);
    }

    @Test
    public void theCancelFlagIsTheTerminals() {
        assertFalse(engine.isCancelled());
        terminalCancelled.set(true);
        assertTrue(engine.isCancelled());
    }
}

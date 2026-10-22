package com.emvenhance.core.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.core.terminal.EmvBehavior;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;

/**
 * The engine owns no transaction state: its transaction steps go to the terminal as they are,
 * it publishes no EMV step once the terminal says the transaction ended, and its cancel flag is
 * the terminal's. Ending a transaction exactly once is {@code PosTerminal}'s job
 * ({@code PosTerminalTransactionLifecycleTest}).
 */
public class EmvEngineEndTest {

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<TransactionStepEvent> handedOver = new ArrayList<>();
    private final List<EmvStep> published = new ArrayList<>();
    private EmvEngine engine;

    @Before
    public void setUp() {
        EmvBehavior behavior = (EmvBehavior) Proxy.newProxyInstance(
                EmvBehavior.class.getClassLoader(), new Class<?>[] {EmvBehavior.class},
                (proxy, method, args) -> method.getReturnType() == boolean.class ? false : null);
        engine = new EmvEngine(behavior, config -> AuthResult.failed("no host"),
                handedOver::add, running::get, cancelled::get);
        engine.emvSteps().map(EmvStepEvent::getStep).subscribe(published::add);
    }

    @Test
    public void transactionStepsGoToTheTerminalAsTheyAre() {
        engine.notifyError("Card search cancelled");
        engine.notifyCompleted();

        assertEquals(2, handedOver.size());
        assertEquals(TransactionStep.ERROR, handedOver.get(0).getStep());
        assertEquals("Card search cancelled", handedOver.get(0).getMessage());
        assertEquals(TransactionStep.COMPLETED, handedOver.get(1).getStep());
    }

    @Test
    public void noEmvStepOnceTheTransactionHasEnded() {
        assertTrue("published", engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"));
        running.set(false);
        assertFalse("dropped", engine.notifyEmvStep(EmvStep.TRANSACTION_COMPLETION, "late"));

        assertEquals(Collections.singletonList(EmvStep.SEARCH_CARD), published);
        assertFalse(engine.isRunning());
    }

    @Test
    public void aStepDroppedAfterACancelSaysSoInTheTrace() {
        engine.apduTrace().setEnabled(true);
        engine.begin();
        cancelled.set(true);
        running.set(false);

        engine.notifyEmvStep(EmvStep.READ_APPLICATION_DATA);

        assertTrue(engine.apduTrace().text(),
                engine.apduTrace().text().contains("EMV step dropped, cancelled: "));
    }

    @Test
    public void theCancelFlagIsTheTerminals() {
        assertFalse(engine.isCancelled());
        cancelled.set(true);
        assertTrue(engine.isCancelled());
    }
}

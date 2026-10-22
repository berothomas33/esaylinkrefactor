package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * {@code dispatchStepMethod} consumes {@link EmvEngine#emvSteps()}: every step published there —
 * by {@link AbstractEmvBehavior#goToStep} or by anyone else — runs its {@code onXxx} method,
 * through the behavior's own observer, on the publishing thread, before the publish returns.
 * Observing lasts one transaction: once it ends, {@code goToStep} publishes and runs nothing more.
 */
public class EmvStepObserverTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private EmvEngine engine;
    private VendorBehavior vendor;
    private TestSession session;

    @Before
    public void setUp() {
        vendor = new VendorBehavior();
        session = new TestSession(vendor, config -> AuthResult.failed("no host"));
        engine = session.engine;
    }

    @Test
    public void everyStepRunsItsMethodOnce() {
        session.begin(CONFIG);
        session.prepare(CONFIG);
        session.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.TERMINAL_INITIALIZATION, EmvStep.APPLICATION_SELECTION,
                EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION), vendor.ran);
    }

    @Test
    public void aStepPublishedByAnyoneRunsItsMethod() {
        List<EmvStep> seen = record(engine);
        session.begin(CONFIG);
        session.prepare(CONFIG);
        vendor.ran.clear();

        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"); // PosTerminal's prompt

        assertEquals(Arrays.asList(EmvStep.SEARCH_CARD), vendor.ran);
        assertTrue(seen.contains(EmvStep.SEARCH_CARD));
    }

    @Test
    public void stepsRunOnThePublishingThreadBeforeStartReturns() {
        Thread caller = Thread.currentThread();
        session.begin(CONFIG);

        session.start(CONFIG, CardPresence.chip());

        assertEquals(3, vendor.ran.size());
        assertEquals(Arrays.asList(caller, caller, caller), vendor.threads);
    }

    @Test
    public void aStepPublishedAfterCancelDoesNotRun() {
        session.begin(CONFIG);
        session.prepare(CONFIG);
        vendor.ran.clear();

        session.cancel();
        vendor.go(EmvStep.SEARCH_CARD);

        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void disposeStopsTheObserver() {
        session.begin(CONFIG);
        session.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        vendor.dispose();
        vendor.go(EmvStep.TRANSACTION_COMPLETION);

        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void subscribersSeeNestedStepsInOrder() {
        List<EmvStep> seen = record(engine);
        session.begin(CONFIG);

        session.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void subscribersSeeNestedStepsInOrderWhenTheyCameAfterAnEarlierTransaction() {
        session.begin(CONFIG);
        session.start(CONFIG, CardPresence.chip());
        session.transaction().publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        List<EmvStep> seen = record(engine); // e.g. a new MainViewModel

        session.begin(CONFIG);
        session.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void cancelledStepsAreNotRun() {
        session.begin(CONFIG);
        session.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        session.cancel();
        boolean ran = vendor.go(EmvStep.TRANSACTION_COMPLETION);

        assertTrue(vendor.ran.isEmpty());
        assertFalse("goToStep says it didn't run, so a vendor releases its reader", ran);
    }

    // ─── The end of a transaction ─────────────────────────────────────────

    @Test
    public void aCompletedTransactionStopsObservingAfterItsLastStepReachedTheUi() {
        vendor.approveOnCompletion = true;
        List<EmvStep> seen = record(engine);
        session.begin(CONFIG);
        session.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps());
        assertEquals(EmvStep.TRANSACTION_COMPLETION, seen.get(seen.size() - 1));

        vendor.ran.clear();
        seen.clear();
        vendor.go(EmvStep.SEARCH_CARD); // after the end

        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty());
        assertTrue("published after the end: " + seen, seen.isEmpty());
    }

    @Test
    public void aThrowingStepEndsTheTransactionStopsObservingAndTheNextOneObservesAgain() {
        session.begin(CONFIG);
        vendor.failOn = EmvStep.READ_APPLICATION_DATA;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA),
                vendor.ran);

        vendor.failOn = null;
        vendor.ran.clear();
        vendor.go(EmvStep.SEARCH_CARD); // after the end
        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty());

        session.begin(CONFIG);
        session.prepare(CONFIG);
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe");
        assertEquals(Arrays.asList(EmvStep.TERMINAL_INITIALIZATION, EmvStep.SEARCH_CARD),
                vendor.ran);
    }

    @Test
    public void aStepFromCodeStillUnwindingAfterTheEndIsNeitherPublishedNorRun() {
        List<EmvStep> seen = record(engine);
        session.begin(CONFIG);
        vendor.errorOn = EmvStep.APPLICATION_SELECTION; // finishError, then carries on to READ

        session.start(CONFIG, CardPresence.chip());

        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), vendor.ran);
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), seen);
        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertFalse(session.transaction().isRunning());
    }

    @Test
    public void anUnknownStepEndsTheTransactionAndNothingIsPublishedOrRunAfterIt() {
        List<EmvStep> seen = record(engine);
        session.begin(CONFIG);
        vendor.afterApplicationSelection = null; // a step with no method to run

        session.start(CONFIG, CardPresence.chip());
        vendor.go(EmvStep.TRANSACTION_COMPLETION); // code still unwinding

        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertEquals("Unknown EMV step: null", session.steps().get(0).getMessage());
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), vendor.ran);
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), seen);
        assertFalse(session.transaction().isRunning());
    }

    @Test
    public void aStepAfterTheEndIsNotedInTheTraceAsDroppedNotAsAKernelStep() {
        assumeTrue("trace records in debug builds only", engine.apduTrace().isEnabled());
        session.begin(CONFIG);
        session.prepare(CONFIG);
        session.transaction().publish(TransactionStepEvent.of(TransactionStep.COMPLETED));

        vendor.go(EmvStep.TRANSACTION_COMPLETION, "late");

        String text = engine.apduTrace().text();
        assertFalse(text.contains("### KERNEL STEP 16."));
        assertTrue(text.contains(
                "!!! EMV step dropped, the transaction has ended: 16. Transaction completion (late)"));
    }

    @Test
    public void aRetryInTheSameTransactionKeepsObserving() {
        session.begin(CONFIG);
        session.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        engine.requestRetry(CONFIG, "Card not read — tap again"); // no ERROR, no COMPLETED
        session.start(engine.consumePendingRetry(), CardPresence.chip()); // as PosTerminal does

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), vendor.ran);
        assertTrue(steps().isEmpty());
    }

    private List<TransactionStep> steps() {
        List<TransactionStep> steps = new ArrayList<>();
        for (TransactionStepEvent event : session.steps()) {
            steps.add(event.getStep());
        }
        return steps;
    }

    private static List<EmvStep> record(EmvEngine engine) {
        List<EmvStep> seen = new ArrayList<>();
        engine.emvSteps().map(EmvStepEvent::getStep).subscribe(seen::add);
        return seen;
    }

    /** Chains APPLICATION_SELECTION → READ_APPLICATION_DATA → TRANSACTION_COMPLETION. */
    private static final class VendorBehavior extends AbstractEmvBehavior {

        final List<EmvStep> ran = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        /** Throws from this step's method. */
        EmvStep failOn;
        /** Ends the transaction with an error in this step's method, then carries on. */
        EmvStep errorOn;
        EmvStep afterApplicationSelection = EmvStep.READ_APPLICATION_DATA;
        boolean approveOnCompletion;

        boolean go(EmvStep step) {
            return goToStep(step);
        }

        boolean go(EmvStep step, String detail) {
            return goToStep(step, detail);
        }

        private void ran(EmvStep step) {
            ran.add(step);
            threads.add(Thread.currentThread());
            if (step == failOn) {
                throw new IllegalStateException("kernel error");
            }
            if (step == errorOn) {
                finishError("kernel error");
            }
        }

        @Override
        public byte[] readIccTag(int tag) {
            return null;
        }

        @Override public void onSearchCard(TransactionConfig c, CardPresence p) {
            ran(EmvStep.SEARCH_CARD);
        }
        @Override public void onTerminalInitialization(TransactionConfig c) {
            ran(EmvStep.TERMINAL_INITIALIZATION);
        }
        @Override public void onApplicationSelection(TransactionConfig c, CardPresence p) {
            ran(EmvStep.APPLICATION_SELECTION);
            goToStep(afterApplicationSelection);
        }
        @Override public void onReadApplicationData(TransactionConfig c, CardPresence p) {
            ran(EmvStep.READ_APPLICATION_DATA);
            goToStep(EmvStep.TRANSACTION_COMPLETION);
        }
        @Override public void onTransactionCompletion(TransactionConfig c, CardPresence p) {
            ran(EmvStep.TRANSACTION_COMPLETION);
            if (approveOnCompletion) {
                finishApproved("APPROVED");
            }
        }
        @Override public void onWaitApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override public void onFinalApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override public void onSetTransactionData(TransactionConfig c, CardPresence p) { }
        @Override public void onOfflineDataAuthentication(TransactionConfig c, CardPresence p) { }
        @Override public void onProcessRestrictions(TransactionConfig c, CardPresence p) { }
        @Override public void onCardholderVerification(TransactionConfig c, CardPresence p) { }
        @Override public void onOfflinePinVerification(TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalRiskManagement(TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalActionAnalysis(TransactionConfig c, CardPresence p) { }
        @Override public void onStartOnlineProcess(TransactionConfig c, CardPresence p) { }
        @Override public void onIssuerAuthentication(TransactionConfig c, CardPresence p) { }
        @Override public void onScriptProcessing(TransactionConfig c, CardPresence p) { }
    }
}

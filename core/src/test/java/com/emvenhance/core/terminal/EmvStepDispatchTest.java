package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.engine.TestSession;
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
 * {@link AbstractEmvBehavior#goToStep} publishes a step on {@link EmvEngine#emvSteps()}, then runs
 * its {@code onXxx} method; {@link AbstractEmvBehavior#announceStep} only publishes it. Once the
 * transaction has ended, nothing more is published or run.
 */
public class EmvStepDispatchTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private final TestSession session = new TestSession();
    private EmvEngine engine;
    private VendorBehavior vendor;
    private final List<TransactionStepEvent> transaction = session.steps;

    @Before
    public void setUp() {
        vendor = new VendorBehavior();
        engine = session.engine(vendor, config -> AuthResult.failed("no host"));
    }

    @Test
    public void enteredStepsRunTheirMethodOnce() {
        session.begin(engine);
        engine.prepare(CONFIG);
        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.TERMINAL_INITIALIZATION, EmvStep.APPLICATION_SELECTION,
                EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION), vendor.ran);
    }

    @Test
    public void announcedStepsReachTheObservableOnly() {
        List<EmvStep> seen = record(engine);
        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        vendor.announce(EmvStep.READ_APPLICATION_DATA);
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"); // PosTerminal's prompt

        assertTrue(vendor.ran.isEmpty());
        assertTrue(seen.contains(EmvStep.READ_APPLICATION_DATA));
        assertTrue(seen.contains(EmvStep.SEARCH_CARD));
    }

    @Test
    public void subscribersSeeNestedStepsInOrder() {
        List<EmvStep> seen = record(engine);
        session.begin(engine);

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void subscribersSeeNestedStepsInOrderWhenTheyCameAfterAnEarlierTransaction() {
        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());
        engine.notifyCompleted();
        List<EmvStep> seen = record(engine); // e.g. a new MainViewModel

        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void cancelledStepsAreNotRun() {
        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        session.cancel();

        assertFalse("goToStep says the step didn't run", vendor.go(EmvStep.TRANSACTION_COMPLETION));
        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void aStepThatRanSaysSo() {
        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        assertTrue(vendor.go(EmvStep.TRANSACTION_COMPLETION));
        assertEquals(Collections.singletonList(EmvStep.TRANSACTION_COMPLETION), vendor.ran);
    }

    // ─── The end of a transaction ─────────────────────────────────────────

    @Test
    public void afterACompletedTransactionNothingIsPublishedOrRun() {
        vendor.approveOnCompletion = true;
        List<EmvStep> seen = record(engine);
        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps());
        assertEquals(EmvStep.TRANSACTION_COMPLETION, seen.get(seen.size() - 1));

        vendor.ran.clear();
        seen.clear();
        vendor.go(EmvStep.TRANSACTION_COMPLETION); // code still unwinding
        vendor.announce(EmvStep.SCRIPT_PROCESSING);

        assertTrue("ran " + vendor.ran, vendor.ran.isEmpty());
        assertTrue("published " + seen, seen.isEmpty());
    }

    @Test
    public void aThrowingStepEndsTheTransactionNothingRunsAfterItAndTheNextOneRuns() {
        session.begin(engine);
        vendor.failOn = EmvStep.READ_APPLICATION_DATA;

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA),
                vendor.ran);

        vendor.failOn = null;
        vendor.ran.clear();
        vendor.go(EmvStep.TRANSACTION_COMPLETION);
        assertTrue("ran " + vendor.ran, vendor.ran.isEmpty());

        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());
        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), vendor.ran);
    }

    @Test
    public void aStepFromCodeStillUnwindingAfterTheEndIsNeitherPublishedNorRun() {
        List<EmvStep> seen = record(engine);
        session.begin(engine);
        vendor.errorOn = EmvStep.APPLICATION_SELECTION; // finishError, then carries on to READ

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), vendor.ran);
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), seen);
        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertFalse(engine.isRunning());
    }

    @Test
    public void anUnknownStepEndsTheTransactionAndNothingIsPublishedOrRunAfterIt() {
        List<EmvStep> seen = record(engine);
        session.begin(engine);
        vendor.afterApplicationSelection = null; // a step with no method to run

        engine.start(CONFIG, CardPresence.chip());
        vendor.go(EmvStep.TRANSACTION_COMPLETION); // code still unwinding
        vendor.announce(null);

        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertEquals("Unknown EMV step: null", transaction.get(0).getMessage());
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), vendor.ran);
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), seen);
        assertFalse(engine.isRunning());
    }

    @Test
    public void aRetryInTheSameTransactionKeepsRunningSteps() {
        session.begin(engine);
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        engine.requestRetry(CONFIG, "Card not read — tap again"); // no ERROR, no COMPLETED
        engine.start(engine.consumePendingRetry(), CardPresence.chip()); // as PosTerminal does

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), vendor.ran);
        assertTrue(steps().isEmpty());
    }

    private List<TransactionStep> steps() {
        List<TransactionStep> steps = new ArrayList<>();
        for (TransactionStepEvent event : transaction) {
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
        /** Throws from this step's method. */
        EmvStep failOn;
        /** Ends the transaction with an error in this step's method, then carries on. */
        EmvStep errorOn;
        EmvStep afterApplicationSelection = EmvStep.READ_APPLICATION_DATA;
        boolean approveOnCompletion;

        boolean go(EmvStep step) {
            return goToStep(step);
        }

        void announce(EmvStep step) {
            announceStep(step);
        }

        private void ran(EmvStep step) {
            ran.add(step);
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

        @Override public void onTerminalInitialization(EmvEngine e, TransactionConfig c) {
            ran(EmvStep.TERMINAL_INITIALIZATION);
        }
        @Override public void onApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) {
            ran(EmvStep.APPLICATION_SELECTION);
            goToStep(afterApplicationSelection);
        }
        @Override public void onReadApplicationData(EmvEngine e, TransactionConfig c, CardPresence p) {
            ran(EmvStep.READ_APPLICATION_DATA);
            goToStep(EmvStep.TRANSACTION_COMPLETION);
        }
        @Override public void onTransactionCompletion(EmvEngine e, TransactionConfig c, CardPresence p) {
            ran(EmvStep.TRANSACTION_COMPLETION);
            if (approveOnCompletion) {
                finishApproved("APPROVED");
            }
        }
        @Override public void onWaitApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onFinalApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onSetTransactionData(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onOfflineDataAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onProcessRestrictions(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onCardholderVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onOfflinePinVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalRiskManagement(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalActionAnalysis(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onStartOnlineProcess(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onIssuerAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onScriptProcessing(EmvEngine e, TransactionConfig c, CardPresence p) { }
    }
}

package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;

/**
 * {@code dispatchStepMethod} consumes {@link EmvEngine#emvSteps()}: every step published there —
 * by {@link AbstractEmvBehavior#goToStep} or by anyone else — runs its {@code onXxx} method,
 * through the behavior's own observer, on the publishing thread, before the publish returns.
 * Observing lasts one transaction: once it ends, nothing more is published or run.
 */
public class EmvStepObserverTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private EmvEngine engine;
    private VendorBehavior vendor;
    private final List<TransactionStepEvent> transaction = new ArrayList<>();

    @Before
    public void setUp() {
        vendor = new VendorBehavior();
        engine = new EmvEngine(vendor, config -> AuthResult.failed("no host"),
                vendor.terminalCancelled::get);
        engine.transactionSteps().subscribe(transaction::add);
    }

    @Test
    public void everyStepRunsItsMethodOnce() {
        engine.begin();
        engine.prepare(CONFIG);
        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.TERMINAL_INITIALIZATION, EmvStep.APPLICATION_SELECTION,
                EmvStep.READ_APPLICATION_DATA, EmvStep.TRANSACTION_COMPLETION), vendor.ran);
    }

    @Test
    public void aStepPublishedByAnyoneRunsItsMethod() {
        List<EmvStep> seen = record(engine);
        engine.begin();
        engine.prepare(CONFIG);
        vendor.ran.clear();

        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"); // PosTerminal's prompt

        assertEquals(Arrays.asList(EmvStep.SEARCH_CARD), vendor.ran);
        assertTrue(seen.contains(EmvStep.SEARCH_CARD));
    }

    @Test
    public void stepsRunOnThePublishingThreadBeforeStartReturns() {
        Thread caller = Thread.currentThread();
        engine.begin();

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(3, vendor.ran.size());
        assertEquals(Arrays.asList(caller, caller, caller), vendor.threads);
    }

    @Test
    public void aStepPublishedAfterCancelDoesNotRun() {
        engine.begin();
        engine.prepare(CONFIG);
        vendor.ran.clear();

        vendor.terminalCancelled.set(true);
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe");

        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void disposeStopsTheObserver() {
        engine.begin();
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        engine.dispose();
        vendor.go(EmvStep.TRANSACTION_COMPLETION);

        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void subscribersSeeNestedStepsInOrder() {
        List<EmvStep> seen = record(engine);
        engine.begin();

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void subscribersSeeNestedStepsInOrderWhenTheyCameAfterAnEarlierTransaction() {
        engine.begin();
        engine.start(CONFIG, CardPresence.chip());
        engine.notifyCompleted();
        List<EmvStep> seen = record(engine); // e.g. a new MainViewModel

        engine.begin();
        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void cancelledStepsAreNotRun() {
        engine.begin();
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        vendor.terminalCancelled.set(true);
        vendor.go(EmvStep.TRANSACTION_COMPLETION);

        assertTrue(vendor.ran.isEmpty());
    }

    // ─── The end of a transaction ─────────────────────────────────────────

    @Test
    public void aCompletedTransactionStopsObservingAfterItsLastStepReachedTheUi() {
        vendor.approveOnCompletion = true;
        List<EmvStep> seen = record(engine);
        engine.begin();
        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps());
        assertEquals(EmvStep.TRANSACTION_COMPLETION, seen.get(seen.size() - 1));

        vendor.ran.clear();
        seen.clear();
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"); // after the end

        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty());
        assertTrue("published after the end: " + seen, seen.isEmpty());
    }

    @Test
    public void aThrowingStepEndsTheTransactionStopsObservingAndTheNextOneObservesAgain() {
        engine.begin();
        vendor.failOn = EmvStep.READ_APPLICATION_DATA;

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA),
                vendor.ran);

        vendor.failOn = null;
        vendor.ran.clear();
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"); // after the end
        assertTrue("the ended transaction's observer ran " + vendor.ran, vendor.ran.isEmpty());

        engine.begin();
        engine.prepare(CONFIG);
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe");
        assertEquals(Arrays.asList(EmvStep.TERMINAL_INITIALIZATION, EmvStep.SEARCH_CARD),
                vendor.ran);
    }

    @Test
    public void aStepFromCodeStillUnwindingAfterTheEndIsNeitherPublishedNorRun() {
        List<EmvStep> seen = record(engine);
        engine.begin();
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
        engine.begin();
        vendor.afterApplicationSelection = null; // a step with no method to run

        engine.start(CONFIG, CardPresence.chip());
        vendor.go(EmvStep.TRANSACTION_COMPLETION); // code still unwinding

        assertEquals(Collections.singletonList(TransactionStep.ERROR), steps());
        assertEquals("Unknown EMV step: null", transaction.get(0).getMessage());
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), vendor.ran);
        assertEquals(Collections.singletonList(EmvStep.APPLICATION_SELECTION), seen);
        assertFalse(engine.isRunning());
    }

    @Test
    public void aRetryInTheSameTransactionKeepsObserving() {
        engine.begin();
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

        /** The terminal's cancel flag (PosTerminal owns it in the app). */
        final AtomicBoolean terminalCancelled = new AtomicBoolean();

        final List<EmvStep> ran = new ArrayList<>();
        final List<Thread> threads = new ArrayList<>();
        /** Throws from this step's method. */
        EmvStep failOn;
        /** Ends the transaction with an error in this step's method, then carries on. */
        EmvStep errorOn;
        EmvStep afterApplicationSelection = EmvStep.READ_APPLICATION_DATA;
        boolean approveOnCompletion;

        void go(EmvStep step) {
            goToStep(step);
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

        @Override public void onSearchCard(EmvEngine e, TransactionConfig c, CardPresence p) {
            ran(EmvStep.SEARCH_CARD);
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

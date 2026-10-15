package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.host.AuthResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * {@code dispatchStepMethod} consumes {@link EmvEngine#emvSteps()}: every step published there —
 * by {@link AbstractEmvBehavior#goToStep} or by anyone else — runs its {@code onXxx} method,
 * through the behavior's own observer, on the publishing thread, before the publish returns.
 */
public class EmvStepObserverTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private EmvEngine engine;
    private VendorBehavior vendor;

    @Before
    public void setUp() {
        vendor = new VendorBehavior();
        engine = new EmvEngine(vendor, config -> AuthResult.failed("no host"));
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
        engine.prepare(CONFIG);
        vendor.ran.clear();

        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe"); // PosTerminal's prompt

        assertEquals(Arrays.asList(EmvStep.SEARCH_CARD), vendor.ran);
        assertTrue(seen.contains(EmvStep.SEARCH_CARD));
    }

    @Test
    public void stepsRunOnThePublishingThreadBeforeStartReturns() {
        Thread caller = Thread.currentThread();

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(3, vendor.ran.size());
        assertEquals(Arrays.asList(caller, caller, caller), vendor.threads);
    }

    @Test
    public void aStepPublishedAfterCancelDoesNotRun() {
        engine.prepare(CONFIG);
        vendor.ran.clear();

        vendor.cancel();
        engine.notifyEmvStep(EmvStep.SEARCH_CARD, "Insert, tap, or swipe");

        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void disposeStopsTheObserver() {
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        engine.dispose();
        vendor.go(EmvStep.TRANSACTION_COMPLETION);

        assertTrue(vendor.ran.isEmpty());
    }

    @Test
    public void subscribersSeeNestedStepsInOrder() {
        List<EmvStep> seen = record(engine);

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void subscribersSeeNestedStepsInOrderWhenTheyCameAfterAnEarlierTransaction() {
        engine.start(CONFIG, CardPresence.chip());
        List<EmvStep> seen = record(engine); // e.g. a new MainViewModel

        engine.start(CONFIG, CardPresence.chip());

        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA,
                EmvStep.TRANSACTION_COMPLETION), seen);
    }

    @Test
    public void aThrowingStepEndsTheTransactionAndKeepsTheObserver() {
        List<TransactionStep> transaction = new ArrayList<>();
        engine.transactionSteps().subscribe(event -> transaction.add(event.getStep()));
        engine.begin();
        vendor.failOn = EmvStep.READ_APPLICATION_DATA;

        engine.start(CONFIG, CardPresence.chip());

        assertTrue(transaction.contains(TransactionStep.ERROR));
        assertEquals(Arrays.asList(EmvStep.APPLICATION_SELECTION, EmvStep.READ_APPLICATION_DATA),
                vendor.ran);

        vendor.failOn = null;
        vendor.ran.clear();
        vendor.go(EmvStep.TRANSACTION_COMPLETION);
        assertEquals(Arrays.asList(EmvStep.TRANSACTION_COMPLETION), vendor.ran);
    }

    @Test
    public void cancelledStepsAreNotRun() {
        engine.start(CONFIG, CardPresence.chip());
        vendor.ran.clear();

        vendor.cancel();
        vendor.go(EmvStep.TRANSACTION_COMPLETION);

        assertTrue(vendor.ran.isEmpty());
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
        EmvStep failOn;

        void go(EmvStep step) {
            goToStep(step);
        }

        private void ran(EmvStep step) {
            ran.add(step);
            threads.add(Thread.currentThread());
            if (step == failOn) {
                throw new IllegalStateException("kernel error");
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
            goToStep(EmvStep.READ_APPLICATION_DATA);
        }
        @Override public void onReadApplicationData(EmvEngine e, TransactionConfig c, CardPresence p) {
            ran(EmvStep.READ_APPLICATION_DATA);
            goToStep(EmvStep.TRANSACTION_COMPLETION);
        }
        @Override public void onTransactionCompletion(EmvEngine e, TransactionConfig c, CardPresence p) {
            ran(EmvStep.TRANSACTION_COMPLETION);
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

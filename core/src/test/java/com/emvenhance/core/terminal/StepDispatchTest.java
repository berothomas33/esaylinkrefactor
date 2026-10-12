package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.event.TransactionStep;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link AbstractEmvBehavior#goToStep} emits on the step observable and its observer runs the
 * step methods: in order, one after another on the calling thread (not nested inside each
 * other), all before {@link AbstractEmvBehavior#start} returns.
 */
public class StepDispatchTest {

    private static final List<EmvStep> CHAIN = Arrays.asList(
            EmvStep.APPLICATION_SELECTION,
            EmvStep.READ_APPLICATION_DATA,
            EmvStep.OFFLINE_DATA_AUTHENTICATION,
            EmvStep.PROCESS_RESTRICTIONS,
            EmvStep.START_ONLINE_PROCESS,
            EmvStep.TRANSACTION_COMPLETION);

    private EmvEngine engine;
    private final List<EmvStep> published = new ArrayList<>();

    @Before
    public void setUp() {
        engine = new EmvEngine();
        engine.emvSteps().map(EmvStepEvent::getStep).subscribe(published::add);
    }

    @Test
    public void chainRunsInOrderAndEndsBeforeStartReturns() {
        ChainBehavior vendor = new ChainBehavior();
        engine.begin();

        vendor.start(engine, sale(), CardPresence.chip());

        assertEquals(CHAIN, vendor.ran);
        assertEquals(CHAIN, published);
        assertEquals(TransactionStep.COMPLETED, engine.currentTransactionState().getStep());
    }

    @Test
    public void stepsRunOneAfterAnotherNotNested() {
        ChainBehavior vendor = new ChainBehavior();
        engine.begin();

        vendor.start(engine, sale(), CardPresence.chip());

        // The first step is delivered directly, the rest by the subject's drain loop, all at
        // one depth: called one after another, the stack would grow by a few frames per step.
        assertEquals("steps after the first all run at the same stack depth",
                1, new HashSet<>(vendor.depths.subList(1, vendor.depths.size())).size());
    }

    @Test
    public void aFailingStepEndsTheTransactionAndTheNextOneStillRuns() {
        ChainBehavior vendor = new ChainBehavior();
        vendor.failAt = EmvStep.OFFLINE_DATA_AUTHENTICATION;
        engine.begin();

        vendor.start(engine, sale(), CardPresence.chip());

        assertEquals(CHAIN.subList(0, 3), vendor.ran);
        assertEquals(TransactionStep.ERROR, engine.currentTransactionState().getStep());
        assertEquals("card removed", engine.currentTransactionState().getMessage());

        vendor.failAt = null;
        vendor.ran.clear();
        engine.begin();
        vendor.start(engine, sale(), CardPresence.chip());

        assertEquals(CHAIN, vendor.ran);
        assertEquals(TransactionStep.COMPLETED, engine.currentTransactionState().getStep());
    }

    @Test
    public void cancelStopsBeforeTheNextStep() {
        ChainBehavior vendor = new ChainBehavior();
        vendor.cancelAt = EmvStep.READ_APPLICATION_DATA;
        engine.begin();

        vendor.start(engine, sale(), CardPresence.chip());

        assertEquals(CHAIN.subList(0, 2), vendor.ran);
        assertEquals(TransactionStep.ERROR, engine.currentTransactionState().getStep());
        assertEquals("Transaction cancelled", engine.currentTransactionState().getMessage());
        assertFalse(engine.isRunning());
    }

    private static TransactionConfig sale() {
        return new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);
    }

    /** Each step records itself and goes to the next one in {@link #CHAIN}. */
    private static final class ChainBehavior extends AbstractEmvBehavior {

        final List<EmvStep> ran = new ArrayList<>();
        final List<Integer> depths = new ArrayList<>();
        EmvStep failAt;
        EmvStep cancelAt;

        private void step(EmvStep step) {
            ran.add(step);
            depths.add(Thread.currentThread().getStackTrace().length);
            if (step == failAt) {
                throw new IllegalStateException("card removed");
            }
            if (step == cancelAt) {
                cancel();
            }
            int next = CHAIN.indexOf(step) + 1;
            if (next < CHAIN.size()) {
                goToStep(CHAIN.get(next));
            } else {
                finishApproved("APPROVED");
            }
        }

        @Override public byte[] readIccTag(int tag) { return null; }
        @Override public void onTerminalInitialization(EmvEngine e, TransactionConfig c) { }
        @Override public void onApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.APPLICATION_SELECTION); }
        @Override public void onWaitApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onFinalApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onReadApplicationData(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.READ_APPLICATION_DATA); }
        @Override public void onSetTransactionData(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onOfflineDataAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.OFFLINE_DATA_AUTHENTICATION); }
        @Override public void onProcessRestrictions(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.PROCESS_RESTRICTIONS); }
        @Override public void onCardholderVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onOfflinePinVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalRiskManagement(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalActionAnalysis(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onStartOnlineProcess(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.START_ONLINE_PROCESS); }
        @Override public void onIssuerAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onScriptProcessing(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTransactionCompletion(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.TRANSACTION_COMPLETION); }
    }
}

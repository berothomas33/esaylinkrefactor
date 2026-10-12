package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link AbstractEmvBehavior} runs each step through its step stream: in order, one at a time, the
 * next step only after the current method returns, all before {@code prepare} / {@code start}
 * return — and a failing step ends the transaction without stopping later ones.
 */
public class StepStreamDispatchTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.ANY);

    private EmvEngine engine;
    private RecordingBehavior vendor;
    private final List<TransactionStepEvent> events = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() {
        engine = new EmvEngine();
        engine.transactionSteps().subscribe(events::add);
        vendor = new RecordingBehavior();
        // Chip chain: APPLICATION_SELECTION → READ → ODA → PROCESS_RESTRICTIONS → COMPLETION.
        vendor.actions.put(EmvStep.APPLICATION_SELECTION,
                () -> vendor.goToStep(EmvStep.READ_APPLICATION_DATA));
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA,
                () -> vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION));
        vendor.actions.put(EmvStep.OFFLINE_DATA_AUTHENTICATION,
                () -> vendor.goToStep(EmvStep.PROCESS_RESTRICTIONS));
        vendor.actions.put(EmvStep.PROCESS_RESTRICTIONS,
                () -> vendor.goToStep(EmvStep.TRANSACTION_COMPLETION));
        vendor.actions.put(EmvStep.TRANSACTION_COMPLETION, () -> vendor.finishApproved("APPROVED"));
    }

    @Test
    public void nextStepRunsAfterTheCurrentMethodReturnsNotInsideIt() {
        startChip();

        assertEquals(Arrays.asList(
                "enter APPLICATION_SELECTION", "exit APPLICATION_SELECTION",
                "enter READ_APPLICATION_DATA", "exit READ_APPLICATION_DATA",
                "enter OFFLINE_DATA_AUTHENTICATION", "exit OFFLINE_DATA_AUTHENTICATION",
                "enter PROCESS_RESTRICTIONS", "exit PROCESS_RESTRICTIONS",
                "enter TRANSACTION_COMPLETION", "exit TRANSACTION_COMPLETION"), vendor.log);
        // Queued, not nested: the stack doesn't grow from one step to the next.
        for (int i = 2; i < vendor.depths.size(); i++) {
            assertEquals("stack depth at step " + i, vendor.depths.get(1), vendor.depths.get(i));
        }
    }

    @Test
    public void startReturnsOnlyOnceEveryStepItLedToHasRun() {
        startChip();

        assertTrue(vendor.log.contains("exit TRANSACTION_COMPLETION"));
        assertNotNull(last(TransactionStep.APPROVED));
        assertNotNull(last(TransactionStep.COMPLETED));
        assertFalse(engine.isRunning());
    }

    @Test
    public void prepareReturnsTheResultOfTerminalInitialization() {
        assertTrue(engine.begin());
        assertTrue(vendor.prepare(engine, CONFIG));
        assertEquals(Arrays.asList("enter TERMINAL_INITIALIZATION", "exit TERMINAL_INITIALIZATION"),
                vendor.log);

        vendor.actions.put(EmvStep.TERMINAL_INITIALIZATION, () -> vendor.finishError("init failed"));
        assertFalse(vendor.prepare(engine, CONFIG));
        assertEquals("init failed", last(TransactionStep.ERROR).getMessage());
    }

    @Test
    public void aThrowingStepEndsTheTransactionAndTheStreamKeepsWorking() {
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA, () -> {
            throw new IllegalStateException("card read failed");
        });

        startChip();

        assertEquals("card read failed", last(TransactionStep.ERROR).getMessage());
        assertFalse(engine.isRunning());

        // Next transaction runs the whole chain: the failure didn't leave the stream stuck.
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA,
                () -> vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION));
        vendor.log.clear();
        startChip();

        assertTrue(vendor.log.contains("exit TRANSACTION_COMPLETION"));
        assertNotNull(last(TransactionStep.COMPLETED));
    }

    @Test
    public void anErrorIsCaughtTooAndReportedWithTheStepTitle() {
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA, () -> {
            throw new StackOverflowError();
        });

        startChip();

        assertEquals(EmvStep.READ_APPLICATION_DATA.getTitle(),
                last(TransactionStep.ERROR).getMessage());

        vendor.actions.put(EmvStep.READ_APPLICATION_DATA,
                () -> vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION));
        startChip();
        assertNotNull(last(TransactionStep.COMPLETED));
    }

    @Test
    public void cancelStopsTheNextStep() {
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA, () -> {
            vendor.cancel();
            vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION);
        });

        startChip();

        assertFalse(vendor.log.contains("enter OFFLINE_DATA_AUTHENTICATION"));
        assertEquals("Transaction cancelled", last(TransactionStep.ERROR).getMessage());
        assertNull(last(TransactionStep.COMPLETED));
    }

    @Test
    public void aStepQueuedBeforeCancelStillRunsSoItCanCleanUp() {
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA, () -> {
            vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION);
            vendor.cancel(); // lands after ODA was queued, before it runs
        });
        vendor.actions.put(EmvStep.OFFLINE_DATA_AUTHENTICATION, () -> {
            if (vendor.isCancelled()) {
                vendor.log.add("cleanup");
                return;
            }
            vendor.goToStep(EmvStep.PROCESS_RESTRICTIONS);
        });

        startChip();

        assertTrue(vendor.log.contains("cleanup"));
        assertFalse(vendor.log.contains("enter PROCESS_RESTRICTIONS"));
    }

    @Test
    public void nothingRunsOnceStartHasReturned() {
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA, () -> vendor.finishApproved("APPROVED"));
        startChip();

        vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION);

        assertFalse(vendor.log.contains("enter OFFLINE_DATA_AUTHENTICATION"));

        // A new start() subscribes again.
        vendor.log.clear();
        startChip();
        assertEquals("enter APPLICATION_SELECTION", vendor.log.get(0));
    }

    @Test
    public void aSecondThreadWaitsForTheRunningChainThenRunsItsOwnStepsOnItsOwnThread()
            throws Exception {
        CountDownLatch inRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        vendor.actions.put(EmvStep.READ_APPLICATION_DATA, () -> {
            inRead.countDown();
            await(release); // e.g. waiting on the host after the UI already cancelled
            vendor.goToStep(EmvStep.OFFLINE_DATA_AUTHENTICATION);
        });
        Thread first = new Thread(this::startChip, "first-transaction");
        first.start();
        assertTrue(inRead.await(5, TimeUnit.SECONDS));

        Thread second = new Thread(() -> vendor.prepare(engine, CONFIG), "second-transaction");
        second.start();
        waitUntilParked(second);
        assertFalse("init must not be queued behind the blocked chain",
                vendor.log.contains("enter TERMINAL_INITIALIZATION"));

        release.countDown();
        first.join(5000);
        second.join(5000);

        int initAt = vendor.log.indexOf("enter TERMINAL_INITIALIZATION");
        assertTrue(initAt > vendor.log.indexOf("exit TRANSACTION_COMPLETION"));
        assertEquals("second-transaction", vendor.threads.get(EmvStep.TERMINAL_INITIALIZATION));
        assertEquals("first-transaction", vendor.threads.get(EmvStep.TRANSACTION_COMPLETION));
    }

    private static void waitUntilParked(Thread thread) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (thread.getState() != Thread.State.WAITING) {
            assertTrue("thread never blocked: " + thread.getState(),
                    thread.isAlive() && System.currentTimeMillis() < deadline);
            Thread.sleep(10);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private void startChip() {
        assertTrue(engine.begin());
        vendor.start(engine, CONFIG, CardPresence.chip());
    }

    private TransactionStepEvent last(TransactionStep step) {
        TransactionStepEvent last = null;
        for (TransactionStepEvent e : events) {
            if (e.getStep() == step) {
                last = e;
            }
        }
        return last;
    }

    /** Logs entering/leaving each step method and its stack depth, then runs that step's action. */
    private static final class RecordingBehavior extends AbstractEmvBehavior {

        final List<String> log = new CopyOnWriteArrayList<>();
        final List<Integer> depths = new CopyOnWriteArrayList<>();
        final Map<EmvStep, String> threads = new ConcurrentHashMap<>();
        final Map<EmvStep, Runnable> actions = new EnumMap<>(EmvStep.class);

        private void step(EmvStep step) {
            log.add("enter " + step);
            depths.add(Thread.currentThread().getStackTrace().length);
            threads.put(step, Thread.currentThread().getName());
            Runnable action = actions.get(step);
            if (action != null) {
                action.run();
            }
            log.add("exit " + step);
        }

        @Override
        public byte[] readIccTag(int tag) {
            return new byte[0];
        }

        @Override public void onTerminalInitialization(EmvEngine e, TransactionConfig c) { step(EmvStep.TERMINAL_INITIALIZATION); }
        @Override public void onApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.APPLICATION_SELECTION); }
        @Override public void onWaitApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.WAIT_APPLICATION_SELECTION); }
        @Override public void onFinalApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.FINAL_APPLICATION_SELECTION); }
        @Override public void onReadApplicationData(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.READ_APPLICATION_DATA); }
        @Override public void onSetTransactionData(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.SET_TRANSACTION_DATA); }
        @Override public void onOfflineDataAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.OFFLINE_DATA_AUTHENTICATION); }
        @Override public void onProcessRestrictions(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.PROCESS_RESTRICTIONS); }
        @Override public void onCardholderVerification(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.CARDHOLDER_VERIFICATION); }
        @Override public void onOfflinePinVerification(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.OFFLINE_PIN_VERIFICATION); }
        @Override public void onTerminalRiskManagement(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.TERMINAL_RISK_MANAGEMENT); }
        @Override public void onTerminalActionAnalysis(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.TERMINAL_ACTION_ANALYSIS); }
        @Override public void onStartOnlineProcess(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.START_ONLINE_PROCESS); }
        @Override public void onIssuerAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.ISSUER_AUTHENTICATION); }
        @Override public void onScriptProcessing(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.SCRIPT_PROCESSING); }
        @Override public void onTransactionCompletion(EmvEngine e, TransactionConfig c, CardPresence p) { step(EmvStep.TRANSACTION_COMPLETION); }
    }
}

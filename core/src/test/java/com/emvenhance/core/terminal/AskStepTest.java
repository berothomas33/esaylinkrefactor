package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.event.EmvStep;
import com.emvenhance.core.event.EmvStepEvent;
import com.emvenhance.core.host.AuthResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link AbstractEmvBehavior#askStep}: a kernel callback waiting on the cardholder publishes a
 * step, that step's method does the UI work and answers, and the answer comes back to the
 * callback. With no answer the kernel still gets one: the fail-closed value.
 */
public class AskStepTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);
    private static final int USER_CANCEL = -100;

    private EmvEngine engine;
    private Vendor vendor;
    private static TestSession session;

    @Before
    public void setUp() {
        vendor = new Vendor();
        session = new TestSession(vendor, config -> AuthResult.failed("no host"));
        engine = session.engine;
        session.begin(CONFIG);
    }

    @Test
    public void theStepMethodsAnswerReachesTheCallback() {
        List<EmvStep> seen = new ArrayList<>();
        engine.emvSteps().map(EmvStepEvent::getStep).subscribe(seen::add);
        vendor.selectionAnswer = 1;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(1, vendor.callbackGot);
        assertEquals(Thread.currentThread(), vendor.answeredOn);
        assertTrue(seen.contains(EmvStep.WAIT_APPLICATION_SELECTION));
    }

    @Test
    public void noAnswerGivesTheFailClosedValue() {
        vendor.selectionAnswer = null;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(USER_CANCEL, vendor.callbackGot);
    }

    @Test
    public void noAnswerIsNotedInTheTrace() {
        assumeTrue("trace records in debug builds only", engine.apduTrace().isEnabled());
        vendor.selectionAnswer = null;

        session.start(CONFIG, CardPresence.chip());

        assertTrue(engine.apduTrace().text().contains("no answer for"));
    }

    @Test
    public void aCancelledTransactionGetsTheFailClosedValue() {
        vendor.selectionAnswer = 1;
        vendor.cancelBeforeAsking = true;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(USER_CANCEL, vendor.callbackGot);
        assertEquals(0, vendor.selectionRuns);
    }

    @Test
    public void aStepMethodThatThrowsGetsTheFailClosedValue() {
        vendor.selectionAnswer = 1;
        vendor.throwInSelection = true;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(USER_CANCEL, vendor.callbackGot);
    }

    @Test
    public void aNestedAskKeepsEachAnswerApart() {
        vendor.selectionAnswer = 1;
        vendor.askFinalInsideSelection = true;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(7, vendor.innerGot);
        assertEquals(1, vendor.callbackGot);
    }

    @Test
    public void anAnswerWithNobodyAskingIsIgnored() {
        vendor.selectionAnswer = 1;
        vendor.goWithoutAsking = true;

        session.start(CONFIG, CardPresence.chip());

        assertEquals(USER_CANCEL, vendor.callbackGot);
    }

    /** APPLICATION_SELECTION plays the kernel calling back for the cardholder's choice. */
    private static final class Vendor extends AbstractEmvBehavior {

        Integer selectionAnswer;
        boolean cancelBeforeAsking;
        boolean throwInSelection;
        boolean askFinalInsideSelection;
        boolean goWithoutAsking;
        int callbackGot = Integer.MIN_VALUE;
        int innerGot = Integer.MIN_VALUE;
        int selectionRuns;
        Thread answeredOn;

        @Override protected void onApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) {
            if (cancelBeforeAsking) {
                session.cancel();
            }
            if (goWithoutAsking) {
                goToStep(EmvStep.WAIT_APPLICATION_SELECTION);
                callbackGot = USER_CANCEL;
                return;
            }
            // The kernel's callback: it waits for this answer.
            callbackGot = askStep(EmvStep.WAIT_APPLICATION_SELECTION, null, USER_CANCEL);
        }

        @Override protected void onWaitApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) {
            selectionRuns++;
            if (throwInSelection) {
                throw new IllegalStateException("dialog failed");
            }
            if (askFinalInsideSelection) {
                innerGot = askStep(EmvStep.FINAL_APPLICATION_SELECTION, null, USER_CANCEL);
            }
            if (selectionAnswer != null) {
                answeredOn = Thread.currentThread();
                answerStep(selectionAnswer);
            }
        }

        @Override protected void onFinalApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) {
            answerStep(7);
        }

        @Override protected byte[] readIccTag(int tag) {
            return null;
        }

        @Override protected void onTerminalInitialization(EmvEngine e, TransactionConfig c) { }
        @Override protected void onReadApplicationData(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onSetTransactionData(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onOfflineDataAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onProcessRestrictions(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onCardholderVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onOfflinePinVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onTerminalRiskManagement(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onTerminalActionAnalysis(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onStartOnlineProcess(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onIssuerAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onScriptProcessing(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override protected void onTransactionCompletion(EmvEngine e, TransactionConfig c, CardPresence p) { }
    }
}

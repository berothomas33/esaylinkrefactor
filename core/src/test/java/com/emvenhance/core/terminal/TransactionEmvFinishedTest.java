package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.event.TransactionStep;
import com.emvenhance.core.event.TransactionStepEvent;
import com.emvenhance.core.host.AuthResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * The EMV reports its outcome ({@code finish*}); the {@link PosTerminal.Transaction} ends the
 * transaction ({@code emvFinished}). The end follows the card's outcome and never turns it into
 * an approval; the outcome and the end can't be split by a cancel; a behavior can't end the
 * transaction by publishing an outcome or an end step itself.
 */
public class TransactionEmvFinishedTest {

    private static final TransactionConfig CONFIG =
            new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);

    private Behavior behavior;
    private TestSession session;

    @Before
    public void setUp() {
        behavior = new Behavior();
        session = new TestSession(behavior, config -> AuthResult.failed("no host"));
        session.begin(CONFIG);
        session.prepare(CONFIG); // the behavior gets its transaction, as PosTerminal hands it
    }

    @Test
    public void approvedThenTheTransactionCompletes() {
        behavior.finishApproved("ok");

        assertEquals(Arrays.asList(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps());
        assertFalse(session.transaction().isRunning());
    }

    @Test
    public void declinedThenTheTransactionCompletes() {
        behavior.finishDeclined("card declined");

        assertEquals(Arrays.asList(TransactionStep.DECLINED, TransactionStep.COMPLETED), steps());
    }

    @Test
    public void anErrorEndsTheTransactionAsItIs() {
        behavior.finishError("Online Failed: Exchange: HTTP 403 Forbidden");

        List<TransactionStepEvent> all = session.steps();
        assertEquals(TransactionStep.ERROR, all.get(all.size() - 1).getStep());
        assertEquals("Online Failed: Exchange: HTTP 403 Forbidden",
                all.get(all.size() - 1).getMessage());
    }

    @Test
    public void aCancelFirstDropsTheOutcome() {
        session.cancel();
        behavior.finishApproved("too late");

        assertEquals("never APPROVED then CANCELLED",
                Arrays.asList(TransactionStep.CANCELLING, TransactionStep.CANCELLED), steps());
    }

    @Test
    public void aCancelAfterTheOutcomeChangesNothing() {
        behavior.finishApproved("ok");
        session.cancel();

        assertEquals(Arrays.asList(TransactionStep.APPROVED, TransactionStep.COMPLETED), steps());
    }

    @Test
    public void onlyTheFirstOutcomeCounts() {
        behavior.finishDeclined("card declined");
        behavior.finishApproved("second outcome");

        assertEquals(Arrays.asList(TransactionStep.DECLINED, TransactionStep.COMPLETED), steps());
    }

    @Test
    public void aBehaviorCantEndTheTransactionByPublishing() {
        behavior.publish(TransactionStepEvent.of(TransactionStep.APPROVED));
        behavior.publish(TransactionStepEvent.of(TransactionStep.COMPLETED));
        behavior.publish(TransactionStepEvent.of(TransactionStep.CARD_READ));

        assertEquals(Arrays.asList(TransactionStep.CARD_READ), steps());
        assertEquals(true, session.transaction().isRunning());
    }

    @Test
    public void aStepThatIsNoOutcomeEndsWithAnError() {
        session.transaction().emvFinished(TransactionStepEvent.of(TransactionStep.CARD_READ));

        List<TransactionStepEvent> all = session.steps();
        assertEquals(TransactionStep.ERROR, all.get(all.size() - 1).getStep());
    }

    /** The transaction's steps without TRANSACTION_STARTED (TestSession never publishes it). */
    private List<TransactionStep> steps() {
        List<TransactionStep> out = new ArrayList<>();
        for (TransactionStepEvent e : session.steps()) {
            out.add(e.getStep());
        }
        return out;
    }

    /** A behavior with no EMV: the test calls its {@code finish*} and {@code publish}. */
    private static final class Behavior extends AbstractEmvBehavior {
        @Override protected byte[] readIccTag(int tag) { return null; }
        @Override protected void onTerminalInitialization(TransactionConfig c) { }
        @Override protected void onApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override protected void onWaitApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override protected void onFinalApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override protected void onReadApplicationData(TransactionConfig c, CardPresence p) { }
        @Override protected void onSetTransactionData(TransactionConfig c, CardPresence p) { }
        @Override protected void onOfflineDataAuthentication(TransactionConfig c, CardPresence p) { }
        @Override protected void onProcessRestrictions(TransactionConfig c, CardPresence p) { }
        @Override protected void onCardholderVerification(TransactionConfig c, CardPresence p) { }
        @Override protected void onOfflinePinVerification(TransactionConfig c, CardPresence p) { }
        @Override protected void onTerminalRiskManagement(TransactionConfig c, CardPresence p) { }
        @Override protected void onTerminalActionAnalysis(TransactionConfig c, CardPresence p) { }
        @Override protected void onStartOnlineProcess(TransactionConfig c, CardPresence p) { }
        @Override protected void onIssuerAuthentication(TransactionConfig c, CardPresence p) { }
        @Override protected void onScriptProcessing(TransactionConfig c, CardPresence p) { }
        @Override protected void onTransactionCompletion(TransactionConfig c, CardPresence p) { }
    }
}

package com.emvenhance.emvflow.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.emvenhance.core.behavior.BehaviorResult;
import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.engine.EmvContext;
import com.emvenhance.core.event.EmvStep;
import org.junit.Before;
import org.junit.Test;

public class DefaultEmvTransitionPolicyTest {

    private DefaultEmvTransitionPolicy policy;
    private EmvContext context;

    @Before
    public void setUp() {
        policy = new DefaultEmvTransitionPolicy();
        context = new EmvContext();
        context.reset(new TransactionConfig("000000", 1000L, TransactionConfig.Mode.CONTACT));
        context.setCard(CardPresence.chip());
    }

    @Test
    public void contactHappyPathAdvancesThroughOnline() {
        assertEquals(EmvStep.SEARCH_CARD,
                policy.next(EmvStep.TERMINAL_INITIALIZATION, BehaviorResult.success(), context));
        assertEquals(EmvStep.APPLICATION_SELECTION,
                policy.next(EmvStep.SEARCH_CARD, BehaviorResult.skip("done"), context));

        context.put(EmvContext.KEY_CANDIDATES,
                java.util.Collections.singletonList("A0000000031010"));
        assertEquals(EmvStep.FINAL_APPLICATION_SELECTION,
                policy.next(EmvStep.APPLICATION_SELECTION, BehaviorResult.success(), context));

        context.put(EmvContext.KEY_ONLINE_REQUIRED, true);
        assertEquals(EmvStep.START_ONLINE_PROCESS,
                policy.next(EmvStep.TERMINAL_ACTION_ANALYSIS, BehaviorResult.success(), context));
        assertNull(policy.next(EmvStep.TRANSACTION_COMPLETION, BehaviorResult.success(), context));
    }

    @Test
    public void contactlessSkipsToCompletionAfterRead() {
        context.setCard(CardPresence.contactless(new byte[] {1}));
        EmvStep next = policy.next(EmvStep.READ_APPLICATION_DATA, BehaviorResult.success(), context);
        assertEquals(EmvStep.TRANSACTION_COMPLETION, next);
        assertEquals(Boolean.TRUE, context.get(EmvContext.KEY_APPROVED));
    }

    @Test
    public void offlinePinRoutesThroughVerification() {
        context.put(EmvContext.KEY_CVM_METHOD, "OFFLINE_PIN");
        assertEquals(EmvStep.OFFLINE_PIN_VERIFICATION,
                policy.next(EmvStep.CARDHOLDER_VERIFICATION, BehaviorResult.success(), context));
    }
}

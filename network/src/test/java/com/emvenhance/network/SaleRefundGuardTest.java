package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.host.AuthResult;
import org.junit.Test;

/**
 * A refund is never sent to the host as a PURCHASE (which would charge the card): it's declined
 * before anything goes on the network. Nulls for every dependency prove nothing else is touched.
 */
public class SaleRefundGuardTest {

    @Test
    public void refundIsDeclinedWithoutGoingOnline() {
        SaleCommunicationBehavior behavior = new SaleCommunicationBehavior(null, null, null, null, "SN");

        AuthResult result = behavior.authorize(
                new TransactionConfig(TransactionType.REFUND, 900, EntryMethod.CONTACTLESS)).blockingGet();

        assertFalse(result.isApproved());
        assertEquals("Refund isn't supported online yet", result.getMessage());
    }
}

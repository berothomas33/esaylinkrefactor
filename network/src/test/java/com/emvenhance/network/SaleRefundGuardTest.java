package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.host.AuthResult;
import com.emvenhance.network.model.RefundRequest;
import com.google.gson.Gson;
import org.junit.Test;

/**
 * Routing by transaction type: what can't go online is online failed (never approved, never sent
 * as another type), and a refund's payload carries the original reference number. Nulls for
 * every network dependency prove the guarded cases touch nothing.
 */
public class SaleRefundGuardTest {

    private final SaleCommunicationBehavior behavior =
            new SaleCommunicationBehavior(null, null, null, null, "SN");

    @Test
    public void refundWithoutReferenceNumberIsOnlineFailed() {
        AuthResult result = behavior.authorize(
                new TransactionConfig(TransactionType.REFUND, 900, EntryMethod.CONTACTLESS)).blockingGet();

        assertFalse(result.isApproved());
        assertTrue(result.isFailed());
        assertEquals("Refund needs the original sale's reference number", result.getMessage());
    }

    @Test
    public void cashInIsOnlineFailedUntilItHasAHostRequest() {
        AuthResult result = behavior.authorize(
                new TransactionConfig(TransactionType.CASH_IN, 900, EntryMethod.CHIP)).blockingGet();

        assertFalse(result.isApproved());
        assertTrue(result.isFailed());
        assertEquals("Cash In isn't supported online yet", result.getMessage());
    }

    @Test
    public void refundPayloadUsesTheOldRefundRequestFields() {
        TransactionConfig config = new TransactionConfig(TransactionType.REFUND, 900, EntryMethod.CONTACTLESS)
                .withReferenceNumber(" 123456789012 ")
                .withIccData("9F2608AABBCCDDEEFF0011");
        EmvTransactionResult emv = new EmvTransactionResult.Builder()
                .pan("4455140000667028")
                .expDate("2806")
                .track2("4455140000667028D28062010000021400000F")
                .hasPin(false)
                .build();

        RefundRequest request = behavior.buildRefundRequest(config, emv);
        String json = new Gson().toJson(request);

        assertEquals("123456789012", request.getReferenceNumber());
        assertEquals(9.0, request.getAmount(), 0.0001);
        assertTrue(json.contains("\"referenceNumber\":\"123456789012\""));
        assertTrue(json.contains("\"pan\":\"4455140000667028\""));
        assertTrue(json.contains("\"expirationMonth\":6"));
        assertTrue(json.contains("\"expirationYear\":28"));
        assertTrue(json.contains("\"trailer\":\"2010000021400000F\""));
        assertTrue(json.contains("\"chipData\":\"9F2608AABBCCDDEEFF0011\""));
        assertFalse("no PIN block for a no-CVM refund", json.contains("pinBlock"));
    }
}

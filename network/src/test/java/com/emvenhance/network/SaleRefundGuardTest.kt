package com.emvenhance.network

import com.emvenhance.core.card.EmvTransactionResult
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Routing by transaction type: what can't go online is online failed (never approved, never sent
 * as another type), and a refund's payload carries the original reference number. The refusal is
 * decided before `authorize` touches the network or the stored onboarding state.
 */
class SaleRefundGuardTest {

    @Test
    fun refundWithoutReferenceNumberIsOnlineFailed() {
        val result = SaleCommunicationBehavior.refusal(
            TransactionConfig(TransactionType.REFUND, 900, EntryMethod.CONTACTLESS),
        )!!

        assertFalse(result.isApproved)
        assertTrue(result.isFailed)
        assertEquals("Refund needs the original sale's reference number", result.message)
    }

    @Test
    fun cashInIsOnlineFailedUntilItHasAHostRequest() {
        val result = SaleCommunicationBehavior.refusal(
            TransactionConfig(TransactionType.CASH_IN, 900, EntryMethod.CHIP),
        )!!

        assertFalse(result.isApproved)
        assertTrue(result.isFailed)
        assertEquals("Cash In isn't supported online yet", result.message)
    }

    @Test
    fun aSaleAndARefundWithItsReferenceCanGoOnline() {
        assertNull(SaleCommunicationBehavior.refusal(TransactionConfig(TransactionType.SALE, 900, EntryMethod.CHIP)))
        assertNull(
            SaleCommunicationBehavior.refusal(
                TransactionConfig(TransactionType.REFUND, 900, EntryMethod.CHIP).withReferenceNumber("123"),
            ),
        )
    }

    @Test
    fun refundPayloadUsesTheOldRefundRequestFields() {
        val config = TransactionConfig(TransactionType.REFUND, 900, EntryMethod.CONTACTLESS)
            .withReferenceNumber(" 123456789012 ")
            .withIccData("9F2608AABBCCDDEEFF0011")
        val emv = EmvTransactionResult.Builder()
            .pan("4455140000667028")
            .expDate("2806")
            .track2("4455140000667028D28062010000021400000F")
            .hasPin(false)
            .build()

        val request = SaleCommunicationBehavior.buildRefundRequest(config, emv)
        val json = Gson().toJson(request)

        assertEquals("123456789012", request.referenceNumber)
        assertEquals(9.0, request.amount, 0.0001)
        assertTrue(json.contains("\"referenceNumber\":\"123456789012\""))
        assertTrue(json.contains("\"pan\":\"4455140000667028\""))
        assertTrue(json.contains("\"expirationMonth\":6"))
        assertTrue(json.contains("\"expirationYear\":28"))
        assertTrue(json.contains("\"trailer\":\"2010000021400000F\""))
        assertTrue(json.contains("\"chipData\":\"9F2608AABBCCDDEEFF0011\""))
        assertFalse("no PIN block for a no-CVM refund", json.contains("pinBlock"))
        assertTrue("wire order kept", json.startsWith("{\"referenceNumber\":\"123456789012\",\"amount\":9.0,\"cvm\":0"))
    }
}

package com.emvenhance.network

import com.emvenhance.core.card.EmvTransactionResult
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.network.model.GeneralRequest
import com.emvenhance.network.model.SaleRequest
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * encPinBlock / pinBlock / pinKey go out only for online PIN. Offline PIN, no CVM and signature
 * leave them out of the JSON entirely. Serialized with a plain `Gson()` — what Retrofit's
 * `GsonConverterFactory.create()` (HostApiClient) uses.
 */
class SalePinBlockTest {

    private val gson = Gson()

    @Test
    fun onlinePinSendsPinBlockAndKey() {
        val config = sale().withOnlinePinBlock(PIN_BLOCK).withOnlinePinKeyEncrypted(PIN_KEY)
        val emv = result(true)

        assertEquals(PIN_BLOCK, SaleCommunicationBehavior.pinBlockToSend(config))
        assertEquals(PIN_KEY, SaleCommunicationBehavior.pinKeyToSend(config))
        assertEquals("ONLINE", SaleCommunicationBehavior.pinEnterMode(config, emv))
        assertEquals(2, SaleCommunicationBehavior.cvmCode(config, emv))
        assertTrue(saleEnvelopeJson(config).contains("\"encPinBlock\":\"$PIN_BLOCK\""))
        assertTrue(saleBodyJson(config, emv).contains("\"pinBlock\":\"$PIN_BLOCK\""))
        assertTrue(exchangeEnvelopeJson(config).contains("\"pinKey\":\"$PIN_KEY\""))
    }

    @Test
    fun offlinePinSendsNoPinBlock() {
        val config = sale()
        val emv = result(true) // PIN verified by the card

        assertEquals("OFFLINE", SaleCommunicationBehavior.pinEnterMode(config, emv))
        // The old app's approved Mastercard offline-PIN chip sale sent "cvm":3.
        assertEquals(3, SaleCommunicationBehavior.cvmCode(config, emv))
        assertTrue(saleBodyJson(config, emv).contains("\"cvm\":3"))
        assertNoPinFields(config, emv)
    }

    @Test
    fun noCvmOrSignatureSendsNoPinBlock() {
        val config = sale()
        val emv = result(false) // no PIN at all: no CVM, or signature

        assertEquals("NO_CVM", SaleCommunicationBehavior.pinEnterMode(config, emv))
        assertNoPinFields(config, emv)
    }

    @Test
    fun emptyPinBlockCountsAsNone() {
        val config = sale().withOnlinePinBlock("  ").withOnlinePinKeyEncrypted(PIN_KEY)
        val emv = result(true)

        assertEquals("OFFLINE", SaleCommunicationBehavior.pinEnterMode(config, emv))
        assertNoPinFields(config, emv)
    }

    @Test
    fun pinKeyWithoutPinBlockIsNotSent() {
        val config = sale().withOnlinePinKeyEncrypted(PIN_KEY) // PIN bypassed

        assertNull(SaleCommunicationBehavior.pinKeyToSend(config))
        assertFalse(exchangeEnvelopeJson(config).contains("pinKey"))
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private fun assertNoPinFields(config: TransactionConfig, emv: EmvTransactionResult) {
        assertNull(SaleCommunicationBehavior.pinBlockToSend(config))
        assertNull(SaleCommunicationBehavior.pinKeyToSend(config))
        assertFalse(saleEnvelopeJson(config).contains("encPinBlock"))
        assertFalse(saleBodyJson(config, emv).contains("pinBlock"))
        assertFalse(exchangeEnvelopeJson(config).contains("pinKey"))
    }

    private fun saleEnvelopeJson(config: TransactionConfig): String =
        gson.toJson(GeneralRequest.forSale("enc", "id", SaleCommunicationBehavior.pinBlockToSend(config)))

    private fun exchangeEnvelopeJson(config: TransactionConfig): String = gson.toJson(
        GeneralRequest.forExchange("enc", "id", "tek", SaleCommunicationBehavior.pinKeyToSend(config), "PURCHASE"),
    )

    private fun saleBodyJson(config: TransactionConfig, emv: EmvTransactionResult): String = gson.toJson(
        SaleRequest(
            10.0, 10.0, SaleCommunicationBehavior.cvmCode(config, emv), "4111111111111111",
            SaleCommunicationBehavior.pinBlockToSend(config), "9F2608", 12, 30, "",
            "051", "CHIP", "false", "{}",
        ),
    )

    private companion object {
        const val PIN_BLOCK = "0123456789ABCDEF0123456789ABCDEF"
        const val PIN_KEY = "RSA-WRAPPED-PEK"

        fun sale() = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP)

        fun result(hasPin: Boolean) = EmvTransactionResult.Builder().hasPin(hasPin).build()
    }
}

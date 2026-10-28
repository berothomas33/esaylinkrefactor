package com.emvenhance.core.terminal

import com.emvenhance.core.card.CardPresence
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.emvenhance.core.engine.EmvEngine
import com.emvenhance.core.host.AuthResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AbstractEmvBehavior.authorizeOnline] builds field 55 from the vendor's tags before every chip
 * or contactless request, and never reaches the host without it.
 */
class AuthorizeOnlineFieldFiftyFiveTest {

    private val sentToHost = ArrayList<TransactionConfig>()

    @Test
    fun chipRequestCarriesTheVendorsFieldFiftyFive() {
        val vendor = VendorBehavior(true)

        val result = vendor.goOnline(sessionFor(vendor), CardPresence.chip())

        assertTrue(result.isApproved)
        assertTrue(vendor.tagsRead > 0)
        assertEquals(1, sentToHost.size)
        assertEquals(FIELD_55, sentToHost[0].iccData)
        assertEquals("extras see field 55", FIELD_55, vendor.iccDataSeenByExtras)
    }

    @Test
    fun contactlessWithoutFieldFiftyFiveIsOnlineFailedAndNeverSent() {
        val vendor = VendorBehavior(false)

        val result = vendor.goOnline(sessionFor(vendor), CardPresence.contactless(null))

        assertFalse(result.isApproved)
        assertTrue(result.isFailed)
        assertEquals("Online failed: no chip data (field 55) to send", result.message)
        assertTrue(sentToHost.isEmpty())
    }

    @Test
    fun magstripeNeverAsksForFieldFiftyFive() {
        val vendor = VendorBehavior(true)

        vendor.goOnline(sessionFor(vendor), CardPresence.magstripe(null, "4111111111111111=2812", null))

        assertEquals(0, vendor.tagsRead)
        assertEquals(1, sentToHost.size)
        assertNull(sentToHost[0].iccData)
    }

    /** A session for `vendor` whose host records each request and approves it. */
    private fun sessionFor(vendor: VendorBehavior) = TestSession(vendor) { config ->
        sentToHost.add(config)
        AuthResult.approved("123456", "00", null, null)
    }

    /** Minimal vendor: every phase is a no-op; its kernel has 9F26 and 9F27, or nothing. */
    private class VendorBehavior(private val hasCryptogram: Boolean) : NoOpStepsBehavior() {
        var tagsRead = 0
        var iccDataSeenByExtras: String? = null

        fun goOnline(session: TestSession, card: CardPresence): AuthResult {
            val config = TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP)
            session.begin(config)
            session.start(config, card)
            return authorizeOnline()
        }

        override fun readIccTag(tag: Int): ByteArray? {
            tagsRead++
            if (!hasCryptogram) {
                return ByteArray(0)
            }
            return when (tag) {
                0x9F26 -> ARQC
                0x9F27 -> byteArrayOf(0x80.toByte())
                else -> null
            }
        }

        override fun addOnlineRequestData(config: TransactionConfig): TransactionConfig {
            iccDataSeenByExtras = config.iccData
            return config
        }
    }

    private companion object {
        val ARQC = byteArrayOf(
            0xF4.toByte(), 0x3D, 0x16, 0x6E, 0xC3.toByte(), 0x61, 0xA9.toByte(), 0xB2.toByte(),
        )
        const val FIELD_55 = "9F2608F43D166EC361A9B29F270180"
    }
}

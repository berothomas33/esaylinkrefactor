package com.emvenhance.core.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tag list's order, BER-TLV encoding and the present/missing summary. */
class Field55Test {

    @Test
    fun presentTagsAreEncodedInListOrderAndMissingOnesLeftOut() {
        val field55 = Field55.read { tag ->
            when (tag) {
                0x9F26 -> byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88.toByte())
                0x5F2A -> byteArrayOf(0x08, 0x18)
                0x82 -> byteArrayOf(0x39, 0x00)
                0x95 -> ByteArray(0)
                else -> null
            }
        }

        assertEquals("5F2A020818" + "82023900" + "9F26081122334455667788", field55.hex())
        val summary = field55.describe("contact")
        assertTrue(summary.startsWith("FIELD 55 (contact, 20 bytes)"))
        assertTrue(summary.contains("\n9F26 Application Cryptogram = 1122334455667788"))
        assertTrue(summary.contains("\nmissing: 5F34 84 95 "))
    }

    @Test
    fun mastercardOfflinePinMatchesTheOldAppByteForByte() {
        val field55 = Field55.read { tag ->
            when (tag) {
                0x5F2A -> hex("0818")
                0x5F34 -> hex("03")
                0x82 -> hex("3000")
                0x84 -> hex("A0000000041010")
                0x95 -> hex("0000008000")
                0x9A -> hex("261013")
                0x9C -> hex("00")
                0x9F02 -> hex("000000012000")
                0x9F03 -> hex("000000000000")
                0x9F09 -> hex("0002")
                0x9F10 -> hex("0110A5000F040000000000000000000000FF")
                0x9F21 -> hex("123720")
                0x9F1A -> hex("0818")
                0x9F26 -> hex("A8E613452342E9A5")
                0x9F27 -> hex("80")
                0x9F33 -> hex("E0F0C8")
                0x9F34 -> hex("410302")
                0x9F35 -> hex("22")
                0x9F36 -> hex("0027")
                0x9F37 -> hex("5D8B4252")
                0x9F41 -> hex("00000000")
                0x9F42 -> hex("0978")
                0x9F1E -> hex("3138353032323437")
                else -> null // 9F53, 9F63, 9F66: none for a Mastercard chip card
            }
        }

        assertEquals(OLD_APP_MASTERCARD_OFFLINE_PIN, field55.hex())
    }

    @Test
    fun longValueUsesTheTwoByteLengthForm() {
        val iad = ByteArray(130)
        val field55 = Field55.read { tag -> if (tag == 0x9F10) iad else null }

        assertTrue(field55.hex().startsWith("9F108182"))
    }

    @Test
    fun noTagsMeansEmpty() {
        val field55 = Field55.read { null }

        assertTrue(field55.isEmpty)
        assertEquals("", field55.hex())
    }

    private companion object {
        /** The old app's field 55 for a Mastercard offline-PIN chip sale, which the host approved. */
        const val OLD_APP_MASTERCARD_OFFLINE_PIN =
            "5F2A0208185F340103820230008407A0000000041010950500000080009A032610139C0100" +
                "9F02060000000120009F03060000000000009F090200029F10120110A5000F04000000000000" +
                "0000000000FF9F21031237209F1A0208189F2608A8E613452342E9A59F2701809F3303E0F0C8" +
                "9F34034103029F3501229F360200279F37045D8B42529F4104000000009F420209789F1E0831" +
                "383530323234379F090200029F3303E0F0C89F34034103029F3501229F410400000000"

        fun hex(hex: String): ByteArray =
            ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }
}

package com.emvenhance.network

import com.emvenhance.core.card.EmvTransactionResult
import com.emvenhance.core.card.EntryMethod
import com.emvenhance.core.card.TransactionConfig
import com.emvenhance.core.card.TransactionType
import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sale request for the old app's approved Mastercard offline-PIN chip sale, built from the
 * same card data, matches what the old app sent field by field (key order aside).
 */
class OldAppSaleRequestTest {

    @Test
    fun mastercardOfflinePinChipSaleMatchesTheOldApp() {
        // The terminal offers every entry method; the card was inserted.
        val config = TransactionConfig(TransactionType.SALE, 12000, EntryMethod.ANY).withIccData(CHIP_DATA)
        val emv = EmvTransactionResult.Builder()
            .pan("5413330089020011")
            .track2("5413330089020011D4912601079360805")
            .expDate("4912")
            .cardHolderName("MTIP06 MCD 25A")
            .chipData(CHIP_DATA)
            .aid("A0000000041010")
            .emvAppLabel("MASTERCARD")
            .emvAppName("MasterCard")
            .issuerName("MasterCard")
            .hasPin(true) // offline PIN, verified by the card
            .entryMethod(EntryMethod.CHIP)
            .build()

        val ours = JsonParser.parseString(
            Gson().toJson(SaleCommunicationBehavior.buildSaleRequest(config, emv)),
        ).asJsonObject
        val old = JsonParser.parseString(OLD_APP_SALE_REQUEST).asJsonObject

        assertEquals(
            JsonParser.parseString(old.remove("extraData").asString),
            JsonParser.parseString(ours.remove("extraData").asString),
        )
        assertEquals(old, ours)
    }

    private companion object {
        const val CHIP_DATA =
            "5F2A0208185F340103820230008407A0000000041010950500000080009A032610139C0100" +
                "9F02060000000120009F03060000000000009F090200029F10120110A5000F04000000000000" +
                "0000000000FF9F21031237209F1A0208189F2608A8E613452342E9A59F2701809F3303E0F0C8" +
                "9F34034103029F3501229F360200279F37045D8B42529F4104000000009F420209789F1E0831" +
                "383530323234379F090200029F3303E0F0C89F34034103029F3501229F410400000000"

        /** The old app's SALE_REQUEST log, 2026-10-13 12:37:31. */
        const val OLD_APP_SALE_REQUEST = "{\"amount\":120.0,\"cardType\":\"MASTERCARD\"," +
            "\"chipData\":\"" + CHIP_DATA + "\",\"cvm\":3,\"dcc\":\"true\",\"expirationMonth\":12," +
            "\"expirationYear\":49,\"extraData\":\"{\\\"aid\\\":\\\"A0000000041010\\\"," +
            "\\\"appName\\\":\\\"MasterCard\\\",\\\"cardHolderName\\\":\\\"MTIP06 MCD 25A\\\"," +
            "\\\"cardType\\\":\\\"CHIP\\\",\\\"issuerName\\\":\\\"MasterCard\\\"," +
            "\\\"pinEnterMode\\\":\\\"OFFLINE\\\"}\",\"netAmount\":0.0," +
            "\"pan\":\"5413330089020011\",\"posEntryMode\":\"051\",\"trailer\":\"601079360805\"}"
    }
}

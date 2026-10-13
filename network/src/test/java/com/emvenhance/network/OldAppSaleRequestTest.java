package com.emvenhance.network;

import static org.junit.Assert.assertEquals;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

/**
 * The sale request for the old app's approved Mastercard offline-PIN chip sale, built from the
 * same card data, matches what the old app sent field by field (key order aside).
 */
public class OldAppSaleRequestTest {

    private static final String CHIP_DATA =
            "5F2A0208185F340103820230008407A0000000041010950500000080009A032610139C0100"
            + "9F02060000000120009F03060000000000009F090200029F10120110A5000F04000000000000"
            + "0000000000FF9F21031237209F1A0208189F2608A8E613452342E9A59F2701809F3303E0F0C8"
            + "9F34034103029F3501229F360200279F37045D8B42529F4104000000009F420209789F1E0831"
            + "383530323234379F090200029F3303E0F0C89F34034103029F3501229F410400000000";

    /** The old app's SALE_REQUEST log, 2026-10-13 12:37:31. */
    private static final String OLD_APP_SALE_REQUEST = "{\"amount\":120.0,\"cardType\":\"MASTERCARD\","
            + "\"chipData\":\"" + CHIP_DATA + "\",\"cvm\":3,\"dcc\":\"true\",\"expirationMonth\":12,"
            + "\"expirationYear\":49,\"extraData\":\"{\\\"aid\\\":\\\"A0000000041010\\\","
            + "\\\"appName\\\":\\\"MasterCard\\\",\\\"cardHolderName\\\":\\\"MTIP06 MCD 25A\\\","
            + "\\\"cardType\\\":\\\"CHIP\\\",\\\"issuerName\\\":\\\"MasterCard\\\","
            + "\\\"pinEnterMode\\\":\\\"OFFLINE\\\"}\",\"netAmount\":0.0,"
            + "\"pan\":\"5413330089020011\",\"posEntryMode\":\"051\",\"trailer\":\"601079360805\"}";

    @Test
    public void mastercardOfflinePinChipSaleMatchesTheOldApp() {
        // The terminal offers every entry method; the card was inserted.
        TransactionConfig config = new TransactionConfig(TransactionType.SALE, 12000, EntryMethod.ANY)
                .withIccData(CHIP_DATA);
        EmvTransactionResult emv = new EmvTransactionResult.Builder()
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
                .build();

        SaleCommunicationBehavior behavior = new SaleCommunicationBehavior(null, null, null, null, "SN");
        JsonObject ours = JsonParser.parseString(
                new Gson().toJson(behavior.buildSaleRequest(config, emv))).getAsJsonObject();
        JsonObject old = JsonParser.parseString(OLD_APP_SALE_REQUEST).getAsJsonObject();

        assertEquals(JsonParser.parseString(old.remove("extraData").getAsString()),
                JsonParser.parseString(ours.remove("extraData").getAsString()));
        assertEquals(old, ours);
    }
}

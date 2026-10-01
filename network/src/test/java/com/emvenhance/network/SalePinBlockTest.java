package com.emvenhance.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.EmvTransactionResult;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.network.model.GeneralRequest;
import com.emvenhance.network.model.SaleRequest;
import com.google.gson.Gson;
import org.junit.Test;

/**
 * encPinBlock / pinBlock / pinKey go out only for online PIN. Offline PIN, no CVM and signature
 * leave them out of the JSON entirely. Serialized with a plain {@code new Gson()} — what Retrofit's
 * {@code GsonConverterFactory.create()} (HostApiClient) uses.
 */
public class SalePinBlockTest {

    private static final String PIN_BLOCK = "0123456789ABCDEF0123456789ABCDEF";
    private static final String PIN_KEY = "RSA-WRAPPED-PEK";

    private final Gson gson = new Gson();

    @Test
    public void onlinePinSendsPinBlockAndKey() {
        TransactionConfig config = sale().withOnlinePinBlock(PIN_BLOCK)
                .withOnlinePinKeyEncrypted(PIN_KEY);
        EmvTransactionResult emv = result(true);

        assertEquals(PIN_BLOCK, SaleCommunicationBehavior.pinBlockToSend(config));
        assertEquals(PIN_KEY, SaleCommunicationBehavior.pinKeyToSend(config));
        assertEquals("ONLINE", SaleCommunicationBehavior.pinEnterMode(config, emv));
        assertTrue(saleEnvelopeJson(config).contains("\"encPinBlock\":\"" + PIN_BLOCK + "\""));
        assertTrue(saleBodyJson(config, emv).contains("\"pinBlock\":\"" + PIN_BLOCK + "\""));
        assertTrue(exchangeEnvelopeJson(config).contains("\"pinKey\":\"" + PIN_KEY + "\""));
    }

    @Test
    public void offlinePinSendsNoPinBlock() {
        TransactionConfig config = sale();
        EmvTransactionResult emv = result(true); // PIN verified by the card

        assertEquals("OFFLINE", SaleCommunicationBehavior.pinEnterMode(config, emv));
        assertNoPinFields(config, emv);
    }

    @Test
    public void noCvmOrSignatureSendsNoPinBlock() {
        TransactionConfig config = sale();
        EmvTransactionResult emv = result(false); // no PIN at all: no CVM, or signature

        assertEquals("NO_CVM", SaleCommunicationBehavior.pinEnterMode(config, emv));
        assertNoPinFields(config, emv);
    }

    @Test
    public void emptyPinBlockCountsAsNone() {
        TransactionConfig config = sale().withOnlinePinBlock("  ").withOnlinePinKeyEncrypted(PIN_KEY);
        EmvTransactionResult emv = result(true);

        assertEquals("OFFLINE", SaleCommunicationBehavior.pinEnterMode(config, emv));
        assertNoPinFields(config, emv);
    }

    @Test
    public void pinKeyWithoutPinBlockIsNotSent() {
        TransactionConfig config = sale().withOnlinePinKeyEncrypted(PIN_KEY); // PIN bypassed

        assertNull(SaleCommunicationBehavior.pinKeyToSend(config));
        assertFalse(exchangeEnvelopeJson(config).contains("pinKey"));
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private void assertNoPinFields(TransactionConfig config, EmvTransactionResult emv) {
        assertNull(SaleCommunicationBehavior.pinBlockToSend(config));
        assertNull(SaleCommunicationBehavior.pinKeyToSend(config));
        assertFalse(saleEnvelopeJson(config).contains("encPinBlock"));
        assertFalse(saleBodyJson(config, emv).contains("pinBlock"));
        assertFalse(exchangeEnvelopeJson(config).contains("pinKey"));
    }

    private String saleEnvelopeJson(TransactionConfig config) {
        return gson.toJson(GeneralRequest.forSale("enc", "id",
                SaleCommunicationBehavior.pinBlockToSend(config)));
    }

    private String exchangeEnvelopeJson(TransactionConfig config) {
        return gson.toJson(GeneralRequest.forExchange("enc", "id", "tek",
                SaleCommunicationBehavior.pinKeyToSend(config), "PURCHASE"));
    }

    private String saleBodyJson(TransactionConfig config, EmvTransactionResult emv) {
        return gson.toJson(new SaleRequest(10.0, 10.0,
                SaleCommunicationBehavior.cvmCode(config, emv), "4111111111111111",
                SaleCommunicationBehavior.pinBlockToSend(config), "9F2608", 12, 30, "",
                "051", "CHIP", "false", "{}"));
    }

    private static TransactionConfig sale() {
        return new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);
    }

    private static EmvTransactionResult result(boolean hasPin) {
        return new EmvTransactionResult.Builder().hasPin(hasPin).build();
    }
}

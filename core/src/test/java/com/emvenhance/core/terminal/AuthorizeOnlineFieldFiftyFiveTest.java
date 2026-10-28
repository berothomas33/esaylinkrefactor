package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.transaction.TransactionConfig;
import com.emvenhance.core.transaction.TransactionType;
import com.emvenhance.core.emv.EmvEngine;
import com.emvenhance.core.host.AuthResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * {@link AbstractEmvBehavior#authorizeOnline} builds field 55 from the vendor's tags before every
 * chip or contactless request, and never reaches the host without it.
 */
public class AuthorizeOnlineFieldFiftyFiveTest {

    private static final byte[] ARQC = {(byte) 0xF4, 0x3D, 0x16, 0x6E, (byte) 0xC3, 0x61, (byte) 0xA9, (byte) 0xB2};
    private static final String FIELD_55 = "9F2608F43D166EC361A9B29F270180";

    private final List<TransactionConfig> sentToHost = new ArrayList<>();

    @Test
    public void chipRequestCarriesTheVendorsFieldFiftyFive() {
        VendorBehavior vendor = new VendorBehavior(true);

        AuthResult result = vendor.goOnline(sessionFor(vendor), CardPresence.chip());

        assertTrue(result.isApproved());
        assertTrue(vendor.tagsRead > 0);
        assertEquals(1, sentToHost.size());
        assertEquals(FIELD_55, sentToHost.get(0).getIccData());
        assertEquals("extras see field 55", FIELD_55, vendor.iccDataSeenByExtras);
    }

    @Test
    public void contactlessWithoutFieldFiftyFiveIsOnlineFailedAndNeverSent() {
        VendorBehavior vendor = new VendorBehavior(false);

        AuthResult result = vendor.goOnline(sessionFor(vendor), CardPresence.contactless(null));

        assertFalse(result.isApproved());
        assertTrue(result.isFailed());
        assertEquals("Online failed: no chip data (field 55) to send", result.getMessage());
        assertTrue(sentToHost.isEmpty());
    }

    @Test
    public void magstripeNeverAsksForFieldFiftyFive() {
        VendorBehavior vendor = new VendorBehavior(true);

        vendor.goOnline(sessionFor(vendor),
                CardPresence.magstripe(null, "4111111111111111=2812", null));

        assertEquals(0, vendor.tagsRead);
        assertEquals(1, sentToHost.size());
        assertNull(sentToHost.get(0).getIccData());
    }

    /** A session for {@code vendor} whose host records each request and approves it. */
    private TestSession sessionFor(VendorBehavior vendor) {
        return new TestSession(vendor, config -> {
            sentToHost.add(config);
            return AuthResult.approved("123456", "00", null, null);
        });
    }

    /** Minimal vendor: every phase is a no-op; its kernel has 9F26 and 9F27, or nothing. */
    private static final class VendorBehavior extends AbstractEmvBehavior {

        private final boolean hasCryptogram;
        int tagsRead;
        String iccDataSeenByExtras;

        VendorBehavior(boolean hasCryptogram) {
            this.hasCryptogram = hasCryptogram;
        }

        AuthResult goOnline(TestSession session, CardPresence card) {
            TransactionConfig config = new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP);
            session.begin(config);
            session.attempt(config, card);
            return authorizeOnline();
        }

        @Override
        public byte[] readIccTag(int tag) {
            tagsRead++;
            if (!hasCryptogram) {
                return new byte[0];
            }
            return tag == 0x9F26 ? ARQC : tag == 0x9F27 ? new byte[] {(byte) 0x80} : null;
        }

        @Override
        protected TransactionConfig addOnlineRequestData(TransactionConfig config) {
            iccDataSeenByExtras = config.getIccData();
            return config;
        }

        @Override public void onTerminalInitialization(TransactionConfig c) { }
        @Override public void onSearchCard(TransactionConfig c, CardPresence p) { }
        @Override public void cancelCardSearch() { }
        @Override public void onApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override public void onWaitApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override public void onFinalApplicationSelection(TransactionConfig c, CardPresence p) { }
        @Override public void onReadApplicationData(TransactionConfig c, CardPresence p) { }
        @Override public void onSetTransactionData(TransactionConfig c, CardPresence p) { }
        @Override public void onOfflineDataAuthentication(TransactionConfig c, CardPresence p) { }
        @Override public void onProcessRestrictions(TransactionConfig c, CardPresence p) { }
        @Override public void onCardholderVerification(TransactionConfig c, CardPresence p) { }
        @Override public void onOfflinePinVerification(TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalRiskManagement(TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalActionAnalysis(TransactionConfig c, CardPresence p) { }
        @Override public void onStartOnlineProcess(TransactionConfig c, CardPresence p) { }
        @Override public void onIssuerAuthentication(TransactionConfig c, CardPresence p) { }
        @Override public void onScriptProcessing(TransactionConfig c, CardPresence p) { }
        @Override public void onTransactionCompletion(TransactionConfig c, CardPresence p) { }
    }
}

package com.emvenhance.core.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.emvenhance.core.card.CardPresence;
import com.emvenhance.core.card.EntryMethod;
import com.emvenhance.core.card.TransactionConfig;
import com.emvenhance.core.card.TransactionType;
import com.emvenhance.core.engine.EmvEngine;
import com.emvenhance.core.host.AuthResult;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * {@link AbstractEmvBehavior#authorizeOnline} asks the vendor for field 55 before every chip or
 * contactless request, and never reaches the host without it.
 */
public class AuthorizeOnlineFieldFiftyFiveTest {

    private static final String FIELD_55 = "9F2608F43D166EC361A9B29F270180";

    private EmvEngine engine;
    private final List<TransactionConfig> sentToHost = new ArrayList<>();

    @Before
    public void setUp() {
        engine = new EmvEngine();
        engine.attachCommunication(config -> {
            sentToHost.add(config);
            return Single.just(AuthResult.approved("123456", "00", null, null));
        });
    }

    @Test
    public void chipRequestCarriesTheVendorsFieldFiftyFive() {
        VendorBehavior vendor = new VendorBehavior(FIELD_55);

        AuthResult result = vendor.goOnline(engine, CardPresence.chip());

        assertTrue(result.isApproved());
        assertEquals(1, vendor.iccDataCalls);
        assertEquals(1, sentToHost.size());
        assertEquals(FIELD_55, sentToHost.get(0).getIccData());
        assertEquals("extras see field 55", FIELD_55, vendor.iccDataSeenByExtras);
    }

    @Test
    public void contactlessWithoutFieldFiftyFiveIsOnlineFailedAndNeverSent() {
        VendorBehavior vendor = new VendorBehavior("");

        AuthResult result = vendor.goOnline(engine, CardPresence.contactless(null));

        assertFalse(result.isApproved());
        assertTrue(result.isFailed());
        assertEquals("Online failed: no chip data (field 55) to send", result.getMessage());
        assertTrue(sentToHost.isEmpty());
    }

    @Test
    public void magstripeNeverAsksForFieldFiftyFive() {
        VendorBehavior vendor = new VendorBehavior(FIELD_55);

        vendor.goOnline(engine, CardPresence.magstripe(null, "4111111111111111=2812", null));

        assertEquals(0, vendor.iccDataCalls);
        assertEquals(1, sentToHost.size());
        assertNull(sentToHost.get(0).getIccData());
    }

    /** Minimal vendor: every phase is a no-op, field 55 is whatever the test hands it. */
    private static final class VendorBehavior extends AbstractEmvBehavior {

        private final String iccData;
        int iccDataCalls;
        String iccDataSeenByExtras;

        VendorBehavior(String iccData) {
            this.iccData = iccData;
        }

        AuthResult goOnline(EmvEngine engine, CardPresence card) {
            start(engine, new TransactionConfig(TransactionType.SALE, 1000, EntryMethod.CHIP), card);
            return authorizeOnline();
        }

        @Override
        public String buildIccData(EmvEngine engine, TransactionConfig config, CardPresence card) {
            iccDataCalls++;
            return iccData;
        }

        @Override
        protected TransactionConfig addOnlineRequestData(TransactionConfig config) {
            iccDataSeenByExtras = config.getIccData();
            return config;
        }

        @Override public void onTerminalInitialization(EmvEngine e, TransactionConfig c) { }
        @Override public void onApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onWaitApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onFinalApplicationSelection(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onReadApplicationData(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onSetTransactionData(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onOfflineDataAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onProcessRestrictions(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onCardholderVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onOfflinePinVerification(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalRiskManagement(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTerminalActionAnalysis(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onStartOnlineProcess(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onIssuerAuthentication(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onScriptProcessing(EmvEngine e, TransactionConfig c, CardPresence p) { }
        @Override public void onTransactionCompletion(EmvEngine e, TransactionConfig c, CardPresence p) { }
    }
}

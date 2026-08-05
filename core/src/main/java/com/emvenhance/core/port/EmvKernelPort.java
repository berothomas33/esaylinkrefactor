package com.emvenhance.core.port;

import androidx.annotation.Nullable;
import com.emvenhance.core.engine.EmvContext;
import java.util.Collections;
import java.util.List;

/**
 * Vendor-neutral L2 kernel operations. PAX / Fake / Ingenico provide adapters.
 */
public interface EmvKernelPort {

    /** Initialize kernel / load AID+CAPK / terminal capabilities. */
    boolean preTrans(EmvContext context);

    /** Build candidate list (PPSE / PSE / directory). Returns AID labels or IDs. */
    List<String> buildCandidates(EmvContext context);

    /** Final select of the chosen AID. */
    boolean finalSelect(EmvContext context, String aid);

    /** Read application records into the context TLV store. */
    boolean readApplicationData(EmvContext context);

    /** Push amount / date / terminal TLVs. */
    boolean setTransactionData(EmvContext context);

    /** SDA / DDA / CDA. Returns auth method label (e.g. "DDA") or null on failure. */
    @Nullable
    String offlineDataAuthentication(EmvContext context);

    /** App version / AUC / effective dates. */
    boolean processRestrictions(EmvContext context);

    /**
     * Process CVM list. Updates context CVM fields. May require PIN via interaction.
     * @return cvm method label: OFFLINE_PIN, ONLINE_PIN, SIGNATURE, NO_CVM, …
     */
    String processCvm(EmvContext context);

    /** Offline PIN verify with PED / card. */
    boolean verifyOfflinePin(EmvContext context);

    /** Floor limits / random / velocity. */
    boolean terminalRiskManagement(EmvContext context);

    /**
     * Offline approve / decline / go online.
     * @return {@code ONLINE}, {@code APPROVE}, or {@code DECLINE}
     */
    String terminalActionAnalysis(EmvContext context);

    /** Generate ARQC / prepare online data. */
    boolean startOnlineProcess(EmvContext context);

    /** Apply ARPC / issuer auth data after host response. */
    boolean issuerAuthentication(EmvContext context);

    /** Process issuer scripts 71/72. */
    boolean processScripts(EmvContext context);

    /** 2nd GENERATE AC / finalize. */
    boolean completeTransaction(EmvContext context);

    /** Abort in-flight kernel session. */
    void abort();

    /** No-op kernel for tests / unused paths. */
    final class NoOp implements EmvKernelPort {
        @Override
        public boolean preTrans(EmvContext context) {
            return true;
        }

        @Override
        public List<String> buildCandidates(EmvContext context) {
            return Collections.singletonList("A0000000031010");
        }

        @Override
        public boolean finalSelect(EmvContext context, String aid) {
            return true;
        }

        @Override
        public boolean readApplicationData(EmvContext context) {
            return true;
        }

        @Override
        public boolean setTransactionData(EmvContext context) {
            return true;
        }

        @Override
        public String offlineDataAuthentication(EmvContext context) {
            return "DDA";
        }

        @Override
        public boolean processRestrictions(EmvContext context) {
            return true;
        }

        @Override
        public String processCvm(EmvContext context) {
            return "OFFLINE_PIN";
        }

        @Override
        public boolean verifyOfflinePin(EmvContext context) {
            return true;
        }

        @Override
        public boolean terminalRiskManagement(EmvContext context) {
            return true;
        }

        @Override
        public String terminalActionAnalysis(EmvContext context) {
            return "ONLINE";
        }

        @Override
        public boolean startOnlineProcess(EmvContext context) {
            return true;
        }

        @Override
        public boolean issuerAuthentication(EmvContext context) {
            return true;
        }

        @Override
        public boolean processScripts(EmvContext context) {
            return true;
        }

        @Override
        public boolean completeTransaction(EmvContext context) {
            return true;
        }

        @Override
        public void abort() {
        }
    }
}

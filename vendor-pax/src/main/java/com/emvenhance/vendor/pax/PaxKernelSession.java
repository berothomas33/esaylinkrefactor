package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;
import java.util.List;

final class PaxKernelSession {

    @Nullable
    TransResult transResult;

    @Nullable
    TransResult clsTransResult;

    @Nullable
    String cachedTrack2Data;

    @Nullable
    String clsCachedTrack2Data;

    @Nullable
    byte[] lastOnlinePinBlock;

    @Nullable
    String lastOnlinePinKeyEncrypted;

    @Nullable
    IssuerRspData pendingScripts;

    @Nullable
    List<CandidateAID> candidates;

    @Nullable
    PinRequest pinRequest;

    /** The outcome's report, run by the TRANSACTION_COMPLETION hook. */
    @Nullable
    Runnable outcome;

    @Nullable
    PaxPinPad activePinPad;

    @Nullable
    String lastPan;

    @Nullable
    String lastTvr;

    @Nullable
    String lastTacDenial;

    @Nullable
    String lastIacDenial;

    @Nullable
    String lastIccData;

    boolean scriptsProcessed;

    static final class PinRequest {
        final boolean online;
        final boolean supportPinBypass;
        final int leftTimes;

        PinRequest(boolean online, boolean supportPinBypass, int leftTimes) {
            this.online = online;
            this.supportPinBypass = supportPinBypass;
            this.leftTimes = leftTimes;
        }
    }
}

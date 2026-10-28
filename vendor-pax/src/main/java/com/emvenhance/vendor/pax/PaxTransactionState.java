package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.host.AuthResult;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.entity.TransResult;
import java.util.List;

final class PaxTransactionState {

    @Nullable
    TransResult transResult;

    @Nullable
    TransResult clsTransResult;

    @Nullable
    String cachedTrack2Data;

    @Nullable
    String clsCachedTrack2Data;

    @Nullable
    AuthResult lastAuth;

    @Nullable
    byte[] lastOnlinePinBlock;

    @Nullable
    String lastOnlinePinKeyEncrypted;

    @Nullable
    OnlineResultWrapper issuerResponse;

    @Nullable
    IssuerRspData pendingScripts;

    @Nullable
    List<CandidateAID> candidates;

    @Nullable
    PinRequest pinRequest;

    @Nullable
    Outcome outcome;

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

    boolean onlineRequested;

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

    static final class Outcome {
        final TransactionStep step;
        final String message;

        Outcome(TransactionStep step, String message) {
            this.step = step;
            this.message = message;
        }
    }
}

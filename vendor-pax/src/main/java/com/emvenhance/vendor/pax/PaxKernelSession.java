package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.TransResult;

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

    /** Mastercard Single Tap: the PIN was asked for once in this attempt — never a second time. */
    boolean singleTapPinEntered;

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

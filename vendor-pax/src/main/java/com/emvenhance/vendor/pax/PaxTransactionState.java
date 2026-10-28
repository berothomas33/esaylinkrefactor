package com.emvenhance.vendor.pax;

import androidx.annotation.Nullable;
import com.emvenhance.core.transaction.TransactionStep;
import com.emvenhance.core.host.AuthResult;
import com.pax.emvbase.process.contact.CandidateAID;
import com.pax.emvbase.process.entity.IssuerRspData;
import com.pax.emvbase.process.entity.OnlineResultWrapper;
import com.pax.emvbase.process.entity.TransResult;
import java.util.List;

/**
 * What {@link PaxEmvBehavior} learns during one attempt (a retry is a new attempt): one object,
 * replaced by a fresh one in {@code PaxEmvBehavior#prepare}, so nothing of a sale is ever read by
 * the next — no field to reset by hand. Its steps write it and read it, all on the transaction
 * thread.
 */
final class PaxTransactionState {

    // ─── The kernel's result ─────────────────────────────────────────────

    /** The contact kernel's result for the stage chain; read by checkContactResult. */
    @Nullable
    TransResult transResult;

    /** The contactless kernel's result for the stage chain; read by checkContactlessResult. */
    @Nullable
    TransResult clsTransResult;

    @Nullable
    String cachedTrack2Data;

    @Nullable
    String clsCachedTrack2Data;

    // ─── Online: the host and the PIN ───────────────────────────────────

    /** The host's answer (mag / manual read it at TRANSACTION_COMPLETION). */
    @Nullable
    AuthResult lastAuth;

    /** The encrypted online PIN block, attached to the authorization in startOnlineProcess. */
    @Nullable
    byte[] lastOnlinePinBlock;

    /** The PIN key (PEK) that encrypted {@link #lastOnlinePinBlock}, RSA-wrapped for the host. */
    @Nullable
    String lastOnlinePinKeyEncrypted;

    /** The host's answer waiting for the card's 2nd GENERATE AC / the 2nd-tap check. */
    @Nullable
    OnlineResultWrapper issuerResponse;

    /** The issuer's scripts waiting for onScriptProcessing (contactless). */
    @Nullable
    IssuerRspData pendingScripts;

    // ─── Waiting for a step: a kernel callback hands these to the step that does the work ──

    /** The kernel's candidate list from onWaitAppSelect, for the application-selection step. */
    @Nullable
    List<CandidateAID> candidates;

    /** What onCardHolderPwd is asking, for the PIN step. */
    @Nullable
    PinRequest pinRequest;

    /** How a chip / contactless transaction ends, for the TRANSACTION_COMPLETION step. */
    @Nullable
    Outcome outcome;

    /** The PIN pad on screen, closed at the stage end if its own callbacks didn't close it. */
    @Nullable
    PaxPinPad activePinPad;

    // ─── The result summary for the APPROVED / DECLINED event ────────────
    // PAN / TVR / TAC / IAC / field 55, captured by captureTransactionSummary while the kernel
    // session is still open. null for mag / manual (no ICC session).

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

    // ─── Flags ───────────────────────────────────────────────────────────
    // Each repeats something another field already says (BACKLOG 28 / 41): onlineRequested =
    // always true where it's read, scriptsProcessed = pendingScripts. Kept for now.

    /** True while startOnlineProcess waits for the host step. */
    boolean onlineRequested;

    /** SCRIPT_PROCESSING already ran in its own step: completeApproved doesn't announce it. */
    boolean scriptsProcessed;

    // ─── The types it holds ──────────────────────────────────────────────

    /** What onCardHolderPwd was asked, for the step method that collects the PIN. */
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

    /** A chip/contactless transaction's end: APPROVED, DECLINED or ERROR, and its message. */
    static final class Outcome {
        final TransactionStep step;
        final String message;

        Outcome(TransactionStep step, String message) {
            this.step = step;
            this.message = message;
        }
    }
}

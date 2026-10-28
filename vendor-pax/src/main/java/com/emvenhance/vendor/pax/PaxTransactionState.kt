package com.emvenhance.vendor.pax

import com.emvenhance.core.event.TransactionStep
import com.emvenhance.core.host.AuthResult
import com.pax.emvbase.process.contact.CandidateAID
import com.pax.emvbase.process.entity.IssuerRspData
import com.pax.emvbase.process.entity.OnlineResultWrapper
import com.pax.emvbase.process.entity.TransResult

/**
 * What [PaxEmvBehavior] learns during one attempt (a retry is a new attempt): one object,
 * replaced by a fresh one in `PaxEmvBehavior.prepare`, so nothing of a sale is ever read by the
 * next — no field to reset by hand. Its steps write it and read it, all on the transaction
 * thread.
 */
internal class PaxTransactionState {

    // ─── The kernel's result ─────────────────────────────────────────────

    /** The contact kernel's result for the stage chain; read by checkContactResult. */
    var transResult: TransResult? = null

    /** The contactless kernel's result for the stage chain; read by checkContactlessResult. */
    var clsTransResult: TransResult? = null

    var cachedTrack2Data: String? = null

    var clsCachedTrack2Data: String? = null

    // ─── Online: the host and the PIN ───────────────────────────────────

    /** The host's answer (mag / manual read it at TRANSACTION_COMPLETION). */
    var lastAuth: AuthResult? = null

    /** The encrypted online PIN block, attached to the authorization in startOnlineProcess. */
    var lastOnlinePinBlock: ByteArray? = null

    /** The PIN key (PEK) that encrypted [lastOnlinePinBlock], RSA-wrapped for the host. */
    var lastOnlinePinKeyEncrypted: String? = null

    /** The host's answer waiting for the card's 2nd GENERATE AC / the 2nd-tap check. */
    var issuerResponse: OnlineResultWrapper? = null

    /** The issuer's scripts waiting for onScriptProcessing (contactless). */
    var pendingScripts: IssuerRspData? = null

    // ─── Waiting for a step: a kernel callback hands these to the step that does the work ──

    /** The kernel's candidate list from onWaitAppSelect, for the application-selection step. */
    var candidates: List<CandidateAID>? = null

    /** What onCardHolderPwd is asking, for the PIN step. */
    var pinRequest: PinRequest? = null

    /** How a chip / contactless transaction ends, for the TRANSACTION_COMPLETION step. */
    var outcome: Outcome? = null

    /** The PIN pad on screen, closed at the stage end if its own callbacks didn't close it. */
    var activePinPad: PaxPinPad? = null

    // ─── The result summary for the APPROVED / DECLINED event ────────────
    // PAN / TVR / TAC / IAC / field 55, captured by captureTransactionSummary while the kernel
    // session is still open. null for mag / manual (no ICC session).

    var lastPan: String? = null

    var lastTvr: String? = null

    var lastTacDenial: String? = null

    var lastIacDenial: String? = null

    var lastIccData: String? = null

    // ─── Flags ───────────────────────────────────────────────────────────
    // Each repeats something another field already says (BACKLOG 28 / 41): initOk = the
    // transaction is still running, onlineRequested = always true where it's read,
    // scriptsProcessed = pendingScripts. Kept for now.

    /** Terminal initialization succeeded (prepareKernel). */
    var initOk = true

    /** True while startOnlineProcess waits for the host step. */
    var onlineRequested = false

    /** SCRIPT_PROCESSING already ran in its own step: completeApproved doesn't announce it. */
    var scriptsProcessed = false

    // ─── The types it holds ──────────────────────────────────────────────

    /** What onCardHolderPwd was asked, for the step method that collects the PIN. */
    class PinRequest(val online: Boolean, val supportPinBypass: Boolean, val leftTimes: Int)

    /** A chip/contactless transaction's end: APPROVED, DECLINED or ERROR, and its message. */
    class Outcome(val step: TransactionStep, val message: String)
}

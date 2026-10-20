package com.emvenhance.network.onboarding

import com.emvenhance.network.HostApiClient
import com.emvenhance.network.model.GeneralResponse
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest
import com.emvenhance.network.onboarding.model.ConfirmOnboardingRequest
import com.emvenhance.network.onboarding.model.CreateTokenRequest
import com.emvenhance.network.onboarding.model.CreateTokenResponse
import com.emvenhance.network.onboarding.model.CreateTokenResult
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest
import com.emvenhance.network.onboarding.model.OnboardingRequest
import com.emvenhance.network.onboarding.model.OnboardingResponse
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse

/**
 * Runs the POS onboarding cycle — mirrors `OnboardingRepoImpl` + `OnboardingUseCase` from the
 * old project's model layer, collapsed into one class the way
 * [com.emvenhance.network.RetrofitCommunicationBehavior] already collapsed its purchase-endpoint
 * equivalent, plus the orchestration `ConfigurationActivity` layered on top (challenge/SACS
 * threading, HMAC signature verification, completion tracking).
 *
 * Two independent three-step cycles:
 * - **Offline** — [handshake] (get the SACS session key) → [onboard] (send a challenge, verify
 *   the signed response) → [confirm] (send back `HMAC-SHA256(challenge, SACS)`). Pairs this
 *   terminal with the host — see [runOfflineCycle] and [HmacSigner].
 * - **Online** — [handshakeOnline] → [onboardOnline] (fetches per-bank TMK blocks) →
 *   [confirmOnline]. Provisions Terminal Master Keys per acquiring bank. **Dormant in the
 *   reference app** — `ConfigurationActivity` has this whole path commented out and its active
 *   state machine skips straight past it; [runOnlineCycle] is kept faithful to what the
 *   commented-out code did; nothing here calls it automatically.
 *
 * Each step is also exposed individually — the old project's ViewModel let its UI drive them
 * one at a time — and [runOfflineCycle]/[runOnlineCycle] chain a whole cycle for a single call
 * site. Every function suspends; a failed step throws ([OnboardingException], or the HTTP/IO
 * error) and the cycle stops there.
 */
class OnboardingClient(private val connection: OnboardingApiConnection) {

    constructor() : this(HostApiClient.createOnboarding())

    // ─── Offline cycle — individual steps ────────────────────────────────

    suspend fun onboard(headers: Map<String, String>, challenge: String): OnboardingResponse =
        connection.onboarding(headers, OnboardingRequest(challenge))

    suspend fun handshake(headers: Map<String, String>): OnboardingStatusResponse =
        connection.onboardingHandshake(headers)

    /** `proof` is `HMAC-SHA256(challenge, SACS)` — see [HmacSigner], not the raw challenge. */
    suspend fun confirm(headers: Map<String, String>, proof: String): OnboardingStatusResponse =
        connection.onboardingConfirm(headers, ConfirmOnboardingRequest(proof))

    /**
     * Exchanges the onboard step's `serviceAccount` for a bearer access token — the fourth step
     * of the offline cycle (`ConfigurationActivity#processAuthentication`/`#createToken`), missing
     * from this class until a real captured onboarding run showed it: `POST
     * authorization/token/pos` with `{"username": serviceAccount}`, same (pre-token) headers as
     * the first three steps.
     *
     * The response also carries a `signature`, meant to be `HMAC-SHA256(accessToken + challenge)`
     * verified against some key — the reference app's `Encryptor.isSignatureVerified(data,
     * signature)` call site shows no key argument at all, so it must be baked into that method;
     * none of this app's now-known fixed header constants (aggregator-app-key/system-app-key/
     * apiKey) or the SACS reproduce the real captured signature (checked against an actual
     * response). Not verified here — treat a 200 status as sufficient until that key is found.
     */
    suspend fun createToken(headers: Map<String, String>, serviceAccount: String): CreateTokenResponse =
        connection.createToken(headers, CreateTokenRequest(serviceAccount))

    /**
     * Runs handshake → onboard → confirm → createToken in sequence, matching
     * `ConfigurationActivity`'s `handShake()` → `callOnboardingOffline()` →
     * `onboardingConfirmation()` → `processAuthentication()`/`createToken()`:
     * 1. [handshake] — its `data` is the SACS session key, saved via [OnboardingState.saveSacs].
     * 2. [onboard] with a fresh [ChallengeGenerator.generate] challenge — the response's
     *    `signature` is HMAC-verified as `publicKey + challenge + serviceAccount` keyed by SACS; a
     *    mismatch fails the cycle without confirming. On success, challenge/publicKey/
     *    serviceAccount are saved.
     * 3. [confirm] with `HMAC-SHA256(challenge, SACS)` as the proof.
     * 4. [createToken] with the saved `serviceAccount` — its `accessToken`/`expiration` are saved
     *    via [OnboardingState.saveAccessToken]/[OnboardingState.saveTokenExpiration], and only
     *    then is the cycle marked complete — a confirmed pairing with no usable token isn't a
     *    complete cycle.
     *
     * On a confirm failure (transport error or non-200), calls [OnboardingState.resetCompletion]
     * — matching `resetOnboarding()`, which only step 3's failure path calls in the reference app,
     * so the next attempt starts over from the handshake. A createToken failure does not reset
     * completion (the reference app's `tokenResponseFailed` handler doesn't either, outside one
     * error code this doesn't model) — a technician can retry onboarding without redoing the
     * pairing.
     */
    suspend fun runOfflineCycle(headers: Map<String, String>, state: OnboardingState): CreateTokenResult {
        val handshakeResponse = handshake(headers).also { requireSuccess(it.statusCode, it.message) }
        val sacs = handshakeResponse.data
            ?: throw OnboardingException(handshakeResponse.statusCode, "Handshake returned no SACS")
        state.saveSacs(sacs)

        val challenge = ChallengeGenerator.generate()
        val onboardingResponse = onboard(headers, challenge).also { requireSuccess(it.statusCode, it.message) }
        val serviceAccount = verifyAndConfirm(headers, challenge, sacs, onboardingResponse, state)

        val tokenResponse = createToken(headers, serviceAccount).also { requireSuccess(it.statusCode, it.message) }
        val data = requireTokenData(tokenResponse)
        state.saveAccessToken(data.accessToken!!)
        data.expiration?.let { state.saveTokenExpiration(it) }
        state.markOfflineOnboarded()
        return data
    }

    /**
     * The saved access token if [OnboardingState.isTokenValid], otherwise a fresh one — ports
     * `ConfigurationActivity`'s `isTokenValid()`-gated call to `processAuthentication()`/
     * `createToken()`: a near-expiry token is refreshed from the already-paired `serviceAccount`
     * alone, not a full re-handshake (the offline cycle's SACS/challenge/publicKey pairing doesn't
     * expire). Every authenticated call site should go through this instead of reading
     * [OnboardingState.getAccessToken] directly, so an expired token never reaches the host.
     *
     * @param headers pre-token headers ([com.emvenhance.network.HostHeaders.build]) — createToken
     *      itself is unauthenticated, same as the rest of the offline cycle.
     */
    suspend fun ensureValidAccessToken(headers: Map<String, String>, state: OnboardingState): String {
        val accessToken = state.getAccessToken()
        if (accessToken != null && state.isTokenValid()) {
            return accessToken
        }
        val serviceAccount = state.getServiceAccount() ?: throw OnboardingException(
            0,
            "No service account on file — full onboarding must complete before a token can be refreshed",
        )
        val response = createToken(headers, serviceAccount).also { requireSuccess(it.statusCode, it.message) }
        val data = requireTokenData(response)
        val fresh = data.accessToken!!
        state.saveAccessToken(fresh)
        data.expiration?.let { state.saveTokenExpiration(it) }
        return fresh
    }

    private fun requireTokenData(response: CreateTokenResponse): CreateTokenResult {
        val data = response.data
        if (data?.accessToken == null) {
            throw OnboardingException(response.statusCode, "createToken response missing accessToken")
        }
        return data
    }

    /** @return the verified onboard response's `serviceAccount`, once confirm succeeds — see [createToken]. */
    private suspend fun verifyAndConfirm(
        headers: Map<String, String>,
        challenge: String,
        sacs: String,
        onboardingResponse: OnboardingResponse,
        state: OnboardingState,
    ): String {
        val data = onboardingResponse.data
        val publicKey = data?.publicKey
        val serviceAccount = data?.serviceAccount
        if (publicKey == null || serviceAccount == null) {
            throw OnboardingException(onboardingResponse.statusCode, "Onboarding response missing identity data")
        }
        val signedMessage = publicKey + challenge + serviceAccount
        if (!HmacSigner.verify(signedMessage, sacs, data.signature)) {
            throw OnboardingException(onboardingResponse.statusCode, "Onboarding signature verification failed")
        }
        state.saveChallenge(challenge)
        state.savePublicKey(publicKey)
        state.saveServiceAccount(serviceAccount)

        val proof = HmacSigner.sign(challenge, sacs)
        try {
            confirm(headers, proof).also { requireSuccess(it.statusCode, it.message) }
        } catch (e: Exception) {
            // Transport error or non-200 — like Rx's doOnError on the confirm chain. A
            // cancellation (the screen went away) is not a failed confirm.
            if (e !is kotlinx.coroutines.CancellationException) {
                state.resetCompletion()
            }
            throw e
        }
        return serviceAccount
    }

    // ─── Online cycle — individual steps ─────────────────────────────────

    suspend fun handshakeOnline(
        headers: Map<String, String>,
        onboardingType: String,
        onboardingKey: String,
    ): OnboardingStatusResponse = connection.handshakeOnboardingOnline(
        headers, HandshakeOnboardingOnlineRequest(onboardingType, onboardingKey),
    )

    /**
     * Fetches the per-bank TMK blocks as an encrypted [GeneralResponse]. The old project's model
     * layer never showed how `data.encSerializedResponse` gets decrypted into
     * `OnboardingOnlineResponse` (that glue lived above the model layer) — this returns the
     * envelope as-is rather than guess at a decryption path for key material.
     */
    suspend fun onboardOnline(headers: Map<String, String>): GeneralResponse =
        connection.onboardingOnline(headers)

    suspend fun confirmOnline(
        headers: Map<String, String>,
        sacs: String,
        bankCodes: String,
    ): OnboardingStatusResponse = connection.confirmOnboardingOnline(
        headers, ConfirmOnboardingOnlineRequest(sacs, bankCodes),
    )

    /**
     * Runs handshakeOnline → onboardOnline → confirmOnline in sequence. `sacs`/`bankCodes`
     * identify which service accounts/banks to confirm receipt for — the caller is expected to
     * derive them from [onboardOnline]'s TMK blocks once those are decrypted (see
     * [onboardOnline]).
     *
     * **Not called by [runOfflineCycle] or anything else here.** In `ConfigurationActivity` this
     * whole path (`onlineOnboardingProcess()` / `callOnlineOnboarding()` / `saveTMKProcess()` /
     * `callConfirmOnlineOnboarding()`) is commented out; its active `onboardingProcess()` state
     * machine goes straight from EMV-param setup to reversing pending transactions. Call this
     * only once TMK provisioning is actually turned back on.
     */
    suspend fun runOnlineCycle(
        headers: Map<String, String>,
        onboardingType: String,
        onboardingKey: String,
        sacs: String,
        bankCodes: String,
    ): OnboardingStatusResponse {
        handshakeOnline(headers, onboardingType, onboardingKey).also { requireSuccess(it.statusCode, it.message) }
        onboardOnline(headers).also { requireSuccess(it.statusCode, it.message) }
        return confirmOnline(headers, sacs, bankCodes).also { requireSuccess(it.statusCode, it.message) }
    }

    companion object {
        /** `statusCode` value every step's response envelope uses for success. */
        const val SUCCESS_STATUS_CODE = 200

        private fun requireSuccess(statusCode: Int, message: String?) {
            if (statusCode != SUCCESS_STATUS_CODE) {
                throw OnboardingException(statusCode, message ?: "Onboarding step failed")
            }
        }
    }
}

package com.emvenhance.network.onboarding;

import com.emvenhance.network.HostApiClient;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingRequest;
import com.emvenhance.network.onboarding.model.CreateTokenRequest;
import com.emvenhance.network.onboarding.model.CreateTokenResponse;
import com.emvenhance.network.onboarding.model.CreateTokenResult;
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.OnboardingRequest;
import com.emvenhance.network.onboarding.model.OnboardingResponse;
import com.emvenhance.network.onboarding.model.OnboardingResult;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;

import java.util.Map;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;

/**
 * Runs the POS onboarding cycle — mirrors {@code OnboardingRepoImpl} + {@code OnboardingUseCase}
 * from the old project's model layer, collapsed into one class the way
 * {@link com.emvenhance.network.RetrofitCommunicationBehavior} already collapsed its
 * purchase-endpoint equivalent, plus the orchestration {@code ConfigurationActivity} layered on
 * top (challenge/SACS threading, HMAC signature verification, completion tracking).
 *
 * <p>Two independent three-step cycles:
 * <ul>
 *   <li><b>Offline</b> — {@link #handshake} (get the SACS session key) → {@link #onboard} (send a
 *       challenge, verify the signed response) → {@link #confirm} (send back
 *       {@code HMAC-SHA256(challenge, SACS)}). Pairs this terminal with the host — see
 *       {@link #runOfflineCycle} and {@link HmacSigner}.
 *   <li><b>Online</b> — {@link #handshakeOnline} → {@link #onboardOnline} (fetches per-bank TMK
 *       blocks) → {@link #confirmOnline}. Provisions Terminal Master Keys per acquiring bank.
 *       <b>Dormant in the reference app</b> — {@code ConfigurationActivity} has this whole path
 *       commented out and its active state machine skips straight past it; {@link #runOnlineCycle}
 *       is kept faithful to what the commented-out code did; nothing here calls it automatically.
 * </ul>
 *
 * <p>Each step is also exposed individually — the old project's ViewModel let its UI drive them
 * one at a time — and {@link #runOfflineCycle}/{@link #runOnlineCycle} chain a whole cycle for a
 * single call site.
 */
public final class OnboardingClient {

    /** {@code statusCode} value every step's response envelope uses for success. */
    public static final int SUCCESS_STATUS_CODE = 200;

    private final OnboardingApiConnection connection;

    public OnboardingClient() {
        this(HostApiClient.createOnboarding());
    }

    public OnboardingClient(OnboardingApiConnection connection) {
        this.connection = connection;
    }

    // ─── Offline cycle — individual steps ────────────────────────────────

    public Single<OnboardingResponse> onboard(Map<String, String> headers, String challenge) {
        return connection.onboarding(headers, new OnboardingRequest(challenge));
    }

    public Single<OnboardingStatusResponse> handshake(Map<String, String> headers) {
        return connection.onboardingHandshake(headers);
    }

    /** {@code proof} is {@code HMAC-SHA256(challenge, SACS)} — see {@link HmacSigner}, not the raw challenge. */
    public Single<OnboardingStatusResponse> confirm(Map<String, String> headers, String proof) {
        return connection.onboardingConfirm(headers, new ConfirmOnboardingRequest(proof));
    }

    /**
     * Exchanges the onboard step's {@code serviceAccount} for a bearer access token — the fourth
     * step of the offline cycle ({@code ConfigurationActivity#processAuthentication}/
     * {@code #createToken}), missing from this class until a real captured onboarding run showed
     * it: {@code POST authorization/token/pos} with {@code {"username": serviceAccount}}, same
     * (pre-token) headers as the first three steps.
     *
     * <p>The response also carries a {@code signature}, meant to be
     * {@code HMAC-SHA256(accessToken + challenge)} verified against some key — the reference app's
     * {@code Encryptor.isSignatureVerified(data, signature)} call site shows no key argument at
     * all, so it must be baked into that method; none of this app's now-known fixed header
     * constants (aggregator-app-key/system-app-key/apiKey) or the SACS reproduce the real captured
     * signature (checked against an actual response). Not verified here — treat a 200 status as
     * sufficient until that key is found.
     */
    public Single<CreateTokenResponse> createToken(Map<String, String> headers, String serviceAccount) {
        return connection.createToken(headers, new CreateTokenRequest(serviceAccount));
    }

    /**
     * Runs handshake → onboard → confirm → createToken in sequence, matching
     * {@code ConfigurationActivity}'s {@code handShake()} → {@code callOnboardingOffline()} →
     * {@code onboardingConfirmation()} → {@code processAuthentication()}/{@code createToken()}:
     * <ol>
     *   <li>{@link #handshake} — its {@code data} is the SACS session key, saved via
     *       {@link OnboardingState#saveSacs}.
     *   <li>{@link #onboard} with a fresh {@link ChallengeGenerator#generate() challenge} — the
     *       response's {@code signature} is HMAC-verified as
     *       {@code publicKey + challenge + serviceAccount} keyed by SACS; a mismatch fails the
     *       cycle without confirming. On success, challenge/publicKey/serviceAccount are saved.
     *   <li>{@link #confirm} with {@code HMAC-SHA256(challenge, SACS)} as the proof.
     *   <li>{@link #createToken} with the saved {@code serviceAccount} — its {@code accessToken}/
     *       {@code expiration} are saved via {@link OnboardingState#saveAccessToken}/
     *       {@link OnboardingState#saveTokenExpiration}, and only then is the cycle marked
     *       complete — a confirmed pairing with no usable token isn't a complete cycle.
     * </ol>
     * On a confirm failure (transport error or non-200), calls
     * {@link OnboardingState#resetCompletion()} — matching {@code resetOnboarding()}, which only
     * step 3's failure path calls in the reference app, so the next attempt starts over from the
     * handshake. A createToken failure does not reset completion (the reference app's
     * {@code tokenResponseFailed} handler doesn't either, outside one error code this doesn't
     * model) — a technician can retry onboarding without redoing the pairing.
     */
    public Single<CreateTokenResult> runOfflineCycle(Map<String, String> headers, OnboardingState state) {
        return handshake(headers)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(handshakeResponse -> {
                    String sacs = handshakeResponse.getData();
                    if (sacs == null) {
                        return Single.error(new OnboardingException(
                                handshakeResponse.getStatusCode(), "Handshake returned no SACS"));
                    }
                    state.saveSacs(sacs);

                    String challenge = ChallengeGenerator.generate();
                    return onboard(headers, challenge)
                            .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                            .flatMap(onboardingResponse ->
                                    verifyAndConfirm(headers, challenge, sacs, onboardingResponse, state));
                })
                .flatMap(serviceAccount -> createToken(headers, serviceAccount)
                        .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                        .flatMap(this::requireTokenData)
                        .doOnSuccess(data -> {
                            state.saveAccessToken(data.getAccessToken());
                            if (data.getExpiration() != null) {
                                state.saveTokenExpiration(data.getExpiration());
                            }
                            state.markOfflineOnboarded();
                        }));
    }

    private Single<CreateTokenResult> requireTokenData(CreateTokenResponse response) {
        CreateTokenResult data = response.getData();
        if (data == null || data.getAccessToken() == null) {
            return Single.error(new OnboardingException(response.getStatusCode(),
                    "createToken response missing accessToken"));
        }
        return Single.just(data);
    }

    /** @return the verified onboard response's {@code serviceAccount}, once confirm succeeds — see {@link #createToken}. */
    private Single<String> verifyAndConfirm(Map<String, String> headers,
            String challenge, String sacs, OnboardingResponse onboardingResponse,
            OnboardingState state) {
        OnboardingResult data = onboardingResponse.getData();
        if (data == null || data.getPublicKey() == null || data.getServiceAccount() == null) {
            return Single.error(new OnboardingException(onboardingResponse.getStatusCode(),
                    "Onboarding response missing identity data"));
        }
        String signedMessage = data.getPublicKey() + challenge + data.getServiceAccount();
        if (!HmacSigner.verify(signedMessage, sacs, data.getSignature())) {
            return Single.error(new OnboardingException(onboardingResponse.getStatusCode(),
                    "Onboarding signature verification failed"));
        }
        state.saveChallenge(challenge);
        state.savePublicKey(data.getPublicKey());
        state.saveServiceAccount(data.getServiceAccount());

        String proof = HmacSigner.sign(challenge, sacs);
        String serviceAccount = data.getServiceAccount();
        return confirm(headers, proof)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .map(r -> serviceAccount)
                .doOnError(e -> state.resetCompletion());
    }

    // ─── Online cycle — individual steps ─────────────────────────────────

    public Single<OnboardingStatusResponse> handshakeOnline(Map<String, String> headers,
            String onboardingType, String onboardingKey) {
        return connection.handshakeOnboardingOnline(headers,
                new HandshakeOnboardingOnlineRequest(onboardingType, onboardingKey));
    }

    /**
     * Fetches the per-bank TMK blocks as an encrypted {@link GeneralResponse}. The old project's
     * model layer never showed how {@code data.encSerializedResponse} gets decrypted into
     * {@code OnboardingOnlineResponse} (that glue lived above the model layer) — this returns the
     * envelope as-is rather than guess at a decryption path for key material.
     */
    public Single<GeneralResponse> onboardOnline(Map<String, String> headers) {
        return connection.onboardingOnline(headers);
    }

    public Single<OnboardingStatusResponse> confirmOnline(Map<String, String> headers,
            String sacs, String bankCodes) {
        return connection.confirmOnboardingOnline(headers,
                new ConfirmOnboardingOnlineRequest(sacs, bankCodes));
    }

    /**
     * Runs handshakeOnline → onboardOnline → confirmOnline in sequence. {@code sacs}/
     * {@code bankCodes} identify which service accounts/banks to confirm receipt for — the
     * caller is expected to derive them from {@link #onboardOnline}'s TMK blocks once those are
     * decrypted (see {@link #onboardOnline}).
     *
     * <p><b>Not called by {@link #runOfflineCycle} or anything else here.</b> In
     * {@code ConfigurationActivity} this whole path ({@code onlineOnboardingProcess()} /
     * {@code callOnlineOnboarding()} / {@code saveTMKProcess()} /
     * {@code callConfirmOnlineOnboarding()}) is commented out; its active
     * {@code onboardingProcess()} state machine goes straight from EMV-param setup to reversing
     * pending transactions. Call this only once TMK provisioning is actually turned back on.
     */
    public Single<OnboardingStatusResponse> runOnlineCycle(Map<String, String> headers,
            String onboardingType, String onboardingKey, String sacs, String bankCodes) {
        return handshakeOnline(headers, onboardingType, onboardingKey)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(r -> onboardOnline(headers))
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(r -> confirmOnline(headers, sacs, bankCodes))
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()));
    }

    private static <T> Single<T> requireSuccess(T response, int statusCode, @Nullable String message) {
        if (statusCode != SUCCESS_STATUS_CODE) {
            return Single.error(new OnboardingException(statusCode,
                    message != null ? message : "Onboarding step failed"));
        }
        return Single.just(response);
    }
}

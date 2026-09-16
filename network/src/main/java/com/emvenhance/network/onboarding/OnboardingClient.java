package com.emvenhance.network.onboarding;

import com.emvenhance.network.HostApiClient;
import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingRequest;
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.OnboardingRequest;
import com.emvenhance.network.onboarding.model.OnboardingResponse;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;

import java.util.Map;

import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Single;

/**
 * Runs the POS onboarding cycle — mirrors {@code OnboardingRepoImpl} + {@code OnboardingUseCase}
 * from the old project's model layer, collapsed into one class the way
 * {@link com.emvenhance.network.RetrofitCommunicationBehavior} already collapsed its
 * purchase-endpoint equivalent.
 *
 * <p>Two independent three-step cycles, matching the old project's {@code OnboardingViewModel}
 * exactly:
 * <ul>
 *   <li><b>Offline</b> — {@link #onboard} (send a challenge) → {@link #handshake} →
 *       {@link #confirm} (echo the same challenge back). Pairs this terminal with the host.
 *   <li><b>Online</b> — {@link #handshakeOnline} → {@link #onboardOnline} (fetches per-bank TMK
 *       blocks) → {@link #confirmOnline}. Provisions Terminal Master Keys per acquiring bank.
 * </ul>
 *
 * <p>Each step is also exposed individually — the old project's ViewModel let its UI drive them
 * one at a time — and {@link #runOfflineCycle}/{@link #runOnlineCycle} chain all three for a
 * single call site that runs the whole cycle.
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

    public Single<OnboardingStatusResponse> confirm(Map<String, String> headers, String challenge) {
        return connection.onboardingConfirm(headers, new ConfirmOnboardingRequest(challenge));
    }

    /**
     * Runs onboard → handshake → confirm in sequence, generating one challenge and reusing it
     * across steps 1 and 3 — same pairing the old project's request classes both carrying
     * {@code challenge} implies. Fails fast (without running later steps) the first time a step's
     * {@code statusCode} isn't {@link #SUCCESS_STATUS_CODE}.
     */
    public Single<OnboardingStatusResponse> runOfflineCycle(Map<String, String> headers) {
        String challenge = ChallengeGenerator.generate();
        return onboard(headers, challenge)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(r -> handshake(headers))
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(r -> confirm(headers, challenge))
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()));
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

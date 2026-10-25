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

public final class OnboardingClient {

    public static final int SUCCESS_STATUS_CODE = 200;

    private final OnboardingApiConnection connection;

    public OnboardingClient() {
        this(HostApiClient.createOnboarding());
    }

    public OnboardingClient(OnboardingApiConnection connection) {
        this.connection = connection;
    }

    public Single<OnboardingResponse> onboard(Map<String, String> headers, String challenge) {
        return connection.onboarding(headers, new OnboardingRequest(challenge));
    }

    public Single<OnboardingStatusResponse> handshake(Map<String, String> headers) {
        return connection.onboardingHandshake(headers);
    }

    public Single<OnboardingStatusResponse> confirm(Map<String, String> headers, String proof) {
        return connection.onboardingConfirm(headers, new ConfirmOnboardingRequest(proof));
    }

    public Single<CreateTokenResponse> createToken(Map<String, String> headers, String serviceAccount) {
        return connection.createToken(headers, new CreateTokenRequest(serviceAccount));
    }

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

    public Single<String> ensureValidAccessToken(Map<String, String> headers, OnboardingState state) {
        String accessToken = state.getAccessToken();
        if (accessToken != null && state.isTokenValid()) {
            return Single.just(accessToken);
        }
        String serviceAccount = state.getServiceAccount();
        if (serviceAccount == null) {
            return Single.error(new OnboardingException(0,
                    "No service account on file — full onboarding must complete before a token can be refreshed"));
        }
        return createToken(headers, serviceAccount)
                .flatMap(r -> requireSuccess(r, r.getStatusCode(), r.getMessage()))
                .flatMap(this::requireTokenData)
                .doOnSuccess(data -> {
                    state.saveAccessToken(data.getAccessToken());
                    if (data.getExpiration() != null) {
                        state.saveTokenExpiration(data.getExpiration());
                    }
                })
                .map(CreateTokenResult::getAccessToken);
    }

    private Single<CreateTokenResult> requireTokenData(CreateTokenResponse response) {
        CreateTokenResult data = response.getData();
        if (data == null || data.getAccessToken() == null) {
            return Single.error(new OnboardingException(response.getStatusCode(),
                    "createToken response missing accessToken"));
        }
        return Single.just(data);
    }

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

    public Single<OnboardingStatusResponse> handshakeOnline(Map<String, String> headers,
            String onboardingType, String onboardingKey) {
        return connection.handshakeOnboardingOnline(headers,
                new HandshakeOnboardingOnlineRequest(onboardingType, onboardingKey));
    }

    public Single<GeneralResponse> onboardOnline(Map<String, String> headers) {
        return connection.onboardingOnline(headers);
    }

    public Single<OnboardingStatusResponse> confirmOnline(Map<String, String> headers,
            String sacs, String bankCodes) {
        return connection.confirmOnboardingOnline(headers,
                new ConfirmOnboardingOnlineRequest(sacs, bankCodes));
    }

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

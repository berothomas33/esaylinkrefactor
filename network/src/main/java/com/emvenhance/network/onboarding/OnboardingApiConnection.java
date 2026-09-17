package com.emvenhance.network.onboarding;

import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingRequest;
import com.emvenhance.network.onboarding.model.CreateTokenRequest;
import com.emvenhance.network.onboarding.model.CreateTokenResponse;
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.OnboardingRequest;
import com.emvenhance.network.onboarding.model.OnboardingResponse;
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse;

import java.util.Map;

import io.reactivex.rxjava3.core.Single;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.HeaderMap;
import retrofit2.http.POST;

/**
 * The onboarding endpoints — mirrors {@code EmvApiConnection}'s onboarding subset in the old
 * project exactly (paths, verbs, and per-call header maps). {@link #createToken} lives under
 * {@code authorization/}, not {@code foundation/onboarding/} like the other six, but shares the
 * same base URL/Retrofit client, so it's declared here too rather than in its own interface.
 */
public interface OnboardingApiConnection {

    @POST("foundation/onboarding/offline")
    Single<OnboardingResponse> onboarding(@HeaderMap Map<String, String> headers,
            @Body OnboardingRequest request);

    @GET("foundation/onboarding/handshake")
    Single<OnboardingStatusResponse> onboardingHandshake(@HeaderMap Map<String, String> headers);

    @POST("foundation/onboarding/confirm")
    Single<OnboardingStatusResponse> onboardingConfirm(@HeaderMap Map<String, String> headers,
            @Body ConfirmOnboardingRequest request);

    /**
     * Exchanges the {@code serviceAccount} the onboard step returned for a bearer access token —
     * the fourth, previously-missing step of the offline cycle; see {@code OnboardingClient}.
     */
    @POST("authorization/token/pos")
    Single<CreateTokenResponse> createToken(@HeaderMap Map<String, String> headers,
            @Body CreateTokenRequest request);

    @POST("foundation/onboarding/online-handshake")
    Single<OnboardingStatusResponse> handshakeOnboardingOnline(@HeaderMap Map<String, String> headers,
            @Body HandshakeOnboardingOnlineRequest request);

    @GET("foundation/onboarding/online")
    Single<GeneralResponse> onboardingOnline(@HeaderMap Map<String, String> headers);

    @POST("foundation/onboarding/confirm-online")
    Single<OnboardingStatusResponse> confirmOnboardingOnline(@HeaderMap Map<String, String> headers,
            @Body ConfirmOnboardingOnlineRequest request);
}

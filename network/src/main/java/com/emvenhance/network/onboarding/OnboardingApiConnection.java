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

public interface OnboardingApiConnection {

    @POST("foundation/onboarding/offline")
    Single<OnboardingResponse> onboarding(@HeaderMap Map<String, String> headers,
            @Body OnboardingRequest request);

    @GET("foundation/onboarding/handshake")
    Single<OnboardingStatusResponse> onboardingHandshake(@HeaderMap Map<String, String> headers);

    @POST("foundation/onboarding/confirm")
    Single<OnboardingStatusResponse> onboardingConfirm(@HeaderMap Map<String, String> headers,
            @Body ConfirmOnboardingRequest request);

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

package com.emvenhance.network.onboarding;

import com.emvenhance.network.model.GeneralResponse;
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.CreateTokenRequest;
import com.emvenhance.network.onboarding.model.CreateTokenResponse;
import com.emvenhance.network.onboarding.model.EncryptedOnboardingRequest;
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest;
import com.emvenhance.network.onboarding.model.KeyExchangeRequest;
import com.emvenhance.network.onboarding.model.KeyExchangeResponse;
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

    /** RKI pilot ①: the SACS (stands in for RKI, as card acceptance develop_rki_pilot). */
    @GET("foundation/rki/offline/handshake")
    Single<OnboardingStatusResponse> offlineHandshake(@HeaderMap Map<String, String> headers);

    /** RKI pilot ②: the challenge → the host's RSA public key, signed with the SACS. */
    @POST("foundation/rki/offline/onboarding")
    Single<OnboardingResponse> offlineOnboarding(@HeaderMap Map<String, String> headers,
            @Body OnboardingRequest request);

    /** RKI pilot ③: the RSA-wrapped KEK → the KBPK ("MAK") as TR-31 under the KEK. */
    @POST("foundation/rki/offline/key-exchange")
    Single<KeyExchangeResponse> keyExchange(@HeaderMap Map<String, String> headers,
            @Body KeyExchangeRequest request);

    /** RKI ④: the APP_TDK as TR-31 under the KBPK. */
    @GET("foundation/rki/onboarding/handshake")
    Single<OnboardingStatusResponse> onboardingHandshake(@HeaderMap Map<String, String> headers);

    /** RKI ⑤: the identity under the APP_TDK → TDK, TPK (TR-31), their KCVs, the service account. */
    @POST("foundation/rki/onboarding")
    Single<OnboardingResponse> onboarding(@HeaderMap Map<String, String> headers,
            @Body EncryptedOnboardingRequest request);

    /** RKI ⑥: the same encrypted identity again: the keys are in. */
    @POST("foundation/rki/onboarding/confirm")
    Single<OnboardingStatusResponse> onboardingConfirm(@HeaderMap Map<String, String> headers,
            @Body EncryptedOnboardingRequest request);

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

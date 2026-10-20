package com.emvenhance.network.onboarding

import com.emvenhance.network.model.GeneralResponse
import com.emvenhance.network.onboarding.model.ConfirmOnboardingOnlineRequest
import com.emvenhance.network.onboarding.model.ConfirmOnboardingRequest
import com.emvenhance.network.onboarding.model.CreateTokenRequest
import com.emvenhance.network.onboarding.model.CreateTokenResponse
import com.emvenhance.network.onboarding.model.HandshakeOnboardingOnlineRequest
import com.emvenhance.network.onboarding.model.OnboardingRequest
import com.emvenhance.network.onboarding.model.OnboardingResponse
import com.emvenhance.network.onboarding.model.OnboardingStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.POST

/**
 * The onboarding endpoints — mirrors `EmvApiConnection`'s onboarding subset in the old project
 * exactly (paths, verbs, and per-call header maps), as Retrofit `suspend` functions.
 * [createToken] lives under `authorization/`, not `foundation/onboarding/` like the other six,
 * but shares the same base URL/Retrofit client, so it's declared here too rather than in its own
 * interface.
 */
interface OnboardingApiConnection {

    @POST("foundation/onboarding/offline")
    suspend fun onboarding(
        @HeaderMap headers: Map<String, String>,
        @Body request: OnboardingRequest,
    ): OnboardingResponse

    @GET("foundation/onboarding/handshake")
    suspend fun onboardingHandshake(@HeaderMap headers: Map<String, String>): OnboardingStatusResponse

    @POST("foundation/onboarding/confirm")
    suspend fun onboardingConfirm(
        @HeaderMap headers: Map<String, String>,
        @Body request: ConfirmOnboardingRequest,
    ): OnboardingStatusResponse

    /**
     * Exchanges the `serviceAccount` the onboard step returned for a bearer access token — the
     * fourth, previously-missing step of the offline cycle; see `OnboardingClient`.
     */
    @POST("authorization/token/pos")
    suspend fun createToken(
        @HeaderMap headers: Map<String, String>,
        @Body request: CreateTokenRequest,
    ): CreateTokenResponse

    @POST("foundation/onboarding/online-handshake")
    suspend fun handshakeOnboardingOnline(
        @HeaderMap headers: Map<String, String>,
        @Body request: HandshakeOnboardingOnlineRequest,
    ): OnboardingStatusResponse

    @GET("foundation/onboarding/online")
    suspend fun onboardingOnline(@HeaderMap headers: Map<String, String>): GeneralResponse

    @POST("foundation/onboarding/confirm-online")
    suspend fun confirmOnboardingOnline(
        @HeaderMap headers: Map<String, String>,
        @Body request: ConfirmOnboardingOnlineRequest,
    ): OnboardingStatusResponse
}

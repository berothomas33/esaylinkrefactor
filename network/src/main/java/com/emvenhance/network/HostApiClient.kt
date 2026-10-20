package com.emvenhance.network

import com.emvenhance.network.env.EnvironmentProvider
import com.emvenhance.network.onboarding.OnboardingApiConnection
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds Retrofit service interfaces against whichever base URL [EnvironmentProvider] is
 * currently holding — same Retrofit/OkHttp/Gson stack as the old project's `EmvApiClient`
 * (with `suspend` functions instead of its RxJava call adapter), rebuilt per call so a base URL
 * change takes effect on the next request instead of being baked into a static singleton.
 */
object HostApiClient {

    private const val TIMEOUT_SECONDS = 75L

    @JvmStatic
    fun create(): HostApiConnection = create(HostApiConnection::class.java)

    @JvmStatic
    fun createOnboarding(): OnboardingApiConnection = create(OnboardingApiConnection::class.java)

    private fun <T> create(service: Class<T>): T {
        val logging = HttpLoggingInterceptor()
        logging.setLevel(HttpLoggingInterceptor.Level.BODY)

        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(EnvironmentProvider.getBaseUrl())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(service)
    }
}

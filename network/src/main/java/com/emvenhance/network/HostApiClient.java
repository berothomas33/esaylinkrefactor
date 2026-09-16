package com.emvenhance.network;

import com.emvenhance.network.env.EnvironmentProvider;
import com.emvenhance.network.onboarding.OnboardingApiConnection;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds Retrofit service interfaces against whichever base URL {@link EnvironmentProvider} is
 * currently holding — same Retrofit/OkHttp/Gson/RxJava3 stack as the old project's
 * {@code EmvApiClient}, rebuilt per call so a base URL change takes effect on the next request
 * instead of being baked into a static singleton.
 */
public final class HostApiClient {

    private static final long TIMEOUT_SECONDS = 75;

    private HostApiClient() {
    }

    public static HostApiConnection create() {
        return create(HostApiConnection.class);
    }

    public static OnboardingApiConnection createOnboarding() {
        return create(OnboardingApiConnection.class);
    }

    private static <T> T create(Class<T> service) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(EnvironmentProvider.getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build();

        return retrofit.create(service);
    }
}

package com.emvenhance.network;

import com.emvenhance.network.env.EnvironmentProvider;
import com.emvenhance.network.onboarding.OnboardingApiConnection;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public final class HostApiClient {

    private static final long TIMEOUT_SECONDS = 75;
    private static final long PAYMENT_CALL_TIMEOUT_SECONDS = 25;
    private static final long NO_CALL_TIMEOUT = 0;

    private HostApiClient() {
    }

    public static HostApiConnection create() {
        return create(HostApiConnection.class, NO_CALL_TIMEOUT);
    }

    public static OnboardingApiConnection createOnboarding() {
        return create(OnboardingApiConnection.class, NO_CALL_TIMEOUT);
    }

    public static HostApiConnection createForPayments() {
        return create(HostApiConnection.class, PAYMENT_CALL_TIMEOUT_SECONDS);
    }

    public static OnboardingApiConnection createOnboardingForPayments() {
        return create(OnboardingApiConnection.class, PAYMENT_CALL_TIMEOUT_SECONDS);
    }

    private static <T> T create(Class<T> service, long callTimeoutSeconds) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(callTimeoutSeconds, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(EnvironmentProvider.getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(callTimeoutSeconds == NO_CALL_TIMEOUT
                        ? RxJava3CallAdapterFactory.create()
                        : RxJava3CallAdapterFactory.createSynchronous())
                .build();

        return retrofit.create(service);
    }
}

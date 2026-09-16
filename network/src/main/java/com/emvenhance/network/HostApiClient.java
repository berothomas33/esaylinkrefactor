package com.emvenhance.network;

import com.emvenhance.network.env.ApiEnvironment;
import com.emvenhance.network.env.EnvironmentProvider;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Builds a {@link HostApiConnection} against whatever {@link ApiEnvironment} is currently
 * selected — same Retrofit/OkHttp/Gson/RxJava3 stack as the old project's {@code EmvApiClient},
 * rebuilt per call so an environment switch takes effect on the next request instead of being
 * baked into a static singleton.
 */
public final class HostApiClient {

    private static final long TIMEOUT_SECONDS = 75;

    private HostApiClient() {
    }

    public static HostApiConnection create() {
        return create(EnvironmentProvider.get());
    }

    public static HostApiConnection create(ApiEnvironment environment) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(environment.getBaseUrl())
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .build();

        return retrofit.create(HostApiConnection.class);
    }
}

package com.example.smartqueue.network;

import com.example.smartqueue.BuildConfig;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    private static Retrofit retrofit = null;
    private static String authToken  = null;

    public static void setAuthToken(String token) {
        authToken = token;
        retrofit  = null; // force rebuild with new token
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            retrofit = buildRetrofit(authToken);
        }
        return retrofit;
    }

    public static Retrofit createAuthorizedInstance(String tokenOverride) {
        return buildRetrofit(tokenOverride);
    }

    private static Retrofit buildRetrofit(String tokenOverride) {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder builder = original.newBuilder()
                            .header("Content-Type", "application/json");

                    // Only login and register are anonymous auth routes.
                    // Other /auth/* routes such as /auth/doctors still require the JWT.
                    String path = original.url().encodedPath();
                    boolean isAnonymousAuthRoute =
                            path.endsWith("/auth/login") || path.endsWith("/auth/register");
                    String effectiveToken = tokenOverride != null ? tokenOverride : authToken;

                    if (effectiveToken != null && !isAnonymousAuthRoute) {
                        builder.header("Authorization", "Bearer " + effectiveToken);
                    }

                    return chain.proceed(builder.build());
                })
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.API_BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }
}

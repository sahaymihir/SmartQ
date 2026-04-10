package com.example.smartqueue.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import java.util.concurrent.TimeUnit;

public class ApiClient {

    // 10.0.2.2 = Android Emulator's way of reaching your PC's localhost
    // If testing on a real device, change this to your PC's LAN IP e.g. http://192.168.1.x:5000/api/
    private static final String BASE_URL = "http://10.0.2.2:5000/api/";

    private static Retrofit retrofit = null;
    private static String authToken  = null;

    public static void setAuthToken(String token) {
        authToken = token;
        retrofit  = null; // force rebuild with new token
    }

    public static Retrofit getInstance() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .addInterceptor(chain -> {
                        Request original = chain.request();
                        Request.Builder builder = original.newBuilder()
                                .header("Content-Type", "application/json");
                        
                        // Only add Authorization header if we have a token 
                        // AND it's not an authentication request (login/register)
                        String path = original.url().encodedPath();
                        if (authToken != null && !path.contains("/auth/")) {
                            builder.header("Authorization", "Bearer " + authToken);
                        }

                        return chain.proceed(builder.build());
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
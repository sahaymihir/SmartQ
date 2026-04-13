package com.example.smartqueue.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.smartqueue.models.request.NotificationRegistrationRequest;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.google.firebase.messaging.FirebaseMessaging;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Registers / unregisters the current device token with the backend so queue
 * push notifications keep working across fresh login, restored sessions, and logout.
 */
public final class PushRegistrationHelper {

    private static final String TAG = "PushRegistration";

    private PushRegistrationHelper() {}

    public static void syncDeviceToken(Context context, @Nullable SessionManager sessionManager) {
        if (context == null || sessionManager == null || !sessionManager.isLoggedIn()) {
            return;
        }

        String sessionToken = sessionManager.getToken();
        if (TextUtils.isEmpty(sessionToken)) {
            return;
        }

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || TextUtils.isEmpty(task.getResult())) {
                Log.w(TAG, "Could not fetch device token for backend registration", task.getException());
                return;
            }

            ApiService apiService = ApiClient.createAuthorizedInstance(sessionToken)
                    .create(ApiService.class);
            apiService.registerDeviceToken(new NotificationRegistrationRequest(task.getResult()))
                    .enqueue(new SilentCallback("device token registration"));
        });
    }

    public static void unregisterDeviceToken(@Nullable SessionManager sessionManager) {
        if (sessionManager == null || !sessionManager.isLoggedIn()) {
            return;
        }

        String sessionToken = sessionManager.getToken();
        if (TextUtils.isEmpty(sessionToken)) {
            return;
        }

        ApiService apiService = ApiClient.createAuthorizedInstance(sessionToken)
                .create(ApiService.class);
        apiService.unregisterDeviceToken().enqueue(new SilentCallback("device token unregister"));
    }

    private static final class SilentCallback implements Callback<MessageResponse> {
        private final String action;

        private SilentCallback(String action) {
            this.action = action;
        }

        @Override
        public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
            if (!response.isSuccessful()) {
                Log.w(TAG, "Backend " + action + " returned " + response.code());
            }
        }

        @Override
        public void onFailure(Call<MessageResponse> call, Throwable t) {
            Log.w(TAG, "Backend " + action + " failed", t);
        }
    }
}

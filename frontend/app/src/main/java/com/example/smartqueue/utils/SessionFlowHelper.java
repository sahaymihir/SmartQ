package com.example.smartqueue.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.smartqueue.R;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.ui.auth.LoginActivity;

/**
 * Shared logout / session-expiry flow for the Java/XML client.
 */
public final class SessionFlowHelper {

    private SessionFlowHelper() {}

    public static void logoutToLogin(Activity activity, SessionManager sessionManager, @Nullable String toastMessage) {
        if (activity == null) {
            return;
        }

        if (toastMessage != null && !toastMessage.trim().isEmpty()) {
            Toast.makeText(activity, toastMessage, Toast.LENGTH_LONG).show();
        }

        PushRegistrationHelper.unregisterDeviceToken(sessionManager);
        sessionManager.clearSession();
        ApiClient.setAuthToken(null);

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        activity.finish();
    }
}

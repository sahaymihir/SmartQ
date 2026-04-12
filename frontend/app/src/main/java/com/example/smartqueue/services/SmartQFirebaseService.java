package com.example.smartqueue.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.NotificationRegistrationRequest;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * SmartQFirebaseService — Firebase Cloud Messaging handler.
 *
 * Responsibilities:
 *   1. Receives push messages from the SmartQ backend and shows a local notification.
 *   2. Refreshes the FCM token on the backend whenever Firebase generates a new one.
 *
 * Supported events (in RemoteMessage.getData()):
 *   token_called   — patient's turn has arrived
 *   eta_updated    — waiting time has been recalculated
 *   queue_paused   — the doctor's queue has been paused
 *   queue_resumed  — the doctor's queue has been resumed
 */
public class SmartQFirebaseService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "smartq_queue_channel";
    private static final String CHANNEL_NAME = "Queue Notifications";
    private static final int NOTIFICATION_ID_QUEUE = 1001;

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        String title = null;
        String body = null;

        // Prefer the notification payload if present (background delivery)
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        // Fall back to data payload fields
        if (title == null) title = remoteMessage.getData().get("title");
        if (body == null)  body  = remoteMessage.getData().get("body");

        if (title == null) title = "SmartQ";
        if (body == null)  body  = "Queue update";

        showLocalNotification(title, body);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Re-register the new FCM token with the backend if the user is logged in.
        SessionManager sessionManager = new SessionManager(getApplicationContext());
        if (!sessionManager.isLoggedIn()) return;

        ApiClient.setAuthToken(sessionManager.getToken());
        ApiService apiService = ApiClient.getInstance().create(ApiService.class);
        apiService.registerDeviceToken(new NotificationRegistrationRequest(token))
                .enqueue(new Callback<MessageResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<MessageResponse> call,
                                           @NonNull Response<MessageResponse> response) {
                        // Silent refresh — no user-visible feedback needed.
                    }
                    @Override
                    public void onFailure(@NonNull Call<MessageResponse> call, @NonNull Throwable t) {
                        // Non-fatal; the next successful login will re-register.
                    }
                });
    }

    // ─── Notification display ─────────────────────────────────

    private void showLocalNotification(String title, String body) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (manager == null) return;

        createNotificationChannel(manager);

        SessionManager sessionManager = new SessionManager(getApplicationContext());
        Intent intent = RoleNavigationHelper.createHomeIntent(this, sessionManager.getRole());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);

        manager.notify(NOTIFICATION_ID_QUEUE, builder.build());
    }

    private void createNotificationChannel(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Real-time SmartQ queue position and turn alerts");
            manager.createNotificationChannel(channel);
        }
    }
}

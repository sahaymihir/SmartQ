package com.example.smartqueue.models.request;

/** Request body for POST /api/notifications/register-device. */
public class NotificationRegistrationRequest {
    private String fcmToken;

    public NotificationRegistrationRequest(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}

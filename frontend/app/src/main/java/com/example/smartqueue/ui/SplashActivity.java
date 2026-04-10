package com.example.smartqueue.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.ui.admin.AdminDashboardActivity;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.ui.patient.PatientHomeActivity;
import com.example.smartqueue.utils.SessionManager;

/**
 * SplashActivity — Entry point.
 * Shows app logo for 1.5s, then redirects:
 *   - Logged in patient  → PatientHomeActivity
 *   - Logged in admin    → AdminDashboardActivity
 *   - Not logged in      → LoginActivity
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager session = new SessionManager(this);

            if (session.isLoggedIn()) {
                // Reattach token for API calls
                ApiClient.setAuthToken(session.getToken());

                Intent intent;
                if (session.isAdmin()) {
                    intent = new Intent(this, AdminDashboardActivity.class);
                } else {
                    intent = new Intent(this, PatientHomeActivity.class);
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, SPLASH_DELAY_MS);
    }
}
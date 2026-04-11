package com.example.smartqueue.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.ui.admin.AdminDashboardActivity;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.ui.doctor.DoctorHomeActivity;
import com.example.smartqueue.ui.patient.PatientHomeActivity;
import com.example.smartqueue.utils.SessionManager;

/**
 * SplashActivity — Premium entry point with staggered animations.
 * Shows animated branding for 2.5s, then redirects based on session.
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Animate elements with staggered timing
        animateSplash();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager session = new SessionManager(this);

            Intent intent;
            if (session.isLoggedIn()) {
                ApiClient.setAuthToken(session.getToken());

                String role = session.getRole();
                if ("admin".equals(role)) {
                    intent = new Intent(this, AdminDashboardActivity.class);
                } else if ("doctor".equals(role)) {
                    intent = new Intent(this, DoctorHomeActivity.class);
                } else {
                    intent = new Intent(this, PatientHomeActivity.class);
                }
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            } else {
                intent = new Intent(this, LoginActivity.class);
            }

            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }, SPLASH_DELAY_MS);
    }

    private void animateSplash() {
        View logoContainer = findViewById(R.id.logoContainer);
        View badgeContainer = findViewById(R.id.badgeContainer);
        View headlineContainer = findViewById(R.id.headlineContainer);
        View tvSubtitle = findViewById(R.id.tvSubtitle);
        ProgressBar progressBar = findViewById(R.id.progressBar);
        View footerContainer = findViewById(R.id.footerContainer);
        View pulseDot = findViewById(R.id.pulseDot);
        View glowTop = findViewById(R.id.glowTop);

        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_enter);
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);

        // Animate ambient glow
        glowTop.animate().alpha(0.8f).scaleX(1.1f).scaleY(1.1f)
                .setDuration(2000).start();

        // Staggered entrance
        Handler handler = new Handler(Looper.getMainLooper());

        // 0ms — Logo
        handler.postDelayed(() -> {
            logoContainer.setAlpha(1f);
            logoContainer.startAnimation(slideUp);
        }, 0);

        // 200ms — Badge
        handler.postDelayed(() -> {
            badgeContainer.setAlpha(1f);
            badgeContainer.startAnimation(fadeIn);
            pulseDot.startAnimation(pulse);
        }, 200);

        // 400ms — Headline
        handler.postDelayed(() -> {
            headlineContainer.setAlpha(1f);
            headlineContainer.startAnimation(slideUp);
        }, 400);

        // 600ms — Subtitle
        handler.postDelayed(() -> {
            tvSubtitle.setAlpha(1f);
            tvSubtitle.startAnimation(fadeIn);
        }, 600);

        // 800ms — Progress
        handler.postDelayed(() -> {
            progressBar.setAlpha(1f);
            progressBar.startAnimation(fadeIn);
        }, 800);

        // 1000ms — Footer
        handler.postDelayed(() -> {
            footerContainer.setAlpha(1f);
            footerContainer.startAnimation(fadeIn);
        }, 1000);
    }
}
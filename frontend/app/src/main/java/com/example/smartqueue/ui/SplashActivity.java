package com.example.smartqueue.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.airbnb.lottie.LottieAnimationView;
import com.example.smartqueue.R;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.ui.admin.AdminDashboardActivity;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;

/**
 * SplashActivity — Premium entry point with Lottie animations
 * and staggered cinematic entrance.
 */
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Tie into Android 12+ native splash screen for smooth boot
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        animateSplash();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager session = new SessionManager(this);

            Intent intent;
            if (session.isLoggedIn()) {
                if (session.hasRestorableSession()) {
                    ApiClient.setAuthToken(session.getToken());
                    intent = RoleNavigationHelper.createClearedHomeIntent(this, session.getRole());
                } else {
                    session.clearSession();
                    ApiClient.setAuthToken(null);
                    intent = new Intent(this, LoginActivity.class);
                }
            } else {
                intent = new Intent(this, LoginActivity.class);
            }

            // Exit animation — zoom out + fade
            View root = findViewById(android.R.id.content);
            root.animate()
                    .scaleX(0.9f).scaleY(0.9f).alpha(0f)
                    .setDuration(400)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        startActivity(intent);
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                        finish();
                    }).start();
        }, SPLASH_DELAY_MS);
    }

    private void animateSplash() {
        LottieAnimationView lottieHero = findViewById(R.id.lottieHero);
        LottieAnimationView lottieLoading = findViewById(R.id.lottieLoading);
        View logoContainer = findViewById(R.id.logoContainer);
        View badgeContainer = findViewById(R.id.badgeContainer);
        View headlineContainer = findViewById(R.id.headlineContainer);
        View tvSubtitle = findViewById(R.id.tvSubtitle);
        View footerContainer = findViewById(R.id.footerContainer);
        View pulseDot = findViewById(R.id.pulseDot);
        View glowTop = findViewById(R.id.glowTop);
        View glowBottom = findViewById(R.id.glowBottom);

        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);

        // Ambient glow — slow breathe animation
        glowTop.animate().alpha(0.7f).scaleX(1.2f).scaleY(1.2f)
                .setDuration(3000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        glowBottom.animate().alpha(0.5f).scaleX(1.15f).scaleY(1.15f)
                .setDuration(3500)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        Handler h = new Handler(Looper.getMainLooper());

        // 0ms — Lottie hero fades in with scale overshoot
        h.postDelayed(() -> {
            lottieHero.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(600)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
            lottieHero.setScaleX(0.5f);
            lottieHero.setScaleY(0.5f);
            lottieHero.playAnimation();
        }, 0);

        // 300ms — Logo slides up
        h.postDelayed(() -> {
            logoContainer.setTranslationY(40);
            logoContainer.animate().alpha(1f).translationY(0)
                    .setDuration(500)
                    .setInterpolator(new OvershootInterpolator(0.8f))
                    .start();
        }, 300);

        // 500ms — Badge pops in
        h.postDelayed(() -> {
            badgeContainer.setScaleX(0.5f);
            badgeContainer.setScaleY(0.5f);
            badgeContainer.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(new OvershootInterpolator(2f))
                    .start();
            pulseDot.startAnimation(pulse);
        }, 500);

        // 700ms — Headline slides up with spring
        h.postDelayed(() -> {
            headlineContainer.setTranslationY(60);
            headlineContainer.animate().alpha(1f).translationY(0)
                    .setDuration(600)
                    .setInterpolator(new OvershootInterpolator(0.6f))
                    .start();
        }, 700);

        // 1000ms — Subtitle fades in
        h.postDelayed(() -> {
            tvSubtitle.animate().alpha(1f)
                    .setDuration(500)
                    .start();
        }, 1000);

        // 1200ms — Lottie loading dots
        h.postDelayed(() -> {
            lottieLoading.animate().alpha(1f)
                    .setDuration(400)
                    .start();
            lottieLoading.playAnimation();
        }, 1200);

        // 1500ms — Footer slides up from bottom
        h.postDelayed(() -> {
            footerContainer.setTranslationY(30);
            footerContainer.animate().alpha(1f).translationY(0)
                    .setDuration(500)
                    .start();
        }, 1500);
    }
}

package com.example.smartqueue.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.LoginRequest;
import com.example.smartqueue.models.response.AuthResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.utils.PushRegistrationHelper;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.Executor;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoToRegister;
    private ProgressBar progressBar;
    private TextView tvError;
    private View biometricContainer;
    private View btnBiometric;
    private CheckBox cbKeepSession;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();

        cbKeepSession.setChecked(sessionManager.shouldKeepSessionActive());

        if (sessionManager.hasRestorableSession()) {
            ApiClient.setAuthToken(sessionManager.getToken());
            biometricContainer.setVisibility(View.VISIBLE);
            showBiometricPrompt();
        } else if (sessionManager.isLoggedIn()) {
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
        }

        setupClickListeners();
        animateEntrance();
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(LoginActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                navigateToHome(sessionManager.getRole());
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_title))
                .setSubtitle(getString(R.string.biometric_subtitle))
                .setNegativeButtonText(getString(R.string.biometric_negative))
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void bindViews() {
        tilEmail            = findViewById(R.id.tilEmail);
        tilPassword         = findViewById(R.id.tilPassword);
        etEmail             = findViewById(R.id.etEmail);
        etPassword          = findViewById(R.id.etPassword);
        btnLogin            = findViewById(R.id.btnLogin);
        btnGoToRegister     = findViewById(R.id.btnGoToRegister);
        progressBar         = findViewById(R.id.progressBar);
        tvError             = findViewById(R.id.tvError);
        biometricContainer  = findViewById(R.id.biometricContainer);
        btnBiometric        = findViewById(R.id.btnBiometric);
        cbKeepSession       = findViewById(R.id.cbKeepSession);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> {
            // Scale press animation
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        attemptLogin();
                    }).start();
        });

        btnGoToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        btnBiometric.setOnClickListener(v -> {
            // Pulse animation on tap
            v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        showBiometricPrompt();
                    }).start();
        });
    }

    private void animateEntrance() {
        View loginLogo = findViewById(R.id.loginLogo);
        View loginHeader = findViewById(R.id.loginHeader);
        View loginForm = findViewById(R.id.loginForm);
        View btnLogin = findViewById(R.id.btnLogin);

        android.view.animation.OvershootInterpolator overshoot =
                new android.view.animation.OvershootInterpolator(0.8f);

        Handler handler = new Handler(Looper.getMainLooper());

        // 0ms — Logo drops in with overshoot
        handler.postDelayed(() -> {
            loginLogo.setTranslationY(-30);
            loginLogo.animate().alpha(1f).translationY(0)
                    .setDuration(500).setInterpolator(overshoot).start();
        }, 0);

        // 200ms — Header slides up
        handler.postDelayed(() -> {
            loginHeader.setTranslationY(40);
            loginHeader.animate().alpha(1f).translationY(0)
                    .setDuration(500).setInterpolator(overshoot).start();
        }, 200);

        // 400ms — Form card scales in
        handler.postDelayed(() -> {
            loginForm.setScaleX(0.9f);
            loginForm.setScaleY(0.9f);
            loginForm.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(500).setInterpolator(overshoot).start();
        }, 400);

        // 600ms — CTA button slides up with bounce
        handler.postDelayed(() -> {
            btnLogin.setTranslationY(30);
            btnLogin.animate().translationY(0)
                    .setDuration(400)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                    .start();
        }, 600);
    }

    private void attemptLogin() {
        tilEmail.setError(null);
        tilPassword.setError(null);
        tvError.setVisibility(View.GONE);

        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) { tilEmail.setError("Email is required"); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { tilEmail.setError("Invalid email"); return; }
        if (TextUtils.isEmpty(password)) { tilPassword.setError("Password is required"); return; }
        if (password.length() < 6) { tilPassword.setError("Min 6 characters"); return; }

        setLoading(true);

        apiService.login(new LoginRequest(email, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            AuthResponse body = response.body();
                            AuthResponse.User user = body.getUser();
                            sessionManager.saveSession(
                                    body.getToken(),
                                    user.getId(),
                                    user.getName(),
                                    user.getEmail(),
                                    user.getRole(),
                                    user.getAge(),
                                    user.getStaffId(),
                                    user.getSpecialty(),
                                    cbKeepSession.isChecked());
                            ApiClient.setAuthToken(body.getToken());
                            PushRegistrationHelper.syncDeviceToken(LoginActivity.this, sessionManager);
                            navigateToHome(user.getRole());
                        } else {
                            showError("Invalid email or password");
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        setLoading(false);
                        showError("Cannot reach server. Is it running?");
                    }
                });
    }

    private void navigateToHome(String role) {
        Intent intent = RoleNavigationHelper.createClearedHomeIntent(this, role);
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnGoToRegister.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setText(loading ? "Initializing..." : getString(R.string.login));
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
        // Shake animation on error
        tvError.animate().translationX(-8).setDuration(50)
                .withEndAction(() -> tvError.animate().translationX(8).setDuration(50)
                        .withEndAction(() -> tvError.animate().translationX(0).setDuration(50).start()).start()).start();
    }
}

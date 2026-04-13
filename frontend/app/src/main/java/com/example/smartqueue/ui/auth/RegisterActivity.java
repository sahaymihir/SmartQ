package com.example.smartqueue.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.RegisterRequest;
import com.example.smartqueue.models.response.AuthResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.utils.PushRegistrationHelper;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPhone, tilAge, tilPassword;
    private TextInputEditText etName, etEmail, etPhone, etAge, etPassword;
    private MaterialButton btnRegister, btnGoToLogin;
    private ProgressBar progressBar;
    private TextView tvError;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        setupClickListeners();
        animateEntrance();
    }

    private void bindViews() {
        tilName     = findViewById(R.id.tilName);
        tilEmail    = findViewById(R.id.tilEmail);
        tilPhone    = findViewById(R.id.tilPhone);
        tilAge      = findViewById(R.id.tilAge);
        tilPassword = findViewById(R.id.tilPassword);
        etName      = findViewById(R.id.etName);
        etEmail     = findViewById(R.id.etEmail);
        etPhone     = findViewById(R.id.etPhone);
        etAge       = findViewById(R.id.etAge);
        etPassword  = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
        progressBar = findViewById(R.id.progressBar);
        tvError     = findViewById(R.id.tvError);
        etPhone.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(10) });
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(v -> {
            // Scale press animation
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        attemptRegister();
                    }).start();
        });

        btnGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        });
    }

    private void animateEntrance() {
        View registerHeader = findViewById(R.id.registerHeader);

        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_enter);
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);

        Handler handler = new Handler(Looper.getMainLooper());

        handler.postDelayed(() -> {
            registerHeader.startAnimation(slideUp);
        }, 0);
    }

    private void attemptRegister() {
        clearErrors();
        tvError.setVisibility(View.GONE);

        String name     = etName.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String phone    = etPhone.getText().toString().trim();
        String ageStr   = etAge.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        String role = "patient";
        // Public registration always creates a patient account.
        // Doctors, nurses, and admins are created by an administrator from the management panel.

        boolean valid = true;

        if (TextUtils.isEmpty(name))   { tilName.setError("Required"); valid = false; }
        if (TextUtils.isEmpty(email))  { tilEmail.setError("Required"); valid = false; }
        else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email"); valid = false;
        }
        if (TextUtils.isEmpty(phone) || phone.length() != 10 || !TextUtils.isDigitsOnly(phone)) {
            tilPhone.setError("Enter valid 10-digit number"); valid = false;
        }

        int age = 0;
        if (TextUtils.isEmpty(ageStr)) {
            tilAge.setError("Required"); valid = false;
        } else {
            try {
                age = Integer.parseInt(ageStr);
                if (age < 1 || age > 120) { tilAge.setError("Invalid age"); valid = false; }
            } catch (NumberFormatException e) {
                tilAge.setError("Invalid number"); valid = false;
            }
        }

        if (TextUtils.isEmpty(password))  { tilPassword.setError("Required"); valid = false; }
        else if (password.length() < 6)   { tilPassword.setError("Min 6 characters"); valid = false; }

        if (!valid) return;

        setLoading(true);

        // Ensure no old token is being sent with the registration request
        ApiClient.setAuthToken(null);

        RegisterRequest request = new RegisterRequest(name, email, password, phone, age, role);

        apiService.register(request).enqueue(new Callback<AuthResponse>() {
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
                            user.getSpecialty());

                    ApiClient.setAuthToken(body.getToken());
                    PushRegistrationHelper.syncDeviceToken(RegisterActivity.this, sessionManager);
                    navigateToHome(user.getRole());
                } else {
                    showError(extractErrorMessage(response));
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                showError("Server error. Is your backend running?");
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
        btnRegister.setEnabled(!loading);
        btnGoToLogin.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setText(loading ? "Creating account..." : getString(R.string.register));
    }

    private void clearErrors() {
        tilName.setError(null); tilEmail.setError(null);
        tilPhone.setError(null); tilAge.setError(null);
        tilPassword.setError(null);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
        // Shake animation on error
        tvError.animate().translationX(-8).setDuration(50)
                .withEndAction(() -> tvError.animate().translationX(8).setDuration(50)
                        .withEndAction(() -> tvError.animate().translationX(0).setDuration(50).start()).start()).start();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String extractErrorMessage(Response<AuthResponse> response) {
        try {
            if (response != null && response.errorBody() != null) {
                String raw = response.errorBody().string();
                if (!TextUtils.isEmpty(raw)) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    AuthResponse error = gson.fromJson(raw, AuthResponse.class);
                    if (error != null && !TextUtils.isEmpty(error.getMessage())) {
                        return error.getMessage();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "Registration failed. Please try again.";
    }
}

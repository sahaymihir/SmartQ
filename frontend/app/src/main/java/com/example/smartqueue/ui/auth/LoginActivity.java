package com.example.smartqueue.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.LoginRequest;
import com.example.smartqueue.models.response.AuthResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.admin.AdminDashboardActivity;
import com.example.smartqueue.ui.doctor.DoctorHomeActivity;
import com.example.smartqueue.ui.patient.PatientHomeActivity;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialButton btnLogin, btnGoToRegister;
    private ProgressBar progressBar;
    private TextView tvError;
    private SessionManager sessionManager;
    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);

        if (sessionManager.isLoggedIn()) {
            ApiClient.setAuthToken(sessionManager.getToken());
            navigateToHome(sessionManager.getRole());
            return;
        }

        bindViews();
        setupClickListeners();
    }

    private void bindViews() {
        tilEmail        = findViewById(R.id.tilEmail);
        tilPassword     = findViewById(R.id.tilPassword);
        etEmail         = findViewById(R.id.etEmail);
        etPassword      = findViewById(R.id.etPassword);
        btnLogin        = findViewById(R.id.btnLogin);
        btnGoToRegister = findViewById(R.id.btnGoToRegister);
        progressBar     = findViewById(R.id.progressBar);
        tvError         = findViewById(R.id.tvError);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
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
                            sessionManager.saveSession(body.getToken(), user.getId(),
                                    user.getName(), user.getEmail(), user.getRole(), user.getAge());
                            ApiClient.setAuthToken(body.getToken());
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
        Intent intent;
        if ("admin".equals(role)) {
            intent = new Intent(this, AdminDashboardActivity.class);
        } else if ("doctor".equals(role)) {
            intent = new Intent(this, DoctorHomeActivity.class);
        } else {
            intent = new Intent(this, PatientHomeActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnGoToRegister.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setText(loading ? "Please wait..." : "Login");
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}

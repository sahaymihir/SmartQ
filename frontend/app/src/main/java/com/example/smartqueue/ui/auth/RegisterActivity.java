package com.example.smartqueue.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
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

public class RegisterActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilEmail, tilPhone, tilAge, tilPassword;
    private TextInputEditText etName, etEmail, etPhone, etAge, etPassword;
    private RadioGroup rgRole;
    private RadioButton rbAdmin, rbDoctor;
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
        rgRole      = findViewById(R.id.rgRole);
        rbAdmin     = findViewById(R.id.rbAdmin);
        rbDoctor    = findViewById(R.id.rbDoctor);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoToLogin = findViewById(R.id.btnGoToLogin);
        progressBar = findViewById(R.id.progressBar);
        tvError     = findViewById(R.id.tvError);
    }

    private void setupClickListeners() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        btnGoToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
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
        if (rbAdmin.isChecked()) role = "admin";
        else if (rbDoctor.isChecked()) role = "doctor";

        boolean valid = true;

        if (TextUtils.isEmpty(name))   { tilName.setError("Required"); valid = false; }
        if (TextUtils.isEmpty(email))  { tilEmail.setError("Required"); valid = false; }
        else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError("Invalid email"); valid = false;
        }
        if (TextUtils.isEmpty(phone) || phone.length() < 10) {
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
                            user.getAge()
                    );
                    
                    ApiClient.setAuthToken(body.getToken());
                    navigateToHome(user.getRole());
                } else {
                    showError("Registration failed. Email might already exist on server.");
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
        btnRegister.setEnabled(!loading);
        btnGoToLogin.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setText(loading ? "Creating account..." : "Create Account");
    }

    private void clearErrors() {
        tilName.setError(null); tilEmail.setError(null);
        tilPhone.setError(null); tilAge.setError(null);
        tilPassword.setError(null);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

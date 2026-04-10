package com.example.smartqueue.utils;

import com.example.smartqueue.models.response.AuthResponse;

/**
 * MockAuthManager — Fake auth for UI testing without a real backend.
 *
 * TEST ACCOUNTS:
 *   patient@test.com  / 123456  → goes to PatientHomeActivity
 *   admin@test.com    / 123456  → goes to AdminDashboardActivity
 *
 * To switch to real backend later:
 *   In LoginActivity and RegisterActivity, replace:
 *     MockAuthManager.login(...)
 *   with the real Retrofit API call.
 */
public class MockAuthManager {

    public interface AuthCallback {
        void onSuccess(AuthResponse response);
        void onFailure(String errorMessage);
    }

    // ── Hardcoded test users ──────────────────────────────
    private static final String[][] USERS = {
            // { name, email, password, role, age, id }
            { "Omkar Nayak",   "patient@test.com", "123456", "patient", "21", "mock_patient_001" },
            { "Dr. Nisha",     "admin@test.com",   "123456", "admin",   "40", "mock_admin_001"   },
            { "Rishi Senior",  "senior@test.com",  "123456", "patient", "65", "mock_patient_002" },
    };

    /**
     * Simulates a login API call.
     * Runs on a short delay to mimic real network behaviour.
     */
    public static void login(String email, String password, AuthCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            for (String[] user : USERS) {
                if (user[1].equalsIgnoreCase(email) && user[2].equals(password)) {
                    callback.onSuccess(buildResponse(user));
                    return;
                }
            }
            callback.onFailure("Invalid email or password");
        }, 800); // 800ms fake network delay
    }

    /**
     * Simulates a register API call.
     * Always succeeds for new emails, fails if email already exists.
     */
    public static void register(String name, String email, String password,
                                String phone, int age, String role, AuthCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Check if email already "exists"
            for (String[] user : USERS) {
                if (user[1].equalsIgnoreCase(email)) {
                    callback.onFailure("Email already registered. Try logging in.");
                    return;
                }
            }

            // Create a mock successful response
            AuthResponse response = new AuthResponse();
            response.setSuccess(true);
            response.setToken("mock_jwt_token_" + System.currentTimeMillis());

            AuthResponse.User user = new AuthResponse.User();
            user.setId("mock_" + System.currentTimeMillis());
            user.setName(name);
            user.setEmail(email);
            user.setRole(role);
            user.setAge(age);
            response.setUser(user);

            callback.onSuccess(response);
        }, 1000);
    }

    // ── Helper ───────────────────────────────────────────
    private static AuthResponse buildResponse(String[] userData) {
        AuthResponse response = new AuthResponse();
        response.setSuccess(true);
        response.setToken("mock_jwt_" + userData[5]);

        AuthResponse.User user = new AuthResponse.User();
        user.setId(userData[5]);
        user.setName(userData[0]);
        user.setEmail(userData[1]);
        user.setRole(userData[3]);
        user.setAge(Integer.parseInt(userData[4]));
        response.setUser(user);

        return response;
    }
}
package com.example.smartqueue.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager — Stores JWT token and user info in SharedPreferences.
 */
public class SessionManager {

    private static final String PREF_NAME = "SmartQSession";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_ROLE = "role";
    private static final String KEY_AGE = "age";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    /** Call after successful login or register */
    public void saveSession(String token, String userId, String name,
                            String email, String role, int age) {
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_ROLE, role);
        editor.putInt(KEY_AGE, age);
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, "patient");
    }

    public int getAge() {
        return prefs.getInt(KEY_AGE, 0);
    }

    public boolean isAdmin() {
        return "admin".equals(getRole());
    }

    public boolean isDoctor() {
        return "doctor".equals(getRole());
    }

    /** Call on logout */
    public void clearSession() {
        editor.clear();
        editor.commit(); // Use commit() for synchronous clearing to avoid race conditions during logout/401 handling
    }
}

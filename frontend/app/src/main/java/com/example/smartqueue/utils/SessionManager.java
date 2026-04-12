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
    private static final String KEY_STAFF_ID = "staffId";
    private static final String KEY_SPECIALTY = "specialty";
    private static final String KEY_KEEP_SESSION_ACTIVE = "keepSessionActive";
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
        saveSession(token, userId, name, email, role, age, null, null, true);
    }

    /** Extended save including staffId and specialty for clinical staff. */
    public void saveSession(String token, String userId, String name,
                            String email, String role, int age,
                            String staffId, String specialty) {
        saveSession(token, userId, name, email, role, age, staffId, specialty, true);
    }

    /** Extended save including session-persistence preference. */
    public void saveSession(String token, String userId, String name,
                            String email, String role, int age,
                            String staffId, String specialty,
                            boolean keepSessionActive) {
        editor.putString(KEY_TOKEN, token);
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_ROLE, role);
        editor.putInt(KEY_AGE, age);
        editor.putString(KEY_STAFF_ID, staffId != null ? staffId : "");
        editor.putString(KEY_SPECIALTY, specialty != null ? specialty : "");
        editor.putBoolean(KEY_KEEP_SESSION_ACTIVE, keepSessionActive);
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

    /** Returns the staff ID string (e.g. "DOC-0001") or empty string for patients. */
    public String getStaffId() {
        return prefs.getString(KEY_STAFF_ID, "");
    }

    /** Returns the doctor's specialty or empty string for non-doctors. */
    public String getSpecialty() {
        return prefs.getString(KEY_SPECIALTY, "");
    }

    public boolean shouldKeepSessionActive() {
        return prefs.getBoolean(KEY_KEEP_SESSION_ACTIVE, true);
    }

    public boolean hasRestorableSession() {
        return isLoggedIn() && shouldKeepSessionActive() && getToken() != null;
    }

    public boolean isAdmin() {
        return "admin".equals(getRole());
    }

    public boolean isDoctor() {
        return "doctor".equals(getRole());
    }

    public boolean isNurse() {
        return "nurse".equals(getRole());
    }

    public boolean isSuperuser() {
        return "superuser".equals(getRole());
    }

    /** Returns true if the user has admin-level access (admin or superuser). */
    public boolean hasAdminAccess() {
        String role = getRole();
        return "admin".equals(role) || "superuser".equals(role);
    }

    /** Call on logout */
    public void clearSession() {
        editor.clear();
        editor.commit(); // Use commit() for synchronous clearing to avoid race conditions during logout/401 handling
    }
}

package com.example.smartqueue.utils;

import android.content.Context;
import android.content.Intent;

import com.example.smartqueue.ui.admin.AdminDashboardActivity;
import com.example.smartqueue.ui.doctor.DoctorHomeActivity;
import com.example.smartqueue.ui.patient.PatientHomeActivity;

/**
 * Centralizes role-to-home routing so saved-session restore and fresh login
 * always land on the same screen for the same role.
 */
public final class RoleNavigationHelper {

    private RoleNavigationHelper() {}

    public static String normalizeRole(String role) {
        return role == null ? "patient" : role.trim().toLowerCase();
    }

    public static Intent createHomeIntent(Context context, String role) {
        switch (normalizeRole(role)) {
            case "admin":
            case "superuser":
                return new Intent(context, AdminDashboardActivity.class);
            case "doctor":
                return new Intent(context, DoctorHomeActivity.class);
            default:
                return new Intent(context, PatientHomeActivity.class);
        }
    }

    public static Intent createClearedHomeIntent(Context context, String role) {
        Intent intent = createHomeIntent(context, role);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }
}

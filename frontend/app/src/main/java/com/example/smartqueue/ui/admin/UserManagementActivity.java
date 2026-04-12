package com.example.smartqueue.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.CreateUserRequest;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.UserListResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * User Management screen — lets admins and superusers view, create, and delete
 * doctors, nurses, patients (and admins/superusers for superuser role only).
 *
 * Staff members (doctor / nurse) display their auto-generated Staff ID badge
 * (e.g. DOC-0001, NRS-0002) throughout this screen.
 */
public class UserManagementActivity extends AppCompatActivity {

    private static final String ELEVATED_ROLE_FILTER = "__elevated__";
    private static final Map<String, String> SPECIALTY_CANONICAL_MAP = buildSpecialtyCanonicalMap();

    private ChipGroup chipGroupFilter;
    private Chip chipAll, chipDoctors, chipNurses, chipPatients, chipAdmins;
    private LinearLayout layoutUserList;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvTitle;
    private MaterialButton btnAddUser, btnBack;

    private SessionManager sessionManager;
    private ApiService apiService;
    private boolean isSuperuser = false;

    private int colorSurfaceContainerLow;
    private int colorOutlineVariant;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorPrimary;
    private int colorPriorityHigh;
    private int colorWhite;

    /** Currently selected role filter. Empty string = all. */
    private String currentRoleFilter = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_management);

        sessionManager = new SessionManager(this);
        if (!sessionManager.hasAdminAccess()) {
            startActivity(RoleNavigationHelper.createClearedHomeIntent(this, sessionManager.getRole()));
            finish();
            return;
        }
        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);
        isSuperuser = sessionManager.isSuperuser();

        bindViews();
        setupClickListeners();
        loadUsers("");
    }

    private void bindViews() {
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        chipAll         = findViewById(R.id.chipAll);
        chipDoctors     = findViewById(R.id.chipDoctors);
        chipNurses      = findViewById(R.id.chipNurses);
        chipPatients    = findViewById(R.id.chipPatients);
        chipAdmins      = findViewById(R.id.chipAdmins);
        layoutUserList  = findViewById(R.id.layoutUserList);
        progressBar     = findViewById(R.id.progressBar);
        tvEmpty         = findViewById(R.id.tvEmpty);
        tvTitle         = findViewById(R.id.tvTitle);
        btnAddUser      = findViewById(R.id.btnAddUser);
        btnBack         = findViewById(R.id.btnBack);

        colorSurfaceContainerLow = ContextCompat.getColor(this, R.color.surface_container_low);
        colorOutlineVariant = ContextCompat.getColor(this, R.color.outline_variant);
        colorTextPrimary = ContextCompat.getColor(this, R.color.text_primary);
        colorTextSecondary = ContextCompat.getColor(this, R.color.text_secondary);
        colorPrimary = ContextCompat.getColor(this, R.color.primary);
        colorPriorityHigh = ContextCompat.getColor(this, R.color.priority_high);
        colorWhite = ContextCompat.getColor(this, R.color.white);

        // Only superusers can see admin accounts
        chipAdmins.setVisibility(isSuperuser ? View.VISIBLE : View.GONE);
        if (isSuperuser) {
            chipAdmins.setText("Admins & Superusers");
        }

        tvTitle.setText(isSuperuser ? "User Management (Superuser)" : "User Management");
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());

        btnAddUser.setOnClickListener(v -> showCreateUserDialog());

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                currentRoleFilter = "";
                loadUsers("");
                return;
            }
            int id = checkedIds.get(0);
            if (id == R.id.chipAll)      currentRoleFilter = "";
            else if (id == R.id.chipDoctors)  currentRoleFilter = "doctor";
            else if (id == R.id.chipNurses)   currentRoleFilter = "nurse";
            else if (id == R.id.chipPatients) currentRoleFilter = "patient";
            else if (id == R.id.chipAdmins)   currentRoleFilter = ELEVATED_ROLE_FILTER;
            loadUsers(currentRoleFilter);
        });
    }

    // ── Load Users ────────────────────────────────────────────

    private void loadUsers(String roleFilter) {
        progressBar.setVisibility(View.VISIBLE);
        layoutUserList.removeAllViews();
        showStatus("Loading users...");

        String filterParam = TextUtils.isEmpty(roleFilter) || ELEVATED_ROLE_FILTER.equals(roleFilter)
                ? null : roleFilter;

        apiService.listUsers(filterParam, null, 1, 100).enqueue(
                new Callback<UserListResponse>() {
                    @Override
                    public void onResponse(Call<UserListResponse> call,
                                           Response<UserListResponse> response) {
                        if (isFinishing() || isDestroyed()) return;
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {
                            List<UserListResponse.UserEntry> users = response.body().getUsers();
                            if (ELEVATED_ROLE_FILTER.equals(roleFilter)) {
                                users = filterElevatedUsers(users);
                            }
                            if (users == null || users.isEmpty()) {
                                showStatus("No users found.");
                            } else {
                                tvEmpty.setVisibility(View.GONE);
                                renderUserCards(users);
                            }
                        } else {
                            showStatus("Failed to load users.");
                        }
                    }

                    @Override
                    public void onFailure(Call<UserListResponse> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        progressBar.setVisibility(View.GONE);
                        showStatus("Network error. Check connection.");
                    }
                });
    }

    // ── Build user card ───────────────────────────────────────

    private View buildUserCard(UserListResponse.UserEntry user) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        int margin = dp(8);
        card.setPadding(pad, pad, pad, pad);

        // Card background
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(colorSurfaceContainerLow);
        bg.setStroke(dp(1), colorOutlineVariant);
        card.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, margin);
        card.setLayoutParams(lp);

        // ── Row 1: name + role badge ──────────────────────
        LinearLayout row1 = row();
        TextView tvName = text(user.getDisplayLabel(), 15, true, colorTextPrimary);
        tvName.setLayoutParams(weightedLP(1));
        row1.addView(tvName);

        TextView tvRoleBadge = roleBadge(user.getRoleBadge(), user.getRole());
        row1.addView(tvRoleBadge);
        card.addView(row1);

        // ── Row 2: staff ID (if present) ─────────────────
        if (user.getStaffId() != null && !user.getStaffId().isEmpty()) {
            TextView tvStaffId = text("Staff ID: " + user.getStaffId(), 12, false, colorPrimary);
            tvStaffId.setPadding(0, dp(2), 0, dp(2));
            card.addView(tvStaffId);
        }

        // ── Row 3: specialty (doctors) ────────────────────
        if ("doctor".equals(user.getRole())
                && user.getSpecialty() != null && !user.getSpecialty().isEmpty()) {
            card.addView(text("Specialty: " + user.getSpecialty(), 12, false, colorTextSecondary));
        }

        // ── Row 4: email + phone ──────────────────────────
        card.addView(text(user.getEmail() + "  ·  " + user.getPhone(), 12, false, colorTextSecondary));

        // ── Row 5: age + delete button ────────────────────
        LinearLayout row5 = row();
        row5.setPadding(0, dp(4), 0, 0);
        TextView tvAge = text("Age: " + user.getAge(), 12, false, colorTextSecondary);
        tvAge.setLayoutParams(weightedLP(1));
        row5.addView(tvAge);

        AppCompatButton btnDelete = new AppCompatButton(this);
        btnDelete.setText("Delete");
        btnDelete.setTextSize(12);
        btnDelete.setTextColor(colorPriorityHigh);
        btnDelete.setPadding(dp(8), dp(2), dp(8), dp(2));
        btnDelete.setAllCaps(false);
        btnDelete.setBackground(buildDeleteButtonBackground());
        btnDelete.setMinWidth(0);
        btnDelete.setMinHeight(0);
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(32));
        btnDelete.setLayoutParams(deleteLp);
        btnDelete.setOnClickListener(v -> confirmDelete(user));
        row5.addView(btnDelete);
        card.addView(row5);

        return card;
    }

    private void renderUserCards(List<UserListResponse.UserEntry> users) {
        layoutUserList.removeAllViews();
        for (UserListResponse.UserEntry user : users) {
            try {
                layoutUserList.addView(buildUserCard(user));
            } catch (RuntimeException error) {
                android.util.Log.e("UserManagement", "Failed to render user card", error);
                layoutUserList.addView(buildFallbackUserCard(user));
            }
        }
    }

    private View buildFallbackUserCard(UserListResponse.UserEntry user) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(12);
        card.setPadding(pad, pad, pad, pad);
        card.setBackground(buildCardBackground());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);

        String name = user != null ? user.getDisplayLabel() : "Unknown user";
        String role = user != null ? user.getRoleBadge() : "USER";
        String email = user != null && user.getEmail() != null ? user.getEmail() : "—";
        String phone = user != null && user.getPhone() != null ? user.getPhone() : "—";

        card.addView(text(name, 15, true, colorTextPrimary));
        card.addView(text(role, 11, true, roleBadgeColor(user != null ? user.getRole() : null)));
        card.addView(text(email + "  ·  " + phone, 12, false, colorTextSecondary));
        return card;
    }

    // ── Delete confirmation ───────────────────────────────────

    private void confirmDelete(UserListResponse.UserEntry user) {
        new AlertDialog.Builder(this)
                .setTitle("Delete " + user.getRoleBadge() + "?")
                .setMessage("Are you sure you want to permanently delete " + user.getName()
                        + (user.getStaffId() != null && !user.getStaffId().isEmpty()
                           ? " (" + user.getStaffId() + ")" : "")
                        + "?\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> deleteUser(String.valueOf(user.getId()), user.getName()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteUser(String userId, String name) {
        apiService.deleteUser(userId).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    Toast.makeText(UserManagementActivity.this,
                            name + " deleted.", Toast.LENGTH_SHORT).show();
                    loadUsers(currentRoleFilter);
                } else {
                    String msg = (response.body() != null && response.body().getMessage() != null)
                            ? response.body().getMessage() : "Delete failed.";
                    Toast.makeText(UserManagementActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(UserManagementActivity.this,
                        "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Create User dialog ────────────────────────────────────

    private void showCreateUserDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_create_user, null);

        TextInputEditText etName     = dialogView.findViewById(R.id.etDuName);
        TextInputEditText etEmail    = dialogView.findViewById(R.id.etDuEmail);
        TextInputEditText etPhone    = dialogView.findViewById(R.id.etDuPhone);
        TextInputEditText etAge      = dialogView.findViewById(R.id.etDuAge);
        TextInputEditText etPassword = dialogView.findViewById(R.id.etDuPassword);
        Spinner spinnerRole          = dialogView.findViewById(R.id.spinnerRole);
        TextInputLayout tilSpecialty = dialogView.findViewById(R.id.tilDuSpecialty);
        AutoCompleteTextView etSpecialty = dialogView.findViewById(R.id.etDuSpecialty);
        MaterialButton btnCreate = dialogView.findViewById(R.id.btnDuCreate);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDuCancel);
        etPhone.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(10) });

        List<String> specialtyOptions = Arrays.asList(
            "Cardiology",
            "Orthopaedics",
            "Neurology",
            "Dermatology",
            "Gastroenterology",
            "Paediatrics",
            "Pulmonology",
            "General OPD",
            "Infectious Disease",
            "Otolaryngology (ENT)",
            "Hematology",
            "Endocrinology",
            "Nephrology / Urology",
            "Emergency Medicine"
        );
        ArrayAdapter<String> specialtyAdapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            specialtyOptions
        );
        etSpecialty.setAdapter(specialtyAdapter);
        etSpecialty.setThreshold(0);
        etSpecialty.setOnClickListener(v -> etSpecialty.showDropDown());
        etSpecialty.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                etSpecialty.showDropDown();
            }
        });

        // Populate role spinner based on current user's permission level
        List<String> roles = new ArrayList<>();
        roles.add("doctor");
        roles.add("nurse");
        roles.add("patient");
        if (isSuperuser) {
            roles.add("admin");
            roles.add("superuser");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, roles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(adapter);

        // Show specialty field only for doctors
        spinnerRole.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                tilSpecialty.setVisibility(
                        "doctor".equals(roles.get(position)) ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add New User")
                .setView(dialogView)
            .setCancelable(true)
            .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnCreate.setOnClickListener(v -> {
                    String name      = etName.getText()     != null ? etName.getText().toString().trim()     : "";
                    String email     = etEmail.getText()    != null ? etEmail.getText().toString().trim()    : "";
                    String phone     = etPhone.getText()    != null ? etPhone.getText().toString().trim()    : "";
                    String ageStr    = etAge.getText()      != null ? etAge.getText().toString().trim()      : "";
                    String password  = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
                String specialty = canonicalizeSpecialty(
                    etSpecialty.getText() != null ? etSpecialty.getText().toString().trim() : ""
                );
                    String role      = roles.get(spinnerRole.getSelectedItemPosition());

                    if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)
                            || TextUtils.isEmpty(phone) || TextUtils.isEmpty(ageStr)
                            || TextUtils.isEmpty(password)) {
                        Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        Toast.makeText(this, "Invalid email.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (phone.length() != 10 || !TextUtils.isDigitsOnly(phone)) {
                        Toast.makeText(this, "Phone number must be exactly 10 digits.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int age;
                    try {
                        age = Integer.parseInt(ageStr);
                        if (age < 1 || age > 120) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Invalid age (1–120).", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (password.length() < 6) {
                        Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    submitCreateUser(new CreateUserRequest(name, email, password, phone, age, role,
                            "doctor".equals(role) ? specialty : null));
                    dialog.dismiss();
                });

        dialog.show();
    }

    private String canonicalizeSpecialty(String specialty) {
        if (TextUtils.isEmpty(specialty)) {
            return "";
        }

        String trimmed = specialty.trim();
        String mapped = SPECIALTY_CANONICAL_MAP.get(trimmed.toLowerCase(Locale.US));
        return mapped != null ? mapped : trimmed;
    }

    private static Map<String, String> buildSpecialtyCanonicalMap() {
        Map<String, String> map = new HashMap<>();
        map.put("cardiologist", "Cardiology");
        map.put("cardiology", "Cardiology");
        map.put("orthopedics", "Orthopaedics");
        map.put("orthopaedics", "Orthopaedics");
        map.put("neurologist", "Neurology");
        map.put("neurology", "Neurology");
        map.put("dermatologist", "Dermatology");
        map.put("dermatology", "Dermatology");
        map.put("gastroenterologist", "Gastroenterology");
        map.put("gastroenterology", "Gastroenterology");
        map.put("pediatrician", "Paediatrics");
        map.put("paediatrics", "Paediatrics");
        map.put("paediatrics", "Paediatrics");
        map.put("pulmonologist", "Pulmonology");
        map.put("pulmonology", "Pulmonology");
        map.put("ent", "Otolaryngology (ENT)");
        map.put("otolaryngology (ent)", "Otolaryngology (ENT)");
        map.put("hematology", "Hematology");
        map.put("endocrinology", "Endocrinology");
        map.put("nephrology", "Nephrology / Urology");
        map.put("urology", "Nephrology / Urology");
        map.put("emergency medicine", "Emergency Medicine");
        map.put("general opd", "General OPD");
        map.put("general practice", "General OPD");
        return map;
    }

    private void submitCreateUser(CreateUserRequest req) {
        apiService.createUser(req).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    Toast.makeText(UserManagementActivity.this,
                            response.body().getMessage(), Toast.LENGTH_LONG).show();
                    loadUsers(currentRoleFilter);
                } else {
                    String msg = (response.body() != null && response.body().getMessage() != null)
                            ? response.body().getMessage() : "Failed to create user.";
                    Toast.makeText(UserManagementActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(UserManagementActivity.this,
                        "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── UI helpers ─────────────────────────────────────────────

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String content, float size, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(content);
        tv.setTextSize(size);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private TextView roleBadge(String label, String role) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(10);
        tv.setTextColor(colorWhite);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(dp(6), dp(2), dp(6), dp(2));

        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(4));
        bg.setColor(roleBadgeColor(role));
        tv.setBackground(bg);
        return tv;
    }

    private int roleBadgeColor(String role) {
        if (role == null) return colorOutlineVariant;
        switch (role.toLowerCase()) {
            case "doctor":    return colorPrimary;
            case "nurse":     return ContextCompat.getColor(this, R.color.status_active);
            case "admin":     return ContextCompat.getColor(this, R.color.secondary_container);
            case "superuser": return colorPriorityHigh;
            default:            return colorOutlineVariant;
        }
    }

    private List<UserListResponse.UserEntry> filterElevatedUsers(List<UserListResponse.UserEntry> users) {
        List<UserListResponse.UserEntry> filtered = new ArrayList<>();
        if (users == null) return filtered;

        for (UserListResponse.UserEntry user : users) {
            if (user == null || user.getRole() == null) continue;
            String role = user.getRole().toLowerCase();
            if ("admin".equals(role) || "superuser".equals(role)) {
                filtered.add(user);
            }
        }
        return filtered;
    }

    private LinearLayout.LayoutParams weightedLP(int weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        return lp;
    }

    private void showStatus(String message) {
        tvEmpty.setText(message);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private android.graphics.drawable.GradientDrawable buildCardBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(colorSurfaceContainerLow);
        bg.setStroke(dp(1), colorOutlineVariant);
        return bg;
    }

    private android.graphics.drawable.GradientDrawable buildDeleteButtonBackground() {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(ContextCompat.getColor(this, R.color.transparent));
        bg.setStroke(dp(1), colorPriorityHigh);
        return bg;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}

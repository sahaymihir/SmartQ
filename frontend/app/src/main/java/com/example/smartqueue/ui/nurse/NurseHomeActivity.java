package com.example.smartqueue.ui.nurse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.NurseTriageRequest;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.ApiErrorParser;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NurseHomeActivity extends AppCompatActivity {

    private static final long NURSE_POLL_INTERVAL_MS = 10_000L;

    private TextView tvNurseName;
    private TextView tvQueueSummary;
    private TextView tvQueueSubSummary;
    private TextView tvQueueEmpty;
    private LinearLayout layoutQueueList;
    private ProgressBar progressBar;
    private MaterialButton btnRefresh;
    private MaterialButton btnLogout;

    private View cardLastResult;
    private TextView tvLastResultPatient;
    private TextView tvLastResultPriority;
    private TextView tvLastResultBreakdown;
    private View layoutSafetyAlertBlock;
    private TextView tvLastResultSafetyAlert;
    private TextView tvLastResultSafetySummary;
    private TextView tvLastResultQueue;
    private TextView tvLastResultRationale;

    private SessionManager sessionManager;
    private ApiService apiService;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nurse_home);

        sessionManager = new SessionManager(this);
        String role = RoleNavigationHelper.normalizeRole(sessionManager.getRole());
        if (!"nurse".equals(role)) {
            startActivity(RoleNavigationHelper.createClearedHomeIntent(this, sessionManager.getRole()));
            finish();
            return;
        }

        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        setupListeners();
        tvNurseName.setText(sessionManager.getName());
        loadNurseQueue(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNurseQueue(false);
        startAutoRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    @Override
    protected void onDestroy() {
        stopAutoRefresh();
        super.onDestroy();
    }

    private void bindViews() {
        tvNurseName = findViewById(R.id.tvNurseName);
        tvQueueSummary = findViewById(R.id.tvQueueSummary);
        tvQueueSubSummary = findViewById(R.id.tvQueueSubSummary);
        tvQueueEmpty = findViewById(R.id.tvQueueEmpty);
        layoutQueueList = findViewById(R.id.layoutQueueList);
        progressBar = findViewById(R.id.progressBar);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnLogout = findViewById(R.id.btnLogout);

        cardLastResult = findViewById(R.id.cardLastResult);
        tvLastResultPatient = findViewById(R.id.tvLastResultPatient);
        tvLastResultPriority = findViewById(R.id.tvLastResultPriority);
        tvLastResultBreakdown = findViewById(R.id.tvLastResultBreakdown);
        layoutSafetyAlertBlock = findViewById(R.id.layoutSafetyAlertBlock);
        tvLastResultSafetyAlert = findViewById(R.id.tvLastResultSafetyAlert);
        tvLastResultSafetySummary = findViewById(R.id.tvLastResultSafetySummary);
        tvLastResultQueue = findViewById(R.id.tvLastResultQueue);
        tvLastResultRationale = findViewById(R.id.tvLastResultRationale);
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> loadNurseQueue(true));
        btnLogout.setOnClickListener(v -> {
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void loadNurseQueue(boolean showLoading) {
        if (showLoading) {
            progressBar.setVisibility(View.VISIBLE);
        }
        apiService.getNurseQueue().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    renderQueue(response.body().getQueue());
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    renderQueueError(error != null && !TextUtils.isEmpty(error.getMessage())
                            ? error.getMessage()
                            : "Could not load nurse triage board.");
                }
            }

            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                renderQueueError("Network error while loading nurse triage board.");
            }
        });
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed() && sessionManager.isLoggedIn()) {
                    loadNurseQueue(false);
                    pollHandler.postDelayed(this, NURSE_POLL_INTERVAL_MS);
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, NURSE_POLL_INTERVAL_MS);
    }

    private void stopAutoRefresh() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void renderQueue(List<QueueResponse.QueueEntry> queue) {
        layoutQueueList.removeAllViews();
        List<QueueResponse.QueueEntry> entries = queue != null ? queue : new ArrayList<>();
        int urgentCount = 0;

        for (QueueResponse.QueueEntry entry : entries) {
            if (entry.isImmediateReviewRequired() || "immediate_review".equals(entry.getRoutingLane())) {
                urgentCount++;
            }

            View row = LayoutInflater.from(this).inflate(R.layout.item_nurse_queue, layoutQueueList, false);
            TextView tvPatientName = row.findViewById(R.id.tvPatientName);
            TextView tvPatientMeta = row.findViewById(R.id.tvPatientMeta);
            TextView tvDoctorMeta = row.findViewById(R.id.tvDoctorMeta);
            TextView tvSymptoms = row.findViewById(R.id.tvSymptoms);
            TextView tvPriorityBadge = row.findViewById(R.id.tvPriorityBadge);
            TextView tvImmediateFlag = row.findViewById(R.id.tvImmediateFlag);
            MaterialButton btnCaptureVitals = row.findViewById(R.id.btnCaptureVitals);

            tvPatientName.setText(textOrDefault(entry.getPatientName(), "Patient"));
            tvPatientMeta.setText(buildQueueRowMeta(entry));
            tvDoctorMeta.setText(textOrDefault(entry.getDoctorName(), "Assigned doctor")
                    + " · " + textOrDefault(entry.getDoctorSpecialty(), "Department pending"));
            tvSymptoms.setText("Symptoms: " + textOrDefault(entry.getSymptoms(), "No symptom summary provided."));
            bindPriorityBadges(entry, tvPriorityBadge, tvImmediateFlag);
            btnCaptureVitals.setOnClickListener(v -> showTriageDialog(entry));

            layoutQueueList.addView(row);
        }

        tvQueueEmpty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        tvQueueSummary.setText(entries.isEmpty()
                ? "No patients waiting for nurse vitals"
                : String.format(Locale.getDefault(), "%d patients waiting for nurse triage", entries.size()));
        if (urgentCount > 0) {
            tvQueueSubSummary.setText(String.format(Locale.getDefault(),
                "%d patients are already flagged for immediate review.", urgentCount));
            tvQueueSubSummary.setTextColor(ContextCompat.getColor(this, R.color.priority_high));
        } else {
            tvQueueSubSummary.setText("Capture vitals to move patients into the doctor queue.");
            tvQueueSubSummary.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        }
    }

    private void renderQueueError(String message) {
        layoutQueueList.removeAllViews();
        tvQueueEmpty.setVisibility(View.VISIBLE);
        tvQueueEmpty.setText(message);
        tvQueueSummary.setText("Nurse board unavailable");
        tvQueueSubSummary.setText("Please refresh or check connectivity.");
    }

    private String buildQueueRowMeta(QueueResponse.QueueEntry entry) {
        List<String> parts = new ArrayList<>();
        parts.add("Token #" + entry.getTokenNumber());
        if (entry.getPatientAge() > 0) {
            parts.add("Age " + entry.getPatientAge());
        }
        if (entry.getModelPriorityClass() != null) {
            parts.add("Raw KTAS " + entry.getModelPriorityClass());
        } else if (entry.getTriagePriorityClass() != null) {
            parts.add("KTAS " + entry.getTriagePriorityClass());
        }
        if (entry.isImmediateReviewRequired()) {
            parts.add("Immediate review flagged");
        }
        return TextUtils.join(" · ", parts);
    }

    private void showTriageDialog(QueueResponse.QueueEntry entry) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nurse_triage, null, false);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvDialogSubtitle = dialogView.findViewById(R.id.tvDialogSubtitle);
        TextInputEditText etTemperature = dialogView.findViewById(R.id.etTemperature);
        TextInputEditText etSpo2 = dialogView.findViewById(R.id.etSpo2);
        TextInputEditText etHeartRate = dialogView.findViewById(R.id.etHeartRate);
        TextInputEditText etSystolicBp = dialogView.findViewById(R.id.etSystolicBp);
        TextInputEditText etDiastolicBp = dialogView.findViewById(R.id.etDiastolicBp);
        TextInputEditText etGcsTotal = dialogView.findViewById(R.id.etGcsTotal);
        TextInputEditText etNews2Score = dialogView.findViewById(R.id.etNews2Score);
        TextInputEditText etPainScore = dialogView.findViewById(R.id.etPainScore);
        TextInputEditText etTriageNote = dialogView.findViewById(R.id.etTriageNote);
        MaterialButton btnDialogSubmit = dialogView.findViewById(R.id.btnDialogSubmit);
        MaterialButton btnDialogCancel = dialogView.findViewById(R.id.btnDialogCancel);

        setDefaultIfEmpty(etTemperature, "37.0");
        setDefaultIfEmpty(etSpo2, "98");
        setDefaultIfEmpty(etHeartRate, "80");
        setDefaultIfEmpty(etSystolicBp, "120");
        setDefaultIfEmpty(etDiastolicBp, "80");
        setDefaultIfEmpty(etGcsTotal, "15");
        setDefaultIfEmpty(etNews2Score, "0");
        setDefaultIfEmpty(etPainScore, "0");

        tvDialogTitle.setText("Submit nurse vitals");
        tvDialogSubtitle.setText(textOrDefault(entry.getPatientName(), "Patient")
                + " · Token #" + entry.getTokenNumber());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        btnDialogCancel.setOnClickListener(v -> dialog.dismiss());
        btnDialogSubmit.setOnClickListener(v -> {
            try {
                NurseTriageRequest request = new NurseTriageRequest();
                request.setTemperatureC(parseOptionalFloat(etTemperature, "temperature"));
                request.setSpo2(parseOptionalFloat(etSpo2, "SpO2"));
                request.setHeartRate(parseOptionalFloat(etHeartRate, "heart rate"));
                request.setSystolicBp(parseOptionalFloat(etSystolicBp, "systolic BP"));
                request.setDiastolicBp(parseOptionalFloat(etDiastolicBp, "diastolic BP"));
                request.setGcsTotal(parseOptionalInt(etGcsTotal, "GCS total"));
                request.setNews2Score(parseOptionalFloat(etNews2Score, "NEWS2 score"));
                request.setPainScore(parseOptionalFloat(etPainScore, "pain score"));
                request.setNurseTriageNote(textOrEmpty(etTriageNote));
                submitNurseTriage(entry, request, dialog, btnDialogSubmit);
            } catch (IllegalArgumentException ex) {
                Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void submitNurseTriage(
            QueueResponse.QueueEntry entry,
            NurseTriageRequest request,
            AlertDialog dialog,
            MaterialButton submitButton
    ) {
        submitButton.setEnabled(false);
        apiService.nurseTriageToken(entry.getTokenId(), request).enqueue(new Callback<TokenResponse>() {
            @Override
            public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                submitButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    TokenResponse body = response.body();
                    dialog.dismiss();
                    renderLastResult(entry, body);
                    Toast.makeText(NurseHomeActivity.this,
                            textOrDefault(body.getMessage(), "Nurse triage recorded."),
                            Toast.LENGTH_LONG).show();
                    loadNurseQueue(false);
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(NurseHomeActivity.this,
                            error != null && !TextUtils.isEmpty(error.getMessage())
                                    ? error.getMessage()
                                    : "Could not submit nurse vitals.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<TokenResponse> call, Throwable t) {
                submitButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(NurseHomeActivity.this,
                        "Network error while submitting nurse triage.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderLastResult(QueueResponse.QueueEntry entry, TokenResponse body) {
        cardLastResult.setVisibility(View.VISIBLE);
        tvLastResultPatient.setText(textOrDefault(entry.getPatientName(), "Patient")
                + " · Token #" + entry.getTokenNumber());
        tvLastResultPriority.setText(buildPriorityHeadline(body));
        styleLastResultPriority(body);
        tvLastResultBreakdown.setText(buildPriorityBreakdown(body));

        List<TokenResponse.SafetyMatch> safetyMatches = body.getSafetyMatches();
        boolean hasSafetyMatches = safetyMatches != null && !safetyMatches.isEmpty();
        layoutSafetyAlertBlock.setVisibility(hasSafetyMatches ? View.VISIBLE : View.GONE);
        if (hasSafetyMatches) {
            tvLastResultSafetyAlert.setText(buildSafetyAlertText(safetyMatches));
            tvLastResultSafetySummary.setText("Hard safety override fired");
        } else {
            tvLastResultSafetyAlert.setText("");
            tvLastResultSafetySummary.setText("No hard safety override fired");
        }

        tvLastResultQueue.setText(buildQueueAssignment(body));
        tvLastResultRationale.setText("Rationale: " + textOrDefault(body.getQueueRationale(), "—"));
    }

    private String buildPriorityHeadline(TokenResponse body) {
        List<String> parts = new ArrayList<>();
        parts.add(textOrDefault(body.getTriageRecommendation(), "Priority pending"));
        parts.add("raw KTAS " + (body.getModelPriorityClass() != null ? body.getModelPriorityClass() : "—"));
        parts.add("final KTAS " + (body.getTriagePriorityClass() != null ? body.getTriagePriorityClass() : "—"));
        parts.add(Math.round(body.getTriageConfidence() * 100) + "% confidence");
        return TextUtils.join(" · ", parts);
    }

    private String buildPriorityBreakdown(TokenResponse body) {
        String score = body.getPriorityFinalScore() != null
                ? String.valueOf(Math.round(body.getPriorityFinalScore()))
                : "—";
        return "complaint " + textOrDefault(body.getDerivedChiefComplaintSystem(), "—")
                + " · KTAS " + (body.getTriagePriorityClass() != null ? body.getTriagePriorityClass() : "—")
                + " · score " + score
                + " · " + textOrDefault(body.getTriageSource(), "—")
                + " · " + textOrDefault(body.getTriageRecommendation(), "—");
    }

    private String buildSafetyAlertText(List<TokenResponse.SafetyMatch> safetyMatches) {
        List<String> lines = new ArrayList<>();
        boolean requiresImmediateEscalation = false;

        for (TokenResponse.SafetyMatch match : safetyMatches) {
            Integer forcedPriorityClass = match.getForcedPriorityClass();
            if (forcedPriorityClass != null && forcedPriorityClass <= 2) {
                requiresImmediateEscalation = true;
            }
            lines.add(textOrDefault(match.getRuleId(), "rule")
                    + " · " + textOrDefault(match.getSeverity(), "unknown")
                    + " · Priority forced to KTAS "
                    + (forcedPriorityClass != null ? forcedPriorityClass : "—")
                    + " · " + textOrDefault(match.getRationale(), "No rationale provided"));
        }

        if (requiresImmediateEscalation) {
            lines.add("⚠ Immediate escalation required");
        }
        return TextUtils.join("\n", lines);
    }

    private String buildQueueAssignment(TokenResponse body) {
        String ahead = body.getQueueCurrentLength() != null
                ? String.valueOf(body.getQueueCurrentLength())
                : "—";
        String availableDoctors = body.getQueueAvailableDoctors() != null
                ? String.valueOf(body.getQueueAvailableDoctors())
                : "—";
        String wait = body.getQueueAvgWaitMinutes() != null
                ? String.valueOf(Math.round(body.getQueueAvgWaitMinutes()))
                : "—";

        return textOrDefault(body.getQueueSelectedRoute(), "—")
                + " · " + textOrDefault(body.getQueueRouteType(), "—")
                + " · " + ahead + " ahead"
                + " · " + availableDoctors + " doctors"
                + " · wait " + wait + "m";
    }

    private void bindPriorityBadges(
            QueueResponse.QueueEntry entry,
            TextView tvPriorityBadge,
            TextView tvImmediateFlag
    ) {
        String priorityLabel = resolvePriorityLabel(entry);
        tvPriorityBadge.setText(priorityLabel);

        if ("HIGH".equals(priorityLabel)) {
            tvPriorityBadge.setBackgroundResource(R.drawable.badge_high);
            tvPriorityBadge.setTextColor(ContextCompat.getColor(this, R.color.priority_high));
        } else if ("MEDIUM".equals(priorityLabel)) {
            tvPriorityBadge.setBackgroundResource(R.drawable.badge_medium);
            tvPriorityBadge.setTextColor(ContextCompat.getColor(this, R.color.priority_medium));
        } else {
            tvPriorityBadge.setBackgroundResource(R.drawable.badge_normal);
            tvPriorityBadge.setTextColor(ContextCompat.getColor(this, R.color.priority_normal));
        }

        boolean immediateReview = entry.isImmediateReviewRequired()
                || "immediate_review".equals(entry.getRoutingLane());
        tvImmediateFlag.setVisibility(immediateReview ? View.VISIBLE : View.GONE);
    }

    private String resolvePriorityLabel(QueueResponse.QueueEntry entry) {
        String priority = entry.getPriority();
        if (!TextUtils.isEmpty(priority)) {
            String normalized = priority.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("high") || normalized.contains("urgent")) return "HIGH";
            if (normalized.contains("medium") || normalized.contains("moderate")) return "MEDIUM";
            if (normalized.contains("normal") || normalized.contains("low")) return "NORMAL";
        }

        Integer cls = entry.getTriagePriorityClass() != null
                ? entry.getTriagePriorityClass()
                : entry.getModelPriorityClass();
        if (cls == null) return "NORMAL";
        if (cls <= 2) return "HIGH";
        if (cls == 3) return "MEDIUM";
        return "NORMAL";
    }

    private void styleLastResultPriority(TokenResponse body) {
        int priorityClass = body.getTriagePriorityClass() != null
                ? body.getTriagePriorityClass()
                : 4;
        if (priorityClass <= 2) {
            tvLastResultPriority.setBackgroundResource(R.drawable.badge_high);
            tvLastResultPriority.setTextColor(ContextCompat.getColor(this, R.color.priority_high));
        } else if (priorityClass == 3) {
            tvLastResultPriority.setBackgroundResource(R.drawable.badge_medium);
            tvLastResultPriority.setTextColor(ContextCompat.getColor(this, R.color.priority_medium));
        } else {
            tvLastResultPriority.setBackgroundResource(R.drawable.badge_normal);
            tvLastResultPriority.setTextColor(ContextCompat.getColor(this, R.color.priority_normal));
        }
        int hPad = dp(10);
        int vPad = dp(6);
        tvLastResultPriority.setPadding(hPad, vPad, hPad, vPad);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setDefaultIfEmpty(TextInputEditText input, String value) {
        if (input.getText() == null || TextUtils.isEmpty(input.getText().toString().trim())) {
            input.setText(value);
        }
    }

    private Float parseOptionalFloat(TextInputEditText input, String label) {
        String text = textOrEmpty(input);
        if (text.isEmpty()) return null;
        try {
            return Float.parseFloat(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + label);
        }
    }

    private Integer parseOptionalInt(TextInputEditText input, String label) {
        String text = textOrEmpty(input);
        if (text.isEmpty()) return null;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid " + label);
        }
    }

    private String textOrDefault(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private String textOrEmpty(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}

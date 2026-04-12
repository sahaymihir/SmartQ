package com.example.smartqueue.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.MlOpsLogsResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.admin.UserManagementActivity;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.ui.prescription.PrescriptionActivity;
import com.example.smartqueue.utils.ApiErrorParser;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView tvAdminName, tvCurrentlyServing, tvPausedBadge, tvUrgentAlert;
    private TextView tvStatWaiting, tvStatDone, tvStatAvg, tvQueueLabel;
    private TextView tvMlOpsStatus, tvMlOpsSummary, tvMlOpsLastEvent, tvMlOpsLastUpdated, tvMlOpsEmpty;
    private MaterialButton btnCallNext, btnPause, btnLogout, btnModelEval, btnSeedData, btnRefreshMlOps, btnManageUsers, btnPrescription;
    private LinearLayout layoutQueueList, layoutMlOpsLogs;

    private SessionManager sessionManager;
    private ApiService apiService;
    private boolean isPaused = false;
    private boolean isSuperuser = false;
    private int consultationsDone = 0;
    private final Handler queueRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable queueRefreshRunnable;
    private int lastImmediateReviewCount = 0;
    private long lastMlOpsRefreshAtMs = 0L;
    private String currentCalledTokenId = null;

    private static final long ML_OPS_REFRESH_INTERVAL_MS = 30_000L;
    private static final SimpleDateFormat API_TIMESTAMP_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);
    private static final SimpleDateFormat DISPLAY_TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // The admin's own doctor ID — set after login
    // This is the MongoDB _id of the logged-in admin user
    private String doctorId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        sessionManager = new SessionManager(this);
        if (!sessionManager.hasAdminAccess()) {
            startActivity(RoleNavigationHelper.createClearedHomeIntent(this, sessionManager.getRole()));
            finish();
            return;
        }
        isSuperuser = sessionManager.isSuperuser();
        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);
        doctorId = sessionManager.getUserId(); // admin's own _id is their doctorId

        bindViews();
        setupClickListeners();
        loadQueue();
        loadMlOpsLogs(true);
    }

    private void bindViews() {
        tvAdminName        = findViewById(R.id.tvAdminName);
        tvCurrentlyServing = findViewById(R.id.tvCurrentlyServing);
        tvPausedBadge      = findViewById(R.id.tvPausedBadge);
        tvUrgentAlert      = findViewById(R.id.tvUrgentAlert);
        tvStatWaiting      = findViewById(R.id.tvStatWaiting);
        tvStatDone         = findViewById(R.id.tvStatDone);
        tvStatAvg          = findViewById(R.id.tvStatAvg);
        tvQueueLabel       = findViewById(R.id.tvQueueLabel);
        layoutQueueList    = findViewById(R.id.layoutQueueList);
        btnCallNext        = findViewById(R.id.btnCallNext);
        btnPrescription    = findViewById(R.id.btnPrescription);
        btnPause           = findViewById(R.id.btnPause);
        btnLogout          = findViewById(R.id.btnLogout);

        tvAdminName.setText(sessionManager.getName());
        btnModelEval    = findViewById(R.id.btnModelEval);
        btnSeedData     = findViewById(R.id.btnSeedData);
        btnRefreshMlOps = findViewById(R.id.btnRefreshMlOps);
        btnManageUsers  = findViewById(R.id.btnManageUsers);
        tvMlOpsStatus = findViewById(R.id.tvMlOpsStatus);
        tvMlOpsSummary = findViewById(R.id.tvMlOpsSummary);
        tvMlOpsLastEvent = findViewById(R.id.tvMlOpsLastEvent);
        tvMlOpsLastUpdated = findViewById(R.id.tvMlOpsLastUpdated);
        tvMlOpsEmpty = findViewById(R.id.tvMlOpsEmpty);
        layoutMlOpsLogs = findViewById(R.id.layoutMlOpsLogs);

        btnSeedData.setVisibility(isSuperuser ? View.VISIBLE : View.GONE);
        btnPrescription.setEnabled(false);
        btnPrescription.setAlpha(0.6f);
    }

    private void setupClickListeners() {

        // ── Call Next ─────────────────────────────────────
        btnCallNext.setOnClickListener(v -> {
            btnCallNext.setEnabled(false);
            apiService.callNextPatient(doctorId).enqueue(new Callback<MessageResponse>() {
                @Override
                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                    btnCallNext.setEnabled(true);
                    if (response.isSuccessful() && response.body() != null) {
                        consultationsDone++;
                        tvStatDone.setText(String.valueOf(consultationsDone));
                        tvCurrentlyServing.setText(response.body().getMessage());
                        Toast.makeText(AdminDashboardActivity.this,
                                response.body().getMessage(), Toast.LENGTH_SHORT).show();
                        loadQueue();
                    } else {
                        MessageResponse error = ApiErrorParser.parseMessage(response);
                        if (error != null && error.isRequiresPrescription() && error.getTokenId() != null) {
                            Toast.makeText(AdminDashboardActivity.this,
                                    error.getMessage(), Toast.LENGTH_SHORT).show();
                            openPrescriptionEditor(error.getTokenId());
                        } else {
                            Toast.makeText(AdminDashboardActivity.this,
                                    error != null && !TextUtils.isEmpty(error.getMessage())
                                            ? error.getMessage()
                                            : "Could not advance queue",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                @Override
                public void onFailure(Call<MessageResponse> call, Throwable t) {
                    btnCallNext.setEnabled(true);
                    Toast.makeText(AdminDashboardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        btnPrescription.setOnClickListener(v -> openPrescriptionEditor(currentCalledTokenId));

        // ── Pause / Resume ────────────────────────────────
        btnPause.setOnClickListener(v -> {
            apiService.togglePause(doctorId, !isPaused).enqueue(new Callback<MessageResponse>() {
                @Override
                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        isPaused = !isPaused;
                        btnPause.setText(isPaused ? "▶ Resume" : "⏸ Pause");
                        tvPausedBadge.setVisibility(isPaused ? View.VISIBLE : View.GONE);
                        Toast.makeText(AdminDashboardActivity.this,
                                response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<MessageResponse> call, Throwable t) {
                    Toast.makeText(AdminDashboardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // ── Logout ────────────────────────────────────────
        btnLogout.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Logout")
                        .setMessage("Are you sure?")
                        .setPositiveButton("Logout", (d, w) -> {
                            sessionManager.clearSession();
                            startActivity(new Intent(this, LoginActivity.class));
                            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        // ── Model Evaluation ──────────────────────────────
        btnModelEval.setOnClickListener(v -> {
            startActivity(new Intent(this, ModelEvalActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });

        // ── Seed Dummy Data ───────────────────────────────
        if (isSuperuser) {
            btnSeedData.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Reset & Seed Demo Data")
                            .setMessage("This will clear the current SmartQ database and recreate a full demo dataset: 1 superadmin, 4 admins, 10 doctors, 6 nurses, 26 patients, live queues, completed visits, prescriptions, and model-eval history. Continue?")
                            .setPositiveButton("Reset & Seed", (d, w) -> {
                                btnSeedData.setEnabled(false);
                                apiService.seedDummyData().enqueue(new Callback<MessageResponse>() {
                                    @Override
                                    public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                                        btnSeedData.setEnabled(true);
                                        if (response.isSuccessful() && response.body() != null) {
                                            Toast.makeText(AdminDashboardActivity.this,
                                                    response.body().getMessage(), Toast.LENGTH_LONG).show();
                                        } else {
                                            String message = (response.body() != null && !TextUtils.isEmpty(response.body().getMessage()))
                                                    ? response.body().getMessage()
                                                    : "Seed failed. Superuser access required.";
                                            Toast.makeText(AdminDashboardActivity.this, message, Toast.LENGTH_LONG).show();
                                        }
                                    }
                                    @Override
                                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                                        btnSeedData.setEnabled(true);
                                        Toast.makeText(AdminDashboardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );
        }

        btnRefreshMlOps.setOnClickListener(v -> loadMlOpsLogs(true));

        // ── Emergency Patient ─────────────────────────────
        MaterialButton btnEmergencyPatient = findViewById(R.id.btnEmergencyPatient);
        btnEmergencyPatient.setOnClickListener(v -> showEmergencyDialog());

        // ── Manage Users ──────────────────────────────────
        btnManageUsers.setOnClickListener(v ->
                startActivity(new Intent(this,
                        UserManagementActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadQueue();
        startQueueRefresh();
    }

    @Override
    protected void onPause() {
        stopQueueRefresh();
        super.onPause();
    }

    private void startQueueRefresh() {
        stopQueueRefresh();
        queueRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadQueue();
                queueRefreshHandler.postDelayed(this, 10000);
            }
        };
        queueRefreshHandler.postDelayed(queueRefreshRunnable, 10000);
    }

    private void stopQueueRefresh() {
        if (queueRefreshRunnable != null) {
            queueRefreshHandler.removeCallbacks(queueRefreshRunnable);
        }
    }

    private void openPrescriptionEditor(String tokenId) {
        if (TextUtils.isEmpty(tokenId)) {
            Toast.makeText(this, "No active consultation selected", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PrescriptionActivity.class);
        intent.putExtra(PrescriptionActivity.EXTRA_TOKEN_ID, tokenId);
        intent.putExtra(PrescriptionActivity.EXTRA_READ_ONLY, false);
        startActivity(intent);
    }

    private void loadQueue() {
        renderQueuePlaceholder("Loading queue...");
        apiService.getAdminQueue(doctorId).enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    List<QueueResponse.QueueEntry> queue = body.getQueue();
                    if (queue == null) {
                        queue = new ArrayList<>();
                    }

                    isPaused = body.isPaused();
                    btnPause.setText(isPaused ? "▶ Resume" : "⏸ Pause");
                    tvPausedBadge.setVisibility(isPaused ? View.VISIBLE : View.GONE);

                    int normalWaitingCount = 0;
                    int immediateReviewCount = 0;
                    QueueResponse.QueueEntry calledPatient = null;
                    for (QueueResponse.QueueEntry entry : queue) {
                        if ("called".equals(entry.getStatus()) && calledPatient == null) {
                            calledPatient = entry;
                        }
                        if (!"waiting".equals(entry.getStatus())) continue;
                        if (isImmediateReview(entry)) immediateReviewCount++;
                        else normalWaitingCount++;
                    }
                    int totalWaitingCount = normalWaitingCount + immediateReviewCount;

                    tvStatWaiting.setText(String.valueOf(totalWaitingCount));
                    tvStatAvg.setText(body.getAvgConsultationMinutes() + "m");
                    tvQueueLabel.setText(immediateReviewCount > 0
                            ? normalWaitingCount + " waiting • " + immediateReviewCount + " immediate review"
                            : totalWaitingCount + " waiting");
                    currentCalledTokenId = calledPatient != null ? calledPatient.getTokenId() : null;
                    btnPrescription.setEnabled(currentCalledTokenId != null);
                    btnPrescription.setAlpha(currentCalledTokenId != null ? 1f : 0.6f);
                    tvCurrentlyServing.setText(calledPatient != null
                            ? "Token #" + calledPatient.getTokenNumber() + " — " + calledPatient.getPatientName()
                            : "No patient currently called");
                    updateUrgentAlert(immediateReviewCount);

                    renderQueueList(queue);
                    loadMlOpsLogs(false);
                } else {
                    renderQueuePlaceholder("Could not load queue.");
                }
            }
            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                renderQueuePlaceholder("Network error while loading queue.");
                Toast.makeText(AdminDashboardActivity.this,
                        "Could not load queue", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderQueueList(List<QueueResponse.QueueEntry> queue) {
        layoutQueueList.removeAllViews();

        if (queue.isEmpty()) {
            renderQueuePlaceholder("Queue is empty.");
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);

        for (QueueResponse.QueueEntry entry : queue) {
            View row = inflater.inflate(R.layout.item_patient_queue, layoutQueueList, false);

            TextView tvPos      = row.findViewById(R.id.tvItemPosition);
            TextView tvName     = row.findViewById(R.id.tvItemName);
            TextView tvPriority = row.findViewById(R.id.tvItemPriority);
            TextView tvEta      = row.findViewById(R.id.tvItemEta);
            MaterialButton btnNoShow = row.findViewById(R.id.btnItemNoShow);

            boolean immediateReview = isImmediateReview(entry);
            Integer ktasClass = entry.getTriagePriorityClass();

            tvPos.setText(immediateReview ? "!" : String.valueOf(Math.max(0, entry.getPosition())));
            tvName.setText(entry.getPatientName());
            tvEta.setText(buildQueueSubtitle(entry));

            if (immediateReview) {
                tvPriority.setText("IMMEDIATE");
                tvPriority.setBackgroundResource(R.drawable.badge_immediate);
                tvPos.setBackgroundResource(R.drawable.circle_priority_immediate);
            } else if (ktasClass != null) {
                tvPriority.setText("KTAS " + ktasClass);
                if (ktasClass <= 2) {
                    tvPriority.setBackgroundResource(R.drawable.badge_high);
                    tvPos.setBackgroundResource(R.drawable.circle_priority_high);
                } else if (ktasClass == 3) {
                    tvPriority.setBackgroundResource(R.drawable.badge_medium);
                    tvPos.setBackgroundResource(R.drawable.circle_primary);
                } else {
                    tvPriority.setBackgroundResource(R.drawable.badge_normal);
                    tvPos.setBackgroundResource(R.drawable.circle_primary);
                }
            } else {
                String priority = entry.getPriority();
                tvPriority.setText(capitalize(priority));
                switch (priority) {
                    case "high":
                        tvPriority.setBackgroundResource(R.drawable.badge_high);
                        tvPos.setBackgroundResource(R.drawable.circle_priority_high);
                        break;
                    case "medium":
                        tvPriority.setBackgroundResource(R.drawable.badge_medium);
                        tvPos.setBackgroundResource(R.drawable.circle_primary);
                        break;
                    default:
                        tvPriority.setBackgroundResource(R.drawable.badge_normal);
                        tvPos.setBackgroundResource(R.drawable.circle_primary);
                        break;
                }
            }

            final String tokenId = entry.getTokenId();
            final String name    = entry.getPatientName();
            btnNoShow.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Mark No-Show?")
                            .setMessage(name + " will be removed from the queue.")
                            .setPositiveButton("Confirm", (d, w) -> {
                                apiService.markNoShow(tokenId).enqueue(new Callback<MessageResponse>() {
                                    @Override
                                    public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                                        Toast.makeText(AdminDashboardActivity.this,
                                                name + " marked as no-show", Toast.LENGTH_SHORT).show();
                                        loadQueue();
                                    }
                                    @Override
                                    public void onFailure(Call<MessageResponse> call, Throwable t) {
                                        Toast.makeText(AdminDashboardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show()
            );

            layoutQueueList.addView(row);
        }
    }

    private void renderQueuePlaceholder(String message) {
        layoutQueueList.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(16f);
        tv.setPadding(0, 80, 0, 80);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTextColor(getResources().getColor(R.color.text_secondary));
        layoutQueueList.addView(tv);
    }

    private boolean isImmediateReview(QueueResponse.QueueEntry entry) {
        return entry != null && (entry.isImmediateReviewRequired()
                || "immediate_review".equals(entry.getRoutingLane()));
    }

    private void updateUrgentAlert(int immediateReviewCount) {
        if (immediateReviewCount > 0) {
            tvUrgentAlert.setVisibility(View.VISIBLE);
            tvUrgentAlert.setText(immediateReviewCount == 1
                    ? "1 patient needs immediate review"
                    : immediateReviewCount + " patients need immediate review");
            if (immediateReviewCount > lastImmediateReviewCount) {
                Toast.makeText(this, "Immediate review patient added to the queue", Toast.LENGTH_LONG).show();
            }
        } else {
            tvUrgentAlert.setVisibility(View.GONE);
        }
        lastImmediateReviewCount = immediateReviewCount;
    }

    private String buildQueueSubtitle(QueueResponse.QueueEntry entry) {
        ArrayList<String> parts = new ArrayList<>();

        if (isImmediateReview(entry)) {
            parts.add("Immediate review");
        } else if ("called".equals(entry.getStatus())) {
            parts.add("In consultation");
        } else {
            int eta = entry.getEtaMinutes();
            parts.add(eta == 0 ? "Next up" : "~" + eta + " min");
        }

        if (entry.getTriagePriorityClass() != null) {
            parts.add("KTAS " + entry.getTriagePriorityClass());
        }
        if (entry.isManualReviewRequired()) {
            parts.add("Manual review");
        }

        String reason = prettifyReason(!TextUtils.isEmpty(entry.getEscalationReason())
                ? entry.getEscalationReason()
                : entry.getOverrideReason());
        if (!TextUtils.isEmpty(reason) && isImmediateReview(entry)) {
            parts.add(reason);
        }

        return TextUtils.join(" • ", parts);
    }

    private String prettifyReason(String reason) {
        if (TextUtils.isEmpty(reason)) return "";
        return capitalize(reason.replace('_', ' '));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void loadMlOpsLogs(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && (now - lastMlOpsRefreshAtMs) < ML_OPS_REFRESH_INTERVAL_MS) {
            return;
        }
        lastMlOpsRefreshAtMs = now;

        btnRefreshMlOps.setEnabled(false);
        if (force) {
            btnRefreshMlOps.setText("Refreshing...");
        }

        apiService.getMlOpsLogs(8).enqueue(new Callback<MlOpsLogsResponse>() {
            @Override
            public void onResponse(Call<MlOpsLogsResponse> call, Response<MlOpsLogsResponse> response) {
                btnRefreshMlOps.setEnabled(true);
                btnRefreshMlOps.setText("Refresh");

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    renderMlOpsDiagnostics(response.body());
                } else if (force) {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Could not load ML diagnostics", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MlOpsLogsResponse> call, Throwable t) {
                btnRefreshMlOps.setEnabled(true);
                btnRefreshMlOps.setText("Refresh");
                if (force) {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Network error while loading ML diagnostics", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void renderMlOpsDiagnostics(MlOpsLogsResponse body) {
        MlOpsLogsResponse.Summary summary = body.getSummary();
        List<MlOpsLogsResponse.LogEntry> logs = body.getLogs();
        if (logs == null) {
            logs = new ArrayList<>();
        }

        int total = summary != null ? summary.getTotalRequests() : 0;
        int success = summary != null ? summary.getSuccessfulRequests() : 0;
        int failed = summary != null ? summary.getFailedRequests() : 0;
        int retries = summary != null ? summary.getRetryEvents() : 0;
        int recovered = summary != null ? summary.getRetryRecoveredRequests() : 0;
        double successRate = summary != null ? summary.getSuccessRate() : 0;

        tvMlOpsSummary.setText(String.format(
                Locale.getDefault(),
                "Requests: %d | Success: %d | Failed: %d | Retry events: %d | Recovered: %d | Success rate: %.1f%%",
                total,
                success,
                failed,
                retries,
                recovered,
                successRate
        ));

        if (total == 0) {
            tvMlOpsStatus.setText("No traffic yet");
            tvMlOpsStatus.setBackgroundResource(R.drawable.badge_normal);
        } else if (failed == 0) {
            tvMlOpsStatus.setText("Healthy");
            tvMlOpsStatus.setBackgroundResource(R.drawable.badge_normal);
        } else if (recovered > 0) {
            tvMlOpsStatus.setText("Recovered with retries");
            tvMlOpsStatus.setBackgroundResource(R.drawable.badge_medium);
        } else {
            tvMlOpsStatus.setText("Degraded");
            tvMlOpsStatus.setBackgroundResource(R.drawable.badge_high);
        }

        if (!logs.isEmpty()) {
            tvMlOpsLastEvent.setText("Last event: " + buildLogLine(logs.get(0)));
        } else {
            tvMlOpsLastEvent.setText("Last event: -");
        }
        tvMlOpsLastUpdated.setText("Updated at " + DISPLAY_TIME_FORMAT.format(new Date()));

        layoutMlOpsLogs.removeAllViews();
        tvMlOpsEmpty.setVisibility(logs.isEmpty() ? View.VISIBLE : View.GONE);
        for (MlOpsLogsResponse.LogEntry log : logs) {
            TextView eventText = new TextView(this);
            eventText.setText(buildLogLine(log));
            eventText.setTextSize(12f);
            eventText.setPadding(0, 0, 0, 8);
            eventText.setTextColor(getResources().getColor(
                    "failure".equals(log.getResult()) ? R.color.priority_high : R.color.text_secondary
            ));
            layoutMlOpsLogs.addView(eventText);
        }
    }

    private String buildLogLine(MlOpsLogsResponse.LogEntry log) {
        StringBuilder line = new StringBuilder();
        line.append(formatTimestamp(log.getTimestamp()));
        line.append(" • ");
        line.append(capitalize(String.valueOf(log.getOperation()).replace('_', ' ')));

        if (log.getStatus() != null) {
            line.append(" • status ").append(log.getStatus());
        }

        line.append(" • attempt ").append(log.getAttempt()).append("/").append(log.getMaxAttempts());

        if (log.isWillRetry()) {
            line.append(" • retry");
        }
        if (log.getLatencyMs() != null) {
            line.append(" • ").append(log.getLatencyMs()).append("ms");
        }
        if ("failure".equals(log.getResult()) && !TextUtils.isEmpty(log.getErrorMessage())) {
            line.append(" • ").append(log.getErrorMessage());
        }

        return line.toString();
    }

    private String formatTimestamp(String timestamp) {
        if (TextUtils.isEmpty(timestamp)) return "--:--:--";
        try {
            Date parsed = API_TIMESTAMP_FORMAT.parse(timestamp);
            if (parsed == null) return "--:--:--";
            return DISPLAY_TIME_FORMAT.format(parsed);
        } catch (ParseException e) {
            return "--:--:--";
        }
    }

    /**
     * Show an emergency patient intake dialog.
     * Staff provides the patient ID (from existing records) and an optional
     * brief description of reported symptoms. The backend creates a KTAS-1
     * token and routes it to the immediate_review lane.
     */
    private void showEmergencyDialog() {
        android.widget.EditText etPatientId = new android.widget.EditText(this);
        etPatientId.setHint("Patient ID (required)");
        etPatientId.setSingleLine(true);

        android.widget.EditText etSymptoms = new android.widget.EditText(this);
        etSymptoms.setHint("Reported symptoms (optional)");
        etSymptoms.setSingleLine(true);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);
        layout.addView(etPatientId);
        layout.addView(etSymptoms);

        new AlertDialog.Builder(this)
                .setTitle("⚠ Emergency Patient Intake")
                .setMessage("Creates an immediate-review token (KTAS 1) bypassing self-intake. Register the patient first if they are new.")
                .setView(layout)
                .setPositiveButton("Create Emergency Token", (dialog, which) -> {
                    String patientId = etPatientId.getText().toString().trim();
                    String symptoms = etSymptoms.getText().toString().trim();
                    if (patientId.isEmpty()) {
                        Toast.makeText(this, "Patient ID is required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitEmergencyToken(patientId, symptoms);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitEmergencyToken(String patientId, String symptoms) {
        com.example.smartqueue.models.request.EmergencyRequest req =
                new com.example.smartqueue.models.request.EmergencyRequest(
                        patientId, doctorId, symptoms);

        apiService.createEmergencyToken(req).enqueue(
                new Callback<com.example.smartqueue.models.response.TokenResponse>() {
                    @Override
                    public void onResponse(
                            Call<com.example.smartqueue.models.response.TokenResponse> call,
                            Response<com.example.smartqueue.models.response.TokenResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {
                            com.example.smartqueue.models.response.TokenResponse body = response.body();
                            Toast.makeText(AdminDashboardActivity.this,
                                    body.getMessage() != null
                                            ? body.getMessage()
                                            : "Emergency Token #" + body.getTokenNumber() + " created",
                                    Toast.LENGTH_LONG).show();
                            loadQueue();
                        } else {
                            Toast.makeText(AdminDashboardActivity.this,
                                    "Failed to create emergency token. Check patient ID.",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(
                            Call<com.example.smartqueue.models.response.TokenResponse> call,
                            Throwable t) {
                        Toast.makeText(AdminDashboardActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

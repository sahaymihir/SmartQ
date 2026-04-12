package com.example.smartqueue.ui.doctor;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.ui.prescription.PrescriptionActivity;
import com.example.smartqueue.utils.ApiErrorParser;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DoctorHomeActivity extends AppCompatActivity {

    private TextView tvDoctorName, tvCurrentPatientName, tvCurrentPatientToken;
    private TextView tvQueueSize, tvQueueSummary, tvQueueEmpty;
    private SwitchMaterial switchAvailability;
    private MaterialButton btnCallNext, btnPrescribe, btnLogout;
    private RecyclerView rvQueue;
    private ProgressBar progressBar;

    private QueueAdapter adapter;
    private SessionManager sessionManager;
    private ApiService apiService;

    private String currentTokenId = null;
    private boolean suppressAvailabilityCallback = false;
    private final Handler queueRefreshHandler = new Handler(Looper.getMainLooper());
    private Runnable queueRefreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_home);

        sessionManager = new SessionManager(this);
        String role = RoleNavigationHelper.normalizeRole(sessionManager.getRole());
        if (!"doctor".equals(role)) {
            startActivity(RoleNavigationHelper.createClearedHomeIntent(this, sessionManager.getRole()));
            finish();
            return;
        }
        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);

        initViews();
        setupRecyclerView();
        setupListeners();
        animateEntrance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchQueueData();
        startQueueRefresh();
    }

    @Override
    protected void onPause() {
        stopQueueRefresh();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopQueueRefresh();
        super.onDestroy();
    }

    private void initViews() {
        tvDoctorName = findViewById(R.id.tvDoctorName);
        tvCurrentPatientName = findViewById(R.id.tvCurrentPatientName);
        tvCurrentPatientToken = findViewById(R.id.tvCurrentPatientToken);
        tvQueueSize = findViewById(R.id.tvQueueSize);
        tvQueueSummary = findViewById(R.id.tvQueueSummary);
        tvQueueEmpty = findViewById(R.id.tvQueueEmpty);
        switchAvailability = findViewById(R.id.switchAvailability);
        btnCallNext = findViewById(R.id.btnCallNext);
        btnPrescribe = findViewById(R.id.btnPrescribe);
        rvQueue = findViewById(R.id.rvQueue);
        progressBar = findViewById(R.id.progressBar);
        btnLogout = findViewById(R.id.btnLogout);

        tvDoctorName.setText(sessionManager.getName());
    }

    private void setupRecyclerView() {
        adapter = new QueueAdapter();
        adapter.setQueueActionListener(new QueueAdapter.QueueActionListener() {
            @Override
            public void onPrescription(QueueResponse.QueueEntry entry) {
                openPrescriptionEditor(entry != null ? entry.getTokenId() : null);
            }

            @Override
            public void onNoShow(QueueResponse.QueueEntry entry) {
                confirmNoShow(entry);
            }
        });
        rvQueue.setLayoutManager(new LinearLayoutManager(this));
        rvQueue.setAdapter(adapter);
    }

    private void animateEntrance() {
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_enter);

        Handler handler = new Handler(Looper.getMainLooper());

        // Animate header
        handler.postDelayed(() -> {
            if (tvDoctorName.getParent() != null) {
                ((View) tvDoctorName.getParent().getParent()).startAnimation(slideUp);
            }
        }, 0);
    }

    private void setupListeners() {
        btnCallNext.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                        callNextPatient();
                    }).start();
        });

        btnPrescribe.setOnClickListener(v -> openPrescriptionEditor(currentTokenId));

        switchAvailability.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressAvailabilityCallback) {
                return;
            }
            toggleAvailability(!isChecked); // paused = !available
        });

        btnLogout.setOnClickListener(v -> {
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
            startActivity(new Intent(this, LoginActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        });
    }

    private void startQueueRefresh() {
        stopQueueRefresh();
        queueRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchQueueData(false);
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

    private void fetchQueueData() {
        fetchQueueData(true);
    }

    private void fetchQueueData(boolean showLoading) {
        if (showLoading) {
            progressBar.setVisibility(View.VISIBLE);
        }
        String doctorId = sessionManager.getUserId();
        if (TextUtils.isEmpty(doctorId)) {
            handleUnauthorized();
            return;
        }

        apiService.getAdminQueue(doctorId).enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    bindQueue(response.body());
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    showQueueError(error != null && !TextUtils.isEmpty(error.getMessage())
                            ? error.getMessage()
                            : "Could not load queue", showLoading);
                }
            }

            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                showQueueError("Failed to load queue", showLoading);
            }
        });
    }

    private void bindQueue(QueueResponse body) {
        List<QueueResponse.QueueEntry> queue = body.getQueue();
        if (queue == null) {
            queue = new ArrayList<>();
        }

        adapter.setQueueList(queue);
        rvQueue.setVisibility(queue.isEmpty() ? View.GONE : View.VISIBLE);
        tvQueueEmpty.setVisibility(queue.isEmpty() ? View.VISIBLE : View.GONE);
        tvQueueEmpty.setText("No patients are waiting right now.");

        int waitingCount = 0;
        int calledCount = 0;
        int immediateReviewCount = 0;
        QueueResponse.QueueEntry calledPatient = null;
        for (QueueResponse.QueueEntry entry : queue) {
            String status = entry.getStatus();
            if ("called".equals(status) || "arrived".equals(status)) {
                calledCount++;
                if (calledPatient == null) {
                    calledPatient = entry;
                }
            }
            if ("waiting".equals(status)) {
                waitingCount++;
                if (isImmediateReview(entry)) {
                    immediateReviewCount++;
                }
            }
        }

        tvQueueSize.setText(String.valueOf(queue.size()));
        tvQueueSummary.setText(immediateReviewCount > 0
                ? waitingCount + " waiting, " + immediateReviewCount + " urgent"
                : waitingCount + " waiting");
        updateAvailabilitySwitch(!body.isPaused());
        switchAvailability.setText(body.isPaused() ? "Unavailable" : "Available");

        if (calledPatient != null) {
            tvCurrentPatientName.setText(textOrDefault(calledPatient.getPatientName(), "Patient"));
            tvCurrentPatientToken.setText("Token #" + calledPatient.getTokenNumber()
                    + " | " + formatStatus(calledPatient.getStatus()));
            currentTokenId = calledPatient.getTokenId();
            btnPrescribe.setVisibility(View.VISIBLE);
        } else {
            resetCurrentPatientUI();
        }

        btnCallNext.setEnabled(!body.isPaused());
        if (body.isPaused()) {
            btnCallNext.setText("Queue Paused");
        } else if (calledCount > 0) {
            btnCallNext.setText("Complete Current and Call Next");
        } else {
            btnCallNext.setText("Call Next Patient");
        }
    }

    private void showQueueError(String message, boolean showToast) {
        adapter.setQueueList(new ArrayList<>());
        rvQueue.setVisibility(View.GONE);
        tvQueueEmpty.setVisibility(View.VISIBLE);
        tvQueueEmpty.setText(message);
        tvQueueSize.setText("--");
        tvQueueSummary.setText("Unavailable");
        resetCurrentPatientUI();
        if (showToast) {
            Toast.makeText(DoctorHomeActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetCurrentPatientUI() {
        tvCurrentPatientName.setText("No patient called");
        tvCurrentPatientToken.setText("Token #--");
        currentTokenId = null;
        btnPrescribe.setVisibility(View.GONE);
    }

    private void callNextPatient() {
        String doctorId = sessionManager.getUserId();
        if (TextUtils.isEmpty(doctorId)) {
            handleUnauthorized();
            return;
        }
        btnCallNext.setEnabled(false);
        btnCallNext.setText("Calling...");
        apiService.callNextPatient(doctorId).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                btnCallNext.setEnabled(true);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(DoctorHomeActivity.this,
                            response.body().getMessage() != null
                                    ? response.body().getMessage()
                                    : "Next patient called",
                            Toast.LENGTH_SHORT).show();
                    fetchQueueData();
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    if (error != null && error.isRequiresPrescription() && error.getTokenId() != null) {
                        Toast.makeText(DoctorHomeActivity.this,
                                error.getMessage(), Toast.LENGTH_SHORT).show();
                        openPrescriptionEditor(error.getTokenId());
                        fetchQueueData(false);
                    } else {
                        Toast.makeText(DoctorHomeActivity.this,
                                error != null && error.getMessage() != null
                                        ? error.getMessage()
                                        : "Queue is empty",
                                Toast.LENGTH_SHORT).show();
                        fetchQueueData(false);
                    }
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                btnCallNext.setEnabled(true);
                fetchQueueData(false);
                Toast.makeText(DoctorHomeActivity.this, "Error calling next", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleAvailability(boolean paused) {
        String doctorId = sessionManager.getUserId();
        if (TextUtils.isEmpty(doctorId)) {
            handleUnauthorized();
            return;
        }
        switchAvailability.setEnabled(false);
        apiService.togglePause(doctorId, paused).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                switchAvailability.setEnabled(true);
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    String status = paused ? "Unavailable" : "Available";
                    updateAvailabilitySwitch(!paused);
                    switchAvailability.setText(status);
                    Toast.makeText(DoctorHomeActivity.this, "Status: " + status, Toast.LENGTH_SHORT).show();
                    fetchQueueData(false);
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(DoctorHomeActivity.this,
                            error != null && !TextUtils.isEmpty(error.getMessage())
                                    ? error.getMessage()
                                    : "Failed to update status",
                            Toast.LENGTH_SHORT).show();
                    fetchQueueData(false);
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                switchAvailability.setEnabled(true);
                fetchQueueData(false);
                Toast.makeText(DoctorHomeActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmNoShow(QueueResponse.QueueEntry entry) {
        if (entry == null || TextUtils.isEmpty(entry.getTokenId())) {
            Toast.makeText(this, "No patient selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String name = textOrDefault(entry.getPatientName(), "This patient");
        new AlertDialog.Builder(this)
                .setTitle("Mark No-Show?")
                .setMessage(name + " will be removed from the active queue.")
                .setPositiveButton("Confirm", (dialog, which) -> markNoShow(entry.getTokenId(), name))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markNoShow(String tokenId, String patientName) {
        apiService.markNoShow(tokenId).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(DoctorHomeActivity.this,
                            response.body().getMessage() != null
                                    ? response.body().getMessage()
                                    : patientName + " marked as no-show",
                            Toast.LENGTH_SHORT).show();
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(DoctorHomeActivity.this,
                            error != null && !TextUtils.isEmpty(error.getMessage())
                                    ? error.getMessage()
                                    : "Could not mark no-show",
                            Toast.LENGTH_SHORT).show();
                }
                fetchQueueData();
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(DoctorHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openPrescriptionEditor(String tokenId) {
        if (TextUtils.isEmpty(tokenId)) {
            Toast.makeText(this, "No active patient selected", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PrescriptionActivity.class);
        intent.putExtra(PrescriptionActivity.EXTRA_TOKEN_ID, tokenId);
        intent.putExtra(PrescriptionActivity.EXTRA_READ_ONLY, false);
        startActivity(intent);
    }

    private void updateAvailabilitySwitch(boolean available) {
        suppressAvailabilityCallback = true;
        switchAvailability.setChecked(available);
        suppressAvailabilityCallback = false;
    }

    private boolean isImmediateReview(QueueResponse.QueueEntry entry) {
        return entry != null && (entry.isImmediateReviewRequired()
                || "immediate_review".equals(entry.getRoutingLane()));
    }

    private String formatStatus(String status) {
        if (TextUtils.isEmpty(status)) return "Waiting";
        return status.substring(0, 1).toUpperCase() + status.substring(1);
    }

    private String textOrDefault(String value, String fallback) {
        return TextUtils.isEmpty(value) ? fallback : value.trim();
    }

    private void handleUnauthorized() {
        if (!isFinishing()) {
            stopQueueRefresh();
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}

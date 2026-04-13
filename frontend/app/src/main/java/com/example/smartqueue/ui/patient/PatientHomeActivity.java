package com.example.smartqueue.ui.patient;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.ConsultationHistoryResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.TestRecommendationResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.prescription.PrescriptionActivity;
import com.example.smartqueue.utils.ApiErrorParser;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionFlowHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PatientHomeActivity extends AppCompatActivity {

    private static final long QUEUE_POLL_INTERVAL_MS = 5_000L;
    private static final long QUEUE_POLL_NEAR_FRONT_MS = 2_000L;
    private static final long QUEUE_POLL_IMMEDIATE_MS = 1_000L;
    private static final long HISTORY_POLL_INTERVAL_MS = 30_000L;
    private static final String QUEUE_ALERT_CHANNEL_ID = "smartq_queue_alerts";
    private static final int NEXT_IN_LINE_NOTIFICATION_ID = 1201;
    private static final int CALLED_NOTIFICATION_ID = 1202;

    private TextView tvGreeting;
    private TextView tvPatientName;
    private LinearLayout layoutDashboardLanding;
    private LinearLayout layoutQueueState;
    private CardView cardLastVisit;
    private TextView tvLastVisitDoctor;
    private TextView tvLastVisitDate;
    private TextView tvLastVisitSummary;
    private TextView tvLastVisitOutcome;
    private MaterialButton btnLastVisitPrescription;
    private MaterialButton btnStartQueueIntake;
    private TextView tvDoctorName;
    private TextView tvTokenNumber;
    private TextView tvPosition;
    private TextView tvPositionLabel;
    private TextView tvETA;
    private TextView tvQueueStatus;
    private TextView tvQueueStageHint;
    private TextView tvQueueEscalation;
    private TextView tvArrivalChip;
    private TextView tvArrivalMeta;
    private MaterialButton btnCheckIn;
    private TextView tvSnoozeCount;
    private MaterialButton btnSnooze;
    private CardView cardTestRecs;
    private LinearLayout layoutTestRecsList;
    private MaterialButton btnDismissTestRecs;
    private MaterialButton btnLeaveQueue;
    private MaterialCardView cardCalled;
    private MaterialButton btnDone;
    private TextView tvHistoryEmpty;
    private LinearLayout layoutHistoryList;

    private SessionManager sessionManager;
    private ApiService apiService;

    private final List<ConsultationHistoryResponse.Consultation> consultationHistory = new ArrayList<>();
    private ConsultationHistoryResponse.Consultation latestConsultation;
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private Handler historyPollHandler = new Handler(Looper.getMainLooper());
    private Runnable historyPollRunnable;
    private boolean isInQueue = false;
    private boolean hasCheckedInToday = false;
    private int activeTokenNumber = -1;
    private boolean nextInLineNotificationSent = false;
    private boolean calledNotificationSent = false;
    private int lastKnownQueuePosition = Integer.MAX_VALUE;
    private String lastKnownQueueStatus = "waiting";
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_home);

        sessionManager = new SessionManager(this);
        String role = RoleNavigationHelper.normalizeRole(sessionManager.getRole());
        if (!"patient".equals(role)) {
            startActivity(RoleNavigationHelper.createClearedHomeIntent(this, sessionManager.getRole()));
            finish();
            return;
        }

        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (!isGranted) {
                        Toast.makeText(
                                this,
                                getString(R.string.patient_notifications_rationale),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        ensureQueueAlertChannel();
        setupGreeting();
        setupClickListeners();
        maybeRequestNotificationPermission();
        loadConsultationHistory();
        checkExistingToken();
    }

    private void bindViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvPatientName = findViewById(R.id.tvPatientName);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> confirmLogout());

        layoutDashboardLanding = findViewById(R.id.layoutDashboardLanding);
        layoutQueueState = findViewById(R.id.layoutQueueState);
        cardLastVisit = findViewById(R.id.cardLastVisit);
        tvLastVisitDoctor = findViewById(R.id.tvLastVisitDoctor);
        tvLastVisitDate = findViewById(R.id.tvLastVisitDate);
        tvLastVisitSummary = findViewById(R.id.tvLastVisitSummary);
        tvLastVisitOutcome = findViewById(R.id.tvLastVisitOutcome);
        btnLastVisitPrescription = findViewById(R.id.btnLastVisitPrescription);
        btnStartQueueIntake = findViewById(R.id.btnStartQueueIntake);

        tvDoctorName = findViewById(R.id.tvDoctorName);
        tvTokenNumber = findViewById(R.id.tvTokenNumber);
        tvPosition = findViewById(R.id.tvPosition);
        tvPositionLabel = findViewById(R.id.tvPositionLabel);
        tvETA = findViewById(R.id.tvETA);
        tvQueueStatus = findViewById(R.id.tvQueueStatus);
        tvQueueStageHint = findViewById(R.id.tvQueueStageHint);
        tvQueueEscalation = findViewById(R.id.tvQueueEscalation);
        tvArrivalChip = findViewById(R.id.tvArrivalChip);
        tvArrivalMeta = findViewById(R.id.tvArrivalMeta);
        btnCheckIn = findViewById(R.id.btnCheckIn);
        tvSnoozeCount = findViewById(R.id.tvSnoozeCount);
        btnSnooze = findViewById(R.id.btnSnooze);
        cardTestRecs = findViewById(R.id.cardTestRecs);
        layoutTestRecsList = findViewById(R.id.layoutTestRecsList);
        btnDismissTestRecs = findViewById(R.id.btnDismissTestRecs);
        btnLeaveQueue = findViewById(R.id.btnLeaveQueue);
        cardCalled = findViewById(R.id.cardCalled);
        btnDone = findViewById(R.id.btnDone);

        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty);
        layoutHistoryList = findViewById(R.id.layoutHistoryList);
    }

    private void setupGreeting() {
        tvPatientName.setText(sessionManager.getName());
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        tvGreeting.setText(hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,");
    }

    private void setupClickListeners() {
        tvGreeting.setOnClickListener(v -> showVisitHistoryDialog());
        tvPatientName.setOnClickListener(v -> showVisitHistoryDialog());
        btnStartQueueIntake.setOnClickListener(v -> openNewIntake());
        btnLastVisitPrescription.setOnClickListener(v -> {
            if (latestConsultation != null) {
                openPrescriptionScreen(latestConsultation.getTokenId(), true);
            }
        });
        btnCheckIn.setOnClickListener(v -> promptForHospitalArrival());
        btnDismissTestRecs.setOnClickListener(v -> cardTestRecs.setVisibility(View.GONE));
        btnLeaveQueue.setOnClickListener(v -> confirmLeaveQueue());
        btnDone.setOnClickListener(v -> fetchQueueStatus(true));
        btnSnooze.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Snooze Queue")
                        .setMessage("You'll be pushed back 2 spots. Max 2 snoozes.")
                        .setPositiveButton("Snooze", (dialog, which) -> performSnooze())
                        .setNegativeButton("Cancel", null)
                        .show()
        );
    }

    private void openNewIntake() {
        startActivity(PatientQueueFlowHelper.createNewVisitIntent(this));
    }

    private void openFollowUpIntake(ConsultationHistoryResponse.Consultation consultation) {
        if (consultation == null) {
            return;
        }
        if (isInQueue) {
            Toast.makeText(this, "You already have an active queue token.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(PatientQueueFlowHelper.createFollowUpIntent(this, consultation));
    }

    private void loadConsultationHistory() {
        apiService.getConsultationHistory().enqueue(new Callback<ConsultationHistoryResponse>() {
            @Override
            public void onResponse(Call<ConsultationHistoryResponse> call, Response<ConsultationHistoryResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()
                        && response.body().getHistory() != null) {
                    consultationHistory.clear();
                    consultationHistory.addAll(response.body().getHistory());
                    renderLastVisitSummary();
                    renderConsultationHistory();
                }
            }

            @Override
            public void onFailure(Call<ConsultationHistoryResponse> call, Throwable t) {
                tvHistoryEmpty.setText(getString(R.string.history_empty));
            }
        });
    }

    private void renderLastVisitSummary() {
        latestConsultation = consultationHistory.isEmpty() ? null : consultationHistory.get(0);
        if (latestConsultation == null) {
            tvLastVisitDoctor.setText("No recent visits yet");
            tvLastVisitDate.setText("Your prescriptions and completed visit summaries will show here.");
            tvLastVisitSummary.setText("Open the latest visit to review medication, diagnosis, and follow-up context.");
            tvLastVisitOutcome.setVisibility(View.GONE);
            btnLastVisitPrescription.setVisibility(View.GONE);
            cardLastVisit.setOnClickListener(null);
            return;
        }

        tvLastVisitDoctor.setText(PatientQueueFlowHelper.textOrDefault(latestConsultation.getDoctorName(), "Recent visit"));
        tvLastVisitDate.setText(
                "Visit day: "
                        + PatientQueueFlowHelper.formatHistoryDate(latestConsultation.getDate())
                        + " | "
                        + PatientQueueFlowHelper.formatVisitTypeLabel(
                                this,
                                latestConsultation.getVisitType(),
                                latestConsultation.getDoctorSpecialty()
                        )
        );
        tvLastVisitSummary.setText(PatientQueueFlowHelper.buildHistorySummary(latestConsultation));
        String outcome = PatientQueueFlowHelper.buildOutcomeSummary(latestConsultation);
        tvLastVisitOutcome.setText(outcome);
        tvLastVisitOutcome.setVisibility(View.VISIBLE);
        btnLastVisitPrescription.setVisibility(View.VISIBLE);
        cardLastVisit.setOnClickListener(v -> openPrescriptionScreen(latestConsultation.getTokenId(), true));
    }

    private void renderConsultationHistory() {
        layoutHistoryList.removeAllViews();

        int startIndex = latestConsultation != null ? 1 : 0;
        if (consultationHistory.size() <= startIndex) {
            tvHistoryEmpty.setVisibility(View.VISIBLE);
            tvHistoryEmpty.setText(
                    latestConsultation == null
                            ? getString(R.string.history_empty)
                            : "Your earlier visits will appear here."
            );
            return;
        }

        tvHistoryEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = startIndex; i < consultationHistory.size(); i++) {
            ConsultationHistoryResponse.Consultation consultation = consultationHistory.get(i);
            View card = inflater.inflate(R.layout.item_consultation_history, layoutHistoryList, false);
            TextView tvDate = card.findViewById(R.id.tvHistoryDate);
            TextView tvDoctor = card.findViewById(R.id.tvHistoryDoctor);
            TextView tvType = card.findViewById(R.id.tvHistoryType);
            TextView tvSummary = card.findViewById(R.id.tvHistorySummary);
            TextView tvOutcome = card.findViewById(R.id.tvHistoryOutcome);
            TextView tvAction = card.findViewById(R.id.tvHistoryAction);
            MaterialButton btnHistoryFollowUp = card.findViewById(R.id.btnHistoryFollowUp);

            tvDate.setText("Visit day: " + PatientQueueFlowHelper.formatHistoryDate(consultation.getDate()));
            tvDoctor.setText(PatientQueueFlowHelper.textOrDefault(consultation.getDoctorName(), "Previous visit"));
            tvType.setText(
                    PatientQueueFlowHelper.formatVisitTypeLabel(
                            this,
                            consultation.getVisitType(),
                            consultation.getDoctorSpecialty()
                    )
            );
            tvSummary.setText(PatientQueueFlowHelper.buildHistorySummary(consultation));
            tvOutcome.setText(PatientQueueFlowHelper.buildOutcomeSummary(consultation));
            tvAction.setText(consultation.hasPrescription() ? "Open prescription" : "Open visit details");
            btnHistoryFollowUp.setText("Continue Follow-Up");

            tvAction.setOnClickListener(v -> openPrescriptionScreen(consultation.getTokenId(), true));
            btnHistoryFollowUp.setOnClickListener(v -> openFollowUpIntake(consultation));
            card.setOnClickListener(v -> openPrescriptionScreen(consultation.getTokenId(), true));
            layoutHistoryList.addView(card);
        }
    }

    private void showVisitHistoryDialog() {
        if (consultationHistory.isEmpty()) {
            Toast.makeText(this, getString(R.string.history_empty), Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] options = new CharSequence[consultationHistory.size()];
        for (int i = 0; i < consultationHistory.size(); i++) {
            ConsultationHistoryResponse.Consultation consultation = consultationHistory.get(i);
            String doctorName = !PatientQueueFlowHelper.isBlank(consultation.getDoctorName())
                    ? consultation.getDoctorName()
                    : "Previous visit";
            String conclusion = PatientQueueFlowHelper.buildOutcomeSummary(consultation);
            options[i] = PatientQueueFlowHelper.formatHistoryDate(consultation.getDate()) + " | " + doctorName + "\n" + conclusion;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.history_title))
                .setItems(options, (dialog, which) ->
                        openPrescriptionScreen(consultationHistory.get(which).getTokenId(), true))
                .setNegativeButton("Close", null)
                .show();
    }

    private void openPrescriptionScreen(String tokenId, boolean readOnly) {
        if (PatientQueueFlowHelper.isBlank(tokenId)) {
            return;
        }
        Intent intent = new Intent(this, PrescriptionActivity.class);
        intent.putExtra(PrescriptionActivity.EXTRA_TOKEN_ID, tokenId);
        intent.putExtra(PrescriptionActivity.EXTRA_READ_ONLY, readOnly);
        startActivity(intent);
    }

    private void checkExistingToken() {
        apiService.getQueueStatus().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    isInQueue = true;
                    hasCheckedInToday = body.isCheckedIn();
                    maybeNotifyQueueEvents(body.getTokenNumber(), body.getStatus(), body.getPosition(), body.getDoctorName());
                    updateQueueUI(body);
                    if ("called".equals(body.getStatus())) {
                        stopPolling();
                        showCalledState();
                    } else {
                        startPolling();
                    }
                } else {
                    handleNoActiveToken(false);
                }
            }

            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                handleNoActiveToken(false);
            }
        });
    }

    private void fetchQueueStatus() {
        fetchQueueStatus(false);
    }

    private void fetchQueueStatus(boolean userInitiated) {
        if (userInitiated) {
            setCalledRefreshLoading(true);
        }

        apiService.getQueueStatus().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (userInitiated) {
                    setCalledRefreshLoading(false);
                }
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.code() == 404) {
                    handleNoActiveToken(userInitiated);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    isInQueue = true;
                    hasCheckedInToday = body.isCheckedIn();
                    maybeNotifyQueueEvents(body.getTokenNumber(), body.getStatus(), body.getPosition(), body.getDoctorName());
                    updateQueueUI(body);
                    if ("called".equals(body.getStatus())) {
                        stopPolling();
                        showCalledState();
                        if (userInitiated) {
                            Toast.makeText(PatientHomeActivity.this, "Your token is still active.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        showQueueState();
                        startPolling();
                    }
                } else if (userInitiated) {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(
                            PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not refresh queue status.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                if (userInitiated) {
                    setCalledRefreshLoading(false);
                    Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void updateQueueUI(QueueResponse body) {
        boolean immediateReview = isImmediateReview(body.getRoutingLane(), body.isImmediateReviewRequired());
        String status = body.getStatus();

        showQueueState();
        tvDoctorName.setText(PatientQueueFlowHelper.textOrDefault(body.getDoctorName(), "Assigned doctor"));
        tvTokenNumber.setText("#" + body.getTokenNumber());
        tvQueueStatus.setText(formatStatus(status, body.isNurseTriaged(), immediateReview));
        updateQueuePollingSignals(status, body.getPosition());

        if (immediateReview) {
            tvPosition.setText("ER");
            tvPositionLabel.setText("Review");
            tvETA.setText("Now");
            tvQueueStageHint.setText("A clinician should review you immediately.");
            tvQueueEscalation.setVisibility(View.VISIBLE);
            tvQueueEscalation.setText(
                    PatientQueueFlowHelper.isBlank(body.getEscalationReason())
                            ? "Immediate escalation required. Please stay available for urgent review."
                            : body.getEscalationReason()
            );
        } else {
            tvPosition.setText(String.valueOf(body.getPosition()));
            tvPositionLabel.setText(body.getPosition() <= 1 ? "Next" : "In line");
            tvETA.setText(body.getEtaMinutes() <= 0 ? "Next up" : "~" + body.getEtaMinutes() + " min");
            tvQueueStageHint.setText(buildQueueStageHint(status, body.isNurseTriaged()));
            tvQueueEscalation.setVisibility(View.GONE);
        }

        updateArrivalState(body.isCheckedIn());
        updateSnoozeState(status, body.getPosition(), body.getSnoozeCount(), immediateReview);
        updateSuggestedTestsCard(status, body.isNurseTriaged(), body.getTestRecommendations());
    }

    private String buildQueueStageHint(String status, boolean nurseTriaged) {
        if ("waiting".equals(status) && !nurseTriaged) {
            return "Awaiting nurse triage before doctor review.";
        }
        if ("waiting_doctor".equals(status)) {
            return "Nurse stage complete. Waiting in the doctor queue.";
        }
        if ("arrived".equals(status)) {
            return "Arrival confirmed. Stay nearby for consultation updates.";
        }
        return "Stay ready for your next queue update.";
    }

    private void updateArrivalState(boolean checkedIn) {
        if (checkedIn) {
            tvArrivalChip.setText("Arrival confirmed");
            tvArrivalChip.setBackgroundResource(R.drawable.bg_arrival_chip_active);
            tvArrivalChip.setTextColor(getColor(R.color.status_active));
            tvArrivalMeta.setText("Hospital staff can see that you have arrived.");
            btnCheckIn.setEnabled(false);
            btnCheckIn.setText("Checked In");
        } else {
            tvArrivalChip.setText("Not arrived");
            tvArrivalChip.setBackgroundResource(R.drawable.bg_arrival_chip_idle);
            tvArrivalChip.setTextColor(getColor(R.color.on_surface_variant));
            tvArrivalMeta.setText("Tap only after you have reached the hospital.");
            btnCheckIn.setEnabled(true);
            btnCheckIn.setText("I'm At Hospital");
        }
    }

    private void promptForHospitalArrival() {
        if (hasCheckedInToday) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Are you at the hospital now?")
                .setMessage("Confirm this only after you have reached the hospital premises.")
                .setPositiveButton("Yes, check me in", (dialog, which) -> performCheckIn())
                .setNegativeButton("Not yet", null)
                .show();
    }

    private void updateSnoozeState(String status, int position, int snoozeCount, boolean immediateReview) {
        boolean waitingStatus = "waiting".equals(status) || "waiting_doctor".equals(status);
        if (immediateReview) {
            btnSnooze.setEnabled(false);
            tvSnoozeCount.setText("Immediate review cases cannot snooze.");
            return;
        }
        if (!waitingStatus) {
            btnSnooze.setEnabled(false);
            tvSnoozeCount.setText("Snooze is only available while you are waiting.");
            return;
        }
        if (position <= 2) {
            btnSnooze.setEnabled(false);
            tvSnoozeCount.setText("You cannot snooze when you are next or second in line.");
            return;
        }
        if (snoozeCount >= 2) {
            btnSnooze.setEnabled(false);
            tvSnoozeCount.setText("Maximum snooze limit reached (2/2 used).");
            return;
        }
        btnSnooze.setEnabled(true);
        tvSnoozeCount.setText("Push back 2 spots (" + snoozeCount + "/2 used)");
    }

    private void updateSuggestedTestsCard(
            String queueStatus,
            boolean nurseTriaged,
            List<TestRecommendationResponse.Recommendation> recommendations
    ) {
        boolean waitingForNurse = ("waiting".equals(queueStatus) || "waiting_doctor".equals(queueStatus))
                && !nurseTriaged;

        if (recommendations != null && !recommendations.isEmpty()) {
            showSuggestedTests(recommendations);
            return;
        }
        if (waitingForNurse) {
            showWaitingForVitalsMessage();
            return;
        }
        cardTestRecs.setVisibility(View.GONE);
    }

    private void showWaitingForVitalsMessage() {
        layoutTestRecsList.removeAllViews();
        TextView tv = new TextView(this);
        tv.setBackgroundResource(R.drawable.bg_patient_info_panel);
        int cardPadding = dp(20);
        tv.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
        tv.setTextColor(getColor(R.color.text_primary));
        tv.setTextSize(13f);
        tv.setText("Vitals are still being captured in nurse triage. Test recommendations will appear here automatically once that stage is complete.");
        layoutTestRecsList.addView(tv);
        cardTestRecs.setVisibility(View.VISIBLE);
    }

    private void showSuggestedTests(List<TestRecommendationResponse.Recommendation> recommendations) {
        layoutTestRecsList.removeAllViews();

        for (TestRecommendationResponse.Recommendation recommendation : recommendations) {
            MaterialCardView card = new MaterialCardView(this);
            card.setCardBackgroundColor(getColor(R.color.surface_container_high));
            card.setStrokeColor(getColor(R.color.outline_variant));
            card.setStrokeWidth(1);
            card.setRadius(16f);

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            int contentPadding = dp(18);
            container.setPadding(contentPadding, contentPadding, contentPadding, contentPadding);

            TextView badge = new TextView(this);
            badge.setText(resolveUrgencyLabel(recommendation.getUrgency()));
            badge.setTextColor(getColor(R.color.primary));
            badge.setTextSize(11f);
            badge.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            badge.setPadding(dp(12), dp(6), dp(12), dp(6));
            badge.setBackgroundResource(resolveUrgencyBadge(recommendation.getUrgency()));
            container.addView(badge);

            TextView title = new TextView(this);
            title.setText(PatientQueueFlowHelper.textOrDefault(recommendation.getTest(), "Suggested test"));
            title.setTextColor(getColor(R.color.text_primary));
            title.setTextSize(16f);
            title.setPadding(0, dp(14), 0, 0);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            container.addView(title);

            TextView rationale = new TextView(this);
            rationale.setText(
                    PatientQueueFlowHelper.isBlank(recommendation.getRationale())
                            ? "Suggested while you wait so your doctor has more information ready."
                            : recommendation.getRationale()
            );
            rationale.setTextColor(getColor(R.color.on_surface_variant));
            rationale.setTextSize(13f);
            rationale.setPadding(0, dp(8), 0, 0);
            container.addView(rationale);

            TextView footer = new TextView(this);
            footer.setText("Suggested while you wait");
            footer.setTextColor(getColor(R.color.primary));
            footer.setTextSize(12f);
            footer.setPadding(0, dp(10), 0, 0);
            container.addView(footer);

            card.addView(container);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.bottomMargin = dp(12);
            layoutTestRecsList.addView(card, params);
        }

        cardTestRecs.setVisibility(View.VISIBLE);
    }

    private int resolveUrgencyBadge(String urgency) {
        if (urgency == null) {
            return R.drawable.badge_normal;
        }
        String normalized = urgency.trim().toLowerCase();
        if (normalized.contains("high") || normalized.contains("urgent")) {
            return R.drawable.badge_high;
        }
        if (normalized.contains("medium") || normalized.contains("soon")) {
            return R.drawable.badge_medium;
        }
        return R.drawable.badge_normal;
    }

    private String resolveUrgencyLabel(String urgency) {
        if (PatientQueueFlowHelper.isBlank(urgency)) {
            return "Routine";
        }
        String normalized = urgency.trim().toLowerCase();
        if (normalized.contains("high") || normalized.contains("urgent")) {
            return "Higher Priority";
        }
        if (normalized.contains("medium") || normalized.contains("soon")) {
            return "Soon";
        }
        return "Routine";
    }

    private void performCheckIn() {
        btnCheckIn.setEnabled(false);
        apiService.checkIn().enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    hasCheckedInToday = true;
                    updateArrivalState(true);
                    Toast.makeText(PatientHomeActivity.this, "Arrival confirmed.", Toast.LENGTH_SHORT).show();
                } else {
                    btnCheckIn.setEnabled(true);
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(
                            PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not check in. Try again.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                btnCheckIn.setEnabled(true);
                Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performSnooze() {
        btnSnooze.setEnabled(false);
        apiService.snoozeQueue(2).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(PatientHomeActivity.this, response.body().getMessage(), Toast.LENGTH_SHORT).show();
                    fetchQueueStatus();
                } else {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(
                            PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not snooze queue. Try again.",
                            Toast.LENGTH_SHORT
                    ).show();
                    fetchQueueStatus();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                btnSnooze.setEnabled(true);
                Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmLeaveQueue() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Queue?")
                .setMessage("This cancels your active token and updates the hospital queue.")
                .setPositiveButton("Leave", (dialog, which) -> leaveQueue())
                .setNegativeButton("Stay", null)
                .show();
    }

    private void leaveQueue() {
        setLeaveQueueLoading(true);
        apiService.leaveQueue().enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.code() == 404) {
                    handleNoActiveToken(false);
                    Toast.makeText(PatientHomeActivity.this, "No active token found.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(
                            PatientHomeActivity.this,
                            response.body().getMessage() != null
                                    ? response.body().getMessage()
                                    : "Token cancelled.",
                            Toast.LENGTH_SHORT
                    ).show();
                    handleNoActiveToken(false);
                } else {
                    setLeaveQueueLoading(false);
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(
                            PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not leave queue. Try again.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                setLeaveQueueLoading(false);
                Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleNoActiveToken(boolean showToast) {
        stopPolling();
        isInQueue = false;
        showDashboardState();
        setLeaveQueueLoading(false);
        setCalledRefreshLoading(false);
        cardTestRecs.setVisibility(View.GONE);
        resetQueueAlertFlags();
        updateQueuePollingSignals("waiting", Integer.MAX_VALUE);
        loadConsultationHistory();
        startHistoryPolling();
        if (showToast) {
            Toast.makeText(this, "No active queue token found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDashboardState() {
        layoutDashboardLanding.setVisibility(View.VISIBLE);
        layoutQueueState.setVisibility(View.GONE);
        cardCalled.setVisibility(View.GONE);
    }

    private void showQueueState() {
        layoutDashboardLanding.setVisibility(View.GONE);
        layoutQueueState.setVisibility(View.VISIBLE);
        cardCalled.setVisibility(View.GONE);
    }

    private void showCalledState() {
        layoutDashboardLanding.setVisibility(View.GONE);
        layoutQueueState.setVisibility(View.GONE);
        cardCalled.setVisibility(View.VISIBLE);
    }

    private boolean isImmediateReview(String routingLane, boolean required) {
        return required || "immediate_review".equals(routingLane);
    }

    private String formatStatus(String status, boolean nurseTriaged, boolean immediateReview) {
        if (immediateReview) {
            return "Immediate Review";
        }
        if ("waiting".equals(status) && !nurseTriaged) {
            return "Nurse Triage";
        }
        if ("waiting_doctor".equals(status)) {
            return "Doctor Queue";
        }
        if ("called".equals(status)) {
            return "Called";
        }
        if ("arrived".equals(status)) {
            return "Arrived";
        }
        return "Waiting";
    }

    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isInQueue && sessionManager.isLoggedIn()) {
                    fetchQueueStatus();
                    pollHandler.postDelayed(this, resolveQueuePollIntervalMs());
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, QUEUE_POLL_IMMEDIATE_MS);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    private void startHistoryPolling() {
        stopHistoryPolling();
        historyPollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isInQueue && sessionManager.isLoggedIn()) {
                    loadConsultationHistory();
                    historyPollHandler.postDelayed(this, HISTORY_POLL_INTERVAL_MS);
                }
            }
        };
        historyPollHandler.postDelayed(historyPollRunnable, HISTORY_POLL_INTERVAL_MS);
    }

    private void stopHistoryPolling() {
        if (historyPollRunnable != null) {
            historyPollHandler.removeCallbacks(historyPollRunnable);
        }
    }

    private void updateQueuePollingSignals(String status, int position) {
        lastKnownQueueStatus = status != null ? status : "waiting";
        lastKnownQueuePosition = Math.max(0, position);
    }

    private long resolveQueuePollIntervalMs() {
        if ("called".equals(lastKnownQueueStatus) || lastKnownQueuePosition <= 1) {
            return QUEUE_POLL_IMMEDIATE_MS;
        }
        if (lastKnownQueuePosition <= 3 || "waiting_doctor".equals(lastKnownQueueStatus)) {
            return QUEUE_POLL_NEAR_FRONT_MS;
        }
        return QUEUE_POLL_INTERVAL_MS;
    }

    private void maybeNotifyQueueEvents(int tokenNumber, String status, int position, String doctorName) {
        if (tokenNumber <= 0) {
            return;
        }

        if (activeTokenNumber != tokenNumber) {
            activeTokenNumber = tokenNumber;
            nextInLineNotificationSent = false;
            calledNotificationSent = false;
        }

        boolean waitingStatus = "waiting".equals(status) || "waiting_doctor".equals(status);
        if (!nextInLineNotificationSent && waitingStatus && position <= 1) {
            postQueueNotification(
                    NEXT_IN_LINE_NOTIFICATION_ID,
                    "You are next in line",
                    "Please stay ready. Your turn is coming up now.",
                    doctorName
            );
            nextInLineNotificationSent = true;
        }

        if (!calledNotificationSent && "called".equals(status)) {
            postQueueNotification(
                    CALLED_NOTIFICATION_ID,
                    "Doctor has called you",
                    "Proceed to the consultation area now.",
                    doctorName
            );
            calledNotificationSent = true;
            nextInLineNotificationSent = true;
        }
    }

    private void postQueueNotification(int notificationId, String title, String message, String doctorName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent launchIntent = new Intent(this, PatientHomeActivity.class);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String content = doctorName != null && !doctorName.trim().isEmpty()
                ? message + " Assigned doctor: " + doctorName
                : message;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, QUEUE_ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat.from(this).notify(notificationId, builder.build());
        } catch (SecurityException ignored) {
            // Runtime permission can be denied; ignore gracefully.
        }
    }

    private void ensureQueueAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        NotificationChannel existing = notificationManager.getNotificationChannel(QUEUE_ALERT_CHANNEL_ID);
        if (existing != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                QUEUE_ALERT_CHANNEL_ID,
                "Queue Alerts",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Alerts when you are next in line or called by doctor");
        notificationManager.createNotificationChannel(channel);
    }

    private void resetQueueAlertFlags() {
        activeTokenNumber = -1;
        nextInLineNotificationSent = false;
        calledNotificationSent = false;
    }

    private void maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void confirmLogout() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logout_confirmation, null, false);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        MaterialButton btnLogout = dialogView.findViewById(R.id.btnDialogLogout);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnLogout.setOnClickListener(v -> {
            dialog.dismiss();
            stopPolling();
            stopHistoryPolling();
            SessionFlowHelper.logoutToLogin(this, sessionManager, null);
        });

        dialog.show();
    }

    private void setLeaveQueueLoading(boolean loading) {
        btnLeaveQueue.setEnabled(!loading);
        btnLeaveQueue.setText(loading ? "Leaving..." : "Leave Queue");
    }

    private void setCalledRefreshLoading(boolean loading) {
        btnDone.setEnabled(!loading);
        btnDone.setText(loading ? "Refreshing..." : "Refresh Status");
    }

    private void handleUnauthorized() {
        if (!isFinishing() && sessionManager.isLoggedIn()) {
            stopPolling();
            stopHistoryPolling();
            SessionFlowHelper.logoutToLogin(this, sessionManager, "Session expired. Please log in again.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConsultationHistory();
        if (isInQueue) {
            stopHistoryPolling();
            fetchQueueStatus();
        } else {
            checkExistingToken();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHistoryPolling();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        stopHistoryPolling();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}

package com.example.smartqueue.ui.patient;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.request.SymptomRequest;
import com.example.smartqueue.models.response.ConsultationHistoryResponse;
import com.example.smartqueue.models.response.DoctorsResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.SymptomPredictResponse;
import com.example.smartqueue.models.response.TestRecommendationResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.ui.prescription.PrescriptionActivity;
import com.example.smartqueue.utils.ApiErrorParser;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PatientHomeActivity extends AppCompatActivity {

    private static final long QUEUE_POLL_INTERVAL_MS = 10_000L;
    private static final long HISTORY_POLL_INTERVAL_MS = 30_000L;
    private static final String QUEUE_ALERT_CHANNEL_ID = "smartq_queue_alerts";
    private static final int NEXT_IN_LINE_NOTIFICATION_ID = 1201;
    private static final int CALLED_NOTIFICATION_ID = 1202;

    // Views
    private TextView tvGreeting, tvPatientName;
    private LinearLayout layoutNotInQueue, layoutInQueue;
    private CardView cardCalled;
    private MaterialButton btnJoinQueue;
    private TextView tvTokenNumber, tvDoctorName, tvPosition, tvPositionLabel, tvETA, tvQueueStatus;
    private TextView tvCheckinTitle, tvCheckinSubtitle, tvSnoozeCount;
    private CardView cardCheckin, cardSnooze;
    private MaterialButton btnCheckIn, btnSnooze, btnLeaveQueue, btnDone;

    // New doctor-selection views
    private TextInputEditText etSymptoms;
    private AutoCompleteTextView dropdownDoctorPicker;
    private MaterialButton btnFindDoctor, btnVoiceInput;
    private LinearLayout layoutAiResult;
    private TextView tvAiPickTitle, tvAiPickMeta, tvAiPickReasoning, tvSelectedDoctor, tvDoctorListLoading;

    // History views
    private LinearLayout layoutHistoryList;
    private TextView tvHistoryEmpty;

    // Test recommendations views (shown when wait is high after joining)
    private CardView cardTestRecs;
    private LinearLayout layoutTestRecsList;
    private MaterialButton btnDismissTestRecs;

    private SessionManager sessionManager;
    private ApiService apiService;

    // Doctor selection state
    private List<DoctorsResponse.Doctor> allDoctors = new ArrayList<>();
    private final List<String> doctorPickerOptions = new ArrayList<>();
    private ArrayAdapter<String> doctorPickerAdapter;
    private String selectedDoctorId   = null;
    private String selectedDoctorName = null;
    private String autoRecommendedId  = null;
    private final List<ConsultationHistoryResponse.Consultation> consultationHistory = new ArrayList<>();

    // Polling handler
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private Handler historyPollHandler = new Handler(Looper.getMainLooper());
    private Runnable historyPollRunnable;
    private boolean isInQueue = false;
    private int activeTokenNumber = -1;
    private boolean nextInLineNotificationSent = false;
    private boolean calledNotificationSent = false;
    private ActivityResultLauncher<Intent> speechInputLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_home);

        sessionManager = new SessionManager(this);
        String role = RoleNavigationHelper.normalizeRole(sessionManager.getRole());
        if ("admin".equals(role) || "superuser".equals(role) || "doctor".equals(role) || "nurse".equals(role)) {
            startActivity(RoleNavigationHelper.createClearedHomeIntent(this, sessionManager.getRole()));
            finish();
            return;
        }
        speechInputLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (matches == null || matches.isEmpty()) {
                        return;
                    }
                    String transcript = matches.get(0);
                    String existing = etSymptoms.getText() != null ? etSymptoms.getText().toString().trim() : "";
                    if (existing.isEmpty()) {
                        etSymptoms.setText(transcript);
                    } else {
                        etSymptoms.setText(existing + ", " + transcript);
                    }
                    etSymptoms.setSelection(etSymptoms.getText() != null ? etSymptoms.getText().length() : 0);
                }
        );
        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        ensureQueueAlertChannel();
        setupGreeting();
        setupClickListeners();
        loadDoctors();
        loadConsultationHistory();
        checkExistingToken();
    }

    private void bindViews() {
        tvGreeting         = findViewById(R.id.tvGreeting);
        tvPatientName      = findViewById(R.id.tvPatientName);
        layoutNotInQueue   = findViewById(R.id.layoutNotInQueue);
        layoutInQueue      = findViewById(R.id.layoutInQueue);
        cardCalled         = findViewById(R.id.cardCalled);
        btnJoinQueue       = findViewById(R.id.btnJoinQueue);
        tvTokenNumber      = findViewById(R.id.tvTokenNumber);
        tvDoctorName       = findViewById(R.id.tvDoctorName);
        tvPosition         = findViewById(R.id.tvPosition);
        tvPositionLabel    = findViewById(R.id.tvPositionLabel);
        tvETA              = findViewById(R.id.tvETA);
        tvQueueStatus      = findViewById(R.id.tvQueueStatus);
        tvCheckinTitle     = findViewById(R.id.tvCheckinTitle);
        tvCheckinSubtitle  = findViewById(R.id.tvCheckinSubtitle);
        tvSnoozeCount      = findViewById(R.id.tvSnoozeCount);
        cardCheckin        = findViewById(R.id.cardCheckin);
        cardSnooze         = findViewById(R.id.cardSnooze);
        btnCheckIn         = findViewById(R.id.btnCheckIn);
        btnSnooze          = findViewById(R.id.btnSnooze);
        btnLeaveQueue      = findViewById(R.id.btnLeaveQueue);
        btnDone            = findViewById(R.id.btnDone);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> confirmLogout());

        // New doctor-selection views
        etSymptoms          = findViewById(R.id.etSymptoms);
        dropdownDoctorPicker = findViewById(R.id.dropdownDoctorPicker);
        btnFindDoctor       = findViewById(R.id.btnFindDoctor);
        btnVoiceInput       = findViewById(R.id.btnVoiceInput);
        layoutAiResult      = findViewById(R.id.layoutAiResult);
        tvAiPickTitle       = findViewById(R.id.tvAiPickTitle);
        tvAiPickMeta        = findViewById(R.id.tvAiPickMeta);
        tvAiPickReasoning   = findViewById(R.id.tvAiPickReasoning);
        tvSelectedDoctor    = findViewById(R.id.tvSelectedDoctor);
        tvDoctorListLoading = findViewById(R.id.tvDoctorListLoading);

        layoutHistoryList   = findViewById(R.id.layoutHistoryList);
        tvHistoryEmpty      = findViewById(R.id.tvHistoryEmpty);

        // Test recommendations views
        cardTestRecs        = findViewById(R.id.cardTestRecs);
        layoutTestRecsList  = findViewById(R.id.layoutTestRecsList);
        btnDismissTestRecs  = findViewById(R.id.btnDismissTestRecs);
    }

    private void setupGreeting() {
        tvPatientName.setText(sessionManager.getName());
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        tvGreeting.setText(hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,");
    }

    private void setupClickListeners() {

        // Dismiss test recommendations
        btnDismissTestRecs.setOnClickListener(v -> cardTestRecs.setVisibility(View.GONE));

        tvPatientName.setOnClickListener(v -> showVisitHistoryDialog());
        tvGreeting.setOnClickListener(v -> showVisitHistoryDialog());

        // Find doctor using prediction
        btnFindDoctor.setOnClickListener(v -> {
            String symptoms = etSymptoms.getText() != null
                    ? etSymptoms.getText().toString().trim() : "";
            if (symptoms.isEmpty()) {
                Toast.makeText(this, "Please describe your symptoms first", Toast.LENGTH_SHORT).show();
                return;
            }
            predictDoctor(symptoms);
        });

        btnVoiceInput.setOnClickListener(v -> startVoiceInput());

        dropdownDoctorPicker.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLabel = (String) parent.getItemAtPosition(position);
            DoctorsResponse.Doctor selectedDoctor = findDoctorByDisplayLabel(selectedLabel);
            if (selectedDoctor == null) {
                return;
            }
            selectedDoctorId = selectedDoctor.getId();
            selectedDoctorName = textOrDefault(selectedDoctor.getName(), "Doctor");
            tvSelectedDoctor.setText(selectedDoctorName);
        });

        // Join Queue
        btnJoinQueue.setOnClickListener(v -> {
            if (selectedDoctorId == null) {
                Toast.makeText(this, "Please select a doctor first", Toast.LENGTH_SHORT).show();
                return;
            }
            String symptoms = etSymptoms.getText() != null
                    ? etSymptoms.getText().toString().trim()
                    : "";
            joinQueueWithContext(selectedDoctorId, selectedDoctorName, symptoms, "new", null);
        });

        // Check In
        btnCheckIn.setOnClickListener(v -> {
            btnCheckIn.setEnabled(false);
            apiService.checkIn().enqueue(new Callback<MessageResponse>() {
                @Override
                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                    if (response.code() == 401) { handleUnauthorized(); return; }
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        updateCheckinUI(true);
                        Toast.makeText(PatientHomeActivity.this,
                                "Checked in! Hospital notified.", Toast.LENGTH_SHORT).show();
                    } else {
                        btnCheckIn.setEnabled(true);
                        fetchQueueStatus();
                        MessageResponse error = ApiErrorParser.parseMessage(response);
                        Toast.makeText(PatientHomeActivity.this,
                                error != null && error.getMessage() != null
                                        ? error.getMessage()
                                        : "Could not check in. Try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(Call<MessageResponse> call, Throwable t) {
                    btnCheckIn.setEnabled(true);
                    Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Snooze
        btnSnooze.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Snooze Queue")
                        .setMessage("You'll be pushed back 2 spots. Max 2 snoozes.")
                        .setPositiveButton("Snooze", (dialog, which) -> {
                            btnSnooze.setEnabled(false);
                            apiService.snoozeQueue(2).enqueue(new Callback<MessageResponse>() {
                                @Override
                                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                                    if (response.code() == 401) { handleUnauthorized(); return; }
                                    if (response.isSuccessful() && response.body() != null
                                            && response.body().isSuccess()) {
                                        Toast.makeText(PatientHomeActivity.this,
                                                response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                        btnSnooze.setEnabled(true);
                                        fetchQueueStatus();
                                    } else {
                                        MessageResponse error = ApiErrorParser.parseMessage(response);
                                        Toast.makeText(PatientHomeActivity.this,
                                                error != null && error.getMessage() != null
                                                        ? error.getMessage()
                                                        : "Could not snooze queue. Try again.",
                                                Toast.LENGTH_SHORT).show();
                                        btnSnooze.setEnabled(true);
                                        fetchQueueStatus();
                                    }
                                }
                                @Override
                                public void onFailure(Call<MessageResponse> call, Throwable t) {
                                    btnSnooze.setEnabled(true);
                                    Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                                }
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        // Leave Queue
        btnLeaveQueue.setOnClickListener(v -> confirmLeaveQueue());

        // Done
        btnDone.setOnClickListener(v -> fetchQueueStatus(true));
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
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 404) {
                    handleNoActiveToken(false);
                    Toast.makeText(PatientHomeActivity.this, "No active token found.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(PatientHomeActivity.this,
                            response.body().getMessage() != null
                                    ? response.body().getMessage()
                                    : "Token cancelled.",
                            Toast.LENGTH_SHORT).show();
                    handleNoActiveToken(false);
                } else {
                    setLeaveQueueLoading(false);
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not leave queue. Try again.",
                            Toast.LENGTH_SHORT).show();
                    fetchQueueStatus();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                setLeaveQueueLoading(false);
                Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Load doctors from API
    private void loadDoctors() {
        tvDoctorListLoading.setVisibility(View.VISIBLE);
        tvDoctorListLoading.setText("Loading doctors...");

        apiService.getDoctors().enqueue(new Callback<DoctorsResponse>() {
            @Override
            public void onResponse(Call<DoctorsResponse> call, Response<DoctorsResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()
                        && response.body().getDoctors() != null
                        && !response.body().getDoctors().isEmpty()) {
                    allDoctors = response.body().getDoctors();
                    updateDoctorPickerOptions();
                    tvDoctorListLoading.setVisibility(View.GONE);
                } else {
                    doctorPickerOptions.clear();
                    if (doctorPickerAdapter != null) {
                        doctorPickerAdapter.notifyDataSetChanged();
                    }
                    tvDoctorListLoading.setText("No doctors found. Ask admin to run Seed Data.");
                    tvDoctorListLoading.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onFailure(Call<DoctorsResponse> call, Throwable t) {
                tvDoctorListLoading.setText("Could not load doctors. Is backend running?");
                tvDoctorListLoading.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateDoctorPickerOptions() {
        doctorPickerOptions.clear();
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            doctorPickerOptions.add(buildDoctorDisplayLabel(doctor));
        }

        if (doctorPickerAdapter == null) {
            doctorPickerAdapter = new ArrayAdapter<>(
                    this,
                R.layout.list_item_doctor_dropdown,
                    doctorPickerOptions
            );
            dropdownDoctorPicker.setAdapter(doctorPickerAdapter);
        } else {
            doctorPickerAdapter.notifyDataSetChanged();
        }
    }

    private String buildDoctorDisplayLabel(DoctorsResponse.Doctor doctor) {
        String name = textOrDefault(doctor.getName(), "Doctor");
        String specialty = textOrDefault(doctor.getSpecialty(), "General OPD");
        return name + " - " + specialty;
    }

    private DoctorsResponse.Doctor findDoctorByDisplayLabel(String label) {
        if (isBlank(label)) {
            return null;
        }
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (buildDoctorDisplayLabel(doctor).equals(label)) {
                return doctor;
            }
        }
        return null;
    }

    private void syncDoctorPickerSelectionById(String doctorId) {
        if (isBlank(doctorId)) {
            return;
        }
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (doctorId.equals(doctor.getId())) {
                dropdownDoctorPicker.setText(buildDoctorDisplayLabel(doctor), false);
                return;
            }
        }
    }

    // Call symptom prediction API
    private void predictDoctor(String symptoms) {
        btnFindDoctor.setEnabled(false);
        btnFindDoctor.setText("Finding...");

        apiService.predictDoctor(new SymptomRequest(symptoms))
                .enqueue(new Callback<SymptomPredictResponse>() {
                    @Override
                    public void onResponse(Call<SymptomPredictResponse> call,
                                           Response<SymptomPredictResponse> response) {
                        btnFindDoctor.setEnabled(true);
                        btnFindDoctor.setText("Find Best Doctor");
                        if (response.code() == 401) { handleUnauthorized(); return; }

                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {
                            SymptomPredictResponse body = response.body();
                            SymptomPredictResponse.Doctor rec = body.getRecommendedDoctor();
                            String recName = rec != null ? textOrDefault(rec.getName(), "Recommended doctor") : "No match";
                            String recSpecialty = rec != null ? textOrDefault(rec.getSpecialty(), "General OPD") : "";
                            if (rec != null && rec.getId() != null) {
                                autoRecommendedId  = rec.getId();
                                selectedDoctorId   = rec.getId();
                                selectedDoctorName = recName;
                                tvSelectedDoctor.setText(recName);
                                syncDoctorPickerSelectionById(rec.getId());
                            }
                            tvAiPickTitle.setText(rec != null
                                    ? "Suggested doctor: " + recName + " (" + recSpecialty + ")"
                                    : "No confident doctor suggestion");

                            StringBuilder meta = new StringBuilder();
                            if (body.getPrimarySpecialist() != null && !body.getPrimarySpecialist().isEmpty()) {
                                meta.append("Clinical fit: ").append(body.getPrimarySpecialist());
                            }
                            if (body.getRoutedSpecialty() != null && !body.getRoutedSpecialty().isEmpty()) {
                                if (meta.length() > 0) meta.append(" • ");
                                meta.append("SmartQ route: ").append(body.getRoutedSpecialty());
                            }
                            if (body.isLowConfidence()) {
                                if (meta.length() > 0) meta.append(" • ");
                                meta.append("You can change this");
                            }
                            tvAiPickMeta.setText(meta.length() > 0 ? meta.toString() : "Doctor suggestion");

                            String reasoning = body.getReasoning() != null ? body.getReasoning() : "—";
                            if (body.isLowConfidence()) {
                                reasoning += " You can pick another doctor if this suggestion does not feel right.";
                            }
                            tvAiPickReasoning.setText(reasoning);
                            layoutAiResult.setVisibility(View.VISIBLE);
                        } else {
                            Toast.makeText(PatientHomeActivity.this,
                                    "Could not predict doctor. Try again.", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<SymptomPredictResponse> call, Throwable t) {
                        btnFindDoctor.setEnabled(true);
                        btnFindDoctor.setText("Find Best Doctor");
                        Toast.makeText(PatientHomeActivity.this,
                                "Server error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private JoinQueueRequest buildJoinQueueRequest(String symptoms, String visitType, String followUpTokenId) {
        return new JoinQueueRequest(symptoms, visitType, followUpTokenId, "en");
    }

    private void joinQueueWithContext(
            String doctorId,
            String doctorName,
            String symptoms,
            String visitType,
            String followUpTokenId
    ) {
        if (isBlank(doctorId)) {
            Toast.makeText(this, "Please select a doctor first", Toast.LENGTH_SHORT).show();
            return;
        }

        btnJoinQueue.setEnabled(false);
        btnJoinQueue.setText("Joining...");

        JoinQueueRequest joinRequest = buildJoinQueueRequest(symptoms, visitType, followUpTokenId);
        apiService.joinQueue(doctorId, joinRequest).enqueue(new Callback<TokenResponse>() {
            @Override
            public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    TokenResponse body = response.body();
                    resetQueueAlertFlags();
                    activeTokenNumber = body.getTokenNumber();
                    isInQueue = true;
                    showInQueueState();
                    updateQueueUI(body.getPosition(), body.getTokenNumber(),
                            body.getEtaMinutes(), "waiting", false, body.getSnoozeCount(),
                            isImmediateReview(body.getRoutingLane(), body.isImmediateReviewRequired()));
                    tvDoctorName.setText(textOrDefault(doctorName, selectedDoctorName));
                    maybeNotifyQueueEvents(body.getTokenNumber(), "waiting", body.getPosition(), textOrDefault(doctorName, selectedDoctorName));
                    startPolling();
                    updateSuggestedTestsCard("waiting", body.isNurseTriaged(), body.getTestRecommendations());
                    Toast.makeText(PatientHomeActivity.this,
                            body.getMessage() != null ? body.getMessage() : "Token #" + body.getTokenNumber() + " issued!",
                            Toast.LENGTH_SHORT).show();
                } else {
                    btnJoinQueue.setEnabled(true);
                    btnJoinQueue.setText("Join Queue");
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not join queue. Try again.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<TokenResponse> call, Throwable t) {
                btnJoinQueue.setEnabled(true);
                btnJoinQueue.setText("Join Queue");
                Toast.makeText(PatientHomeActivity.this,
                        "Server error. Is backend running?", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void requestFollowUpQueueJoin(ConsultationHistoryResponse.Consultation consultation) {
        if (consultation == null) {
            return;
        }
        if (isInQueue) {
            Toast.makeText(this, "You already have an active queue token.", Toast.LENGTH_SHORT).show();
            return;
        }

        DoctorsResponse.Doctor followUpDoctor = resolveFollowUpDoctor(consultation);
        String targetDoctorId = followUpDoctor != null ? followUpDoctor.getId() : selectedDoctorId;
        String targetDoctorName = followUpDoctor != null
                ? textOrDefault(followUpDoctor.getName(), "Doctor")
                : selectedDoctorName;

        if (isBlank(targetDoctorId)) {
            Toast.makeText(this, "No matching follow-up doctor found. Select a doctor first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (followUpDoctor != null) {
            selectedDoctorId = followUpDoctor.getId();
            selectedDoctorName = targetDoctorName;
            tvSelectedDoctor.setText(targetDoctorName);
            syncDoctorPickerSelectionById(followUpDoctor.getId());
        }

        String symptoms = etSymptoms.getText() != null ? etSymptoms.getText().toString().trim() : "";
        if (symptoms.isEmpty()) {
            showFollowUpSymptomsPrompt(consultation, targetDoctorId, targetDoctorName);
            return;
        }

        joinQueueWithContext(targetDoctorId, targetDoctorName, symptoms, "follow_up", consultation.getTokenId());
    }

    private void showFollowUpSymptomsPrompt(
            ConsultationHistoryResponse.Consultation consultation,
            String doctorId,
            String doctorName
    ) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_follow_up_symptoms, null, false);
        TextInputEditText input = dialogView.findViewById(R.id.etFollowUpSymptoms);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        MaterialButton btnJoin = dialogView.findViewById(R.id.btnDialogJoin);

        input.setText(!isBlank(consultation.getSymptomsSummary())
                ? consultation.getSymptomsSummary()
                : textOrDefault(consultation.getSymptoms(), ""));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnJoin.setOnClickListener(v -> {
            String promptedSymptoms = input.getText() != null ? input.getText().toString().trim() : "";
            if (promptedSymptoms.isEmpty()) {
                Toast.makeText(this, "Symptoms are required for follow-up.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            etSymptoms.setText(promptedSymptoms);
            joinQueueWithContext(doctorId, doctorName, promptedSymptoms, "follow_up", consultation.getTokenId());
        });

        dialog.show();
    }

    private DoctorsResponse.Doctor resolveFollowUpDoctor(ConsultationHistoryResponse.Consultation consultation) {
        if (consultation == null || allDoctors == null || allDoctors.isEmpty()) {
            return null;
        }

        DoctorsResponse.Doctor sameSpecialtyDoctor = null;
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (doctor.getId() != null && doctor.getId().equals(consultation.getDoctorId())) {
                return doctor;
            }
            if (sameSpecialtyDoctor == null
                    && !isBlank(consultation.getDoctorSpecialty())
                    && consultation.getDoctorSpecialty().equalsIgnoreCase(doctor.getSpecialty())) {
                sameSpecialtyDoctor = doctor;
            }
        }
        return sameSpecialtyDoctor;
    }

    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your symptoms");
        try {
            speechInputLauncher.launch(intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, "Speech recognition is not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkExistingToken() {
        apiService.getQueueStatus().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    isInQueue = true;
                    maybeNotifyQueueEvents(body.getTokenNumber(), body.getStatus(), body.getPosition(), body.getDoctorName());
                    showInQueueState();
                    updateQueueUI(body.getPosition(), body.getTokenNumber(),
                            body.getEtaMinutes(), body.getStatus(), body.isCheckedIn(), body.getSnoozeCount(),
                            isImmediateReview(body.getRoutingLane(), body.isImmediateReviewRequired()));
                    updateSuggestedTestsCard(body.getStatus(), body.isNurseTriaged(), body.getTestRecommendations());
                    if (body.getDoctorName() != null) tvDoctorName.setText(body.getDoctorName());
                    if ("called".equals(body.getStatus())) showCalledState();
                    else startPolling();
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

    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (isInQueue && sessionManager.isLoggedIn()) {
                    fetchQueueStatus();
                    pollHandler.postDelayed(this, QUEUE_POLL_INTERVAL_MS);
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, QUEUE_POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollRunnable != null) pollHandler.removeCallbacks(pollRunnable);
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
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.code() == 404) {
                    handleNoActiveToken(userInitiated);
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    isInQueue = true;
                    maybeNotifyQueueEvents(body.getTokenNumber(), body.getStatus(), body.getPosition(), body.getDoctorName());
                    if (body.getDoctorName() != null) tvDoctorName.setText(body.getDoctorName());
                    if ("called".equals(body.getStatus())) {
                        stopPolling();
                        showCalledState();
                        if (userInitiated) {
                            Toast.makeText(PatientHomeActivity.this,
                                    "Your token is still active.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        showInQueueState();
                        updateQueueUI(body.getPosition(), body.getTokenNumber(),
                                body.getEtaMinutes(), body.getStatus(), body.isCheckedIn(), body.getSnoozeCount(),
                                isImmediateReview(body.getRoutingLane(), body.isImmediateReviewRequired()));
                        updateSuggestedTestsCard(body.getStatus(), body.isNurseTriaged(), body.getTestRecommendations());
                        startPolling();
                    }
                } else if (userInitiated) {
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(PatientHomeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not refresh queue status.",
                            Toast.LENGTH_SHORT).show();
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

    private void handleNoActiveToken(boolean showToast) {
        stopPolling();
        isInQueue = false;
        showNotInQueueState();
        resetJoinButton();
        setLeaveQueueLoading(false);
        setCalledRefreshLoading(false);
        cardTestRecs.setVisibility(View.GONE);
        resetQueueAlertFlags();
        loadConsultationHistory();
        if (showToast) {
            Toast.makeText(this, "No active queue token found.", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetJoinButton() {
        btnJoinQueue.setEnabled(true);
        btnJoinQueue.setText("Join Queue");
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
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
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
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
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
            // Notification permission can be denied at runtime; ignore gracefully.
        }
    }

    private void updateQueueUI(int position, int token, int eta,
                                String status, boolean checkedIn, int snoozeCount, boolean immediateReview) {
        tvTokenNumber.setText("#" + token);
        if (immediateReview) {
            tvPosition.setText("ER");
            tvPositionLabel.setText("review");
            tvETA.setText("Immediate review");
            tvQueueStatus.setText("Immediate review");
            btnSnooze.setEnabled(false);
            cardSnooze.setAlpha(0.6f);
            tvSnoozeCount.setText("Emergency cases cannot use snooze");
        } else {
            tvPosition.setText(String.valueOf(position));
            tvPositionLabel.setText("in line");
            tvETA.setText(eta == 0 ? "Next up!" : "~" + eta + " min");
            tvQueueStatus.setText(formatStatus(status));
            boolean canSnooze = ("waiting".equals(status) || "waiting_doctor".equals(status)) && snoozeCount < 2;
            btnSnooze.setEnabled(canSnooze);
            cardSnooze.setAlpha(canSnooze ? 1f : 0.6f);
            tvSnoozeCount.setText(snoozeCount >= 2
                    ? "Maximum snooze limit reached (2/2 used)"
                    : "Push back 2 spots (" + snoozeCount + "/2 used)");
        }
        updateCheckinUI(checkedIn);
    }

    private boolean isImmediateReview(String routingLane, boolean required) {
        return required || "immediate_review".equals(routingLane);
    }

    private void updateCheckinUI(boolean isCheckedIn) {
        if (isCheckedIn) {
            tvCheckinTitle.setText("Checked in at hospital");
            tvCheckinSubtitle.setText("Hospital has been notified of your arrival");
            btnCheckIn.setEnabled(false);
            btnCheckIn.setText("Done");
        } else {
            tvCheckinTitle.setText("Not checked in yet");
            tvCheckinSubtitle.setText("Tap when you arrive at the hospital");
            btnCheckIn.setEnabled(true);
            btnCheckIn.setText("Check In");
        }
    }

    private void showNotInQueueState() {
        layoutNotInQueue.setVisibility(View.VISIBLE);
        layoutInQueue.setVisibility(View.GONE);
        cardCalled.setVisibility(View.GONE);
        startHistoryPolling();
    }

    private void showInQueueState() {
        layoutNotInQueue.setVisibility(View.GONE);
        layoutInQueue.setVisibility(View.VISIBLE);
        cardCalled.setVisibility(View.GONE);
        stopHistoryPolling();
    }

    private void showCalledState() {
        layoutNotInQueue.setVisibility(View.GONE);
        layoutInQueue.setVisibility(View.GONE);
        cardCalled.setVisibility(View.VISIBLE);
    }

    private String formatStatus(String status) {
        if (status == null) return "Waiting";
        switch (status) {
            case "waiting":   return "Waiting";
            case "waiting_doctor": return "Waiting for doctor";
            case "called":    return "Called!";
            case "arrived":
            case "checkedin": return "Arrived";
            default:          return "Waiting";
        }
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
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        });

        dialog.show();
    }

    private void loadConsultationHistory() {
        apiService.getConsultationHistory().enqueue(new Callback<ConsultationHistoryResponse>() {
            @Override
            public void onResponse(Call<ConsultationHistoryResponse> call, Response<ConsultationHistoryResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getHistory() != null) {
                    consultationHistory.clear();
                    consultationHistory.addAll(response.body().getHistory());
                    renderConsultationHistory();
                }
            }

            @Override
            public void onFailure(Call<ConsultationHistoryResponse> call, Throwable t) {
                tvHistoryEmpty.setText(getString(R.string.history_empty));
            }
        });
    }

    private void renderConsultationHistory() {
        layoutHistoryList.removeAllViews();
        if (consultationHistory.isEmpty()) {
            tvHistoryEmpty.setVisibility(View.VISIBLE);
            return;
        }

        tvHistoryEmpty.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ConsultationHistoryResponse.Consultation consultation : consultationHistory) {
            View card = inflater.inflate(R.layout.item_consultation_history, layoutHistoryList, false);
            TextView tvDate = card.findViewById(R.id.tvHistoryDate);
            TextView tvDoctor = card.findViewById(R.id.tvHistoryDoctor);
            TextView tvType = card.findViewById(R.id.tvHistoryType);
            TextView tvSummary = card.findViewById(R.id.tvHistorySummary);
            TextView tvOutcome = card.findViewById(R.id.tvHistoryOutcome);
            TextView tvAction = card.findViewById(R.id.tvHistoryAction);
            MaterialButton btnHistoryFollowUp = card.findViewById(R.id.btnHistoryFollowUp);

            tvDate.setText("Visit day: " + formatHistoryDate(consultation.getDate()));
            tvDoctor.setText(consultation.getDoctorName());
            tvType.setText(formatVisitTypeLabel(consultation.getVisitType(), consultation.getDoctorSpecialty()));
            tvSummary.setText(!isBlank(consultation.getSymptomsSummary())
                    ? consultation.getSymptomsSummary()
                    : (!isBlank(consultation.getSymptoms()) ? consultation.getSymptoms() : "No symptom summary recorded."));
            tvOutcome.setText(!isBlank(consultation.getConclusionPreview())
                    ? consultation.getConclusionPreview()
                    : (!isBlank(consultation.getDiagnosis()) ? consultation.getDiagnosis() : "Open for full prescription details."));
            tvAction.setText(consultation.hasPrescription() ? "Open prescription" : "Open visit details");

            tvAction.setOnClickListener(v -> openPrescriptionScreen(consultation.getTokenId(), true));
            btnHistoryFollowUp.setOnClickListener(v -> requestFollowUpQueueJoin(consultation));
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
            String doctorName = !isBlank(consultation.getDoctorName()) ? consultation.getDoctorName() : "Previous visit";
            String conclusion = !isBlank(consultation.getConclusionPreview())
                    ? consultation.getConclusionPreview()
                    : (!isBlank(consultation.getDiagnosis()) ? consultation.getDiagnosis() : "Visit details");
            options[i] = formatHistoryDate(consultation.getDate()) + " • " + doctorName + "\n" + conclusion;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.history_title))
                .setItems(options, (dialog, which) ->
                        openPrescriptionScreen(consultationHistory.get(which).getTokenId(), true))
                .setNegativeButton("Close", null)
                .show();
    }


    private void openPrescriptionScreen(String tokenId, boolean readOnly) {
        if (isBlank(tokenId)) {
            return;
        }
        Intent intent = new Intent(this, PrescriptionActivity.class);
        intent.putExtra(PrescriptionActivity.EXTRA_TOKEN_ID, tokenId);
        intent.putExtra(PrescriptionActivity.EXTRA_READ_ONLY, readOnly);
        startActivity(intent);
    }

    private String formatHistoryDate(String rawDate) {
        if (isBlank(rawDate)) {
            return "Visit";
        }
        int separator = rawDate.indexOf('T');
        return separator > 0 ? rawDate.substring(0, separator) : rawDate;
    }

    private String formatVisitTypeLabel(String visitType, String specialty) {
        String label = "follow_up".equals(visitType)
                ? getString(R.string.visit_type_follow_up)
                : getString(R.string.visit_type_new);
        if (!isBlank(specialty)) {
            label += " • " + specialty;
        }
        return label;
    }

    private String textOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void updateSuggestedTestsCard(
            String queueStatus,
            boolean nurseTriaged,
            List<TestRecommendationResponse.Recommendation> recommendations) {
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
        tv.setTextSize(12f);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setPadding(0, 4, 0, 4);
        tv.setText("Waiting for vitals info from nurse triage. Suggested tests will appear automatically once vitals are recorded.");
        layoutTestRecsList.addView(tv);
        cardTestRecs.setVisibility(View.VISIBLE);
    }

    private void showSuggestedTests(
            List<TestRecommendationResponse.Recommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            cardTestRecs.setVisibility(View.GONE);
            return;
        }

        layoutTestRecsList.removeAllViews();
        for (TestRecommendationResponse.Recommendation rec : recommendations) {
            TextView tv = new TextView(this);
            tv.setTextSize(12f);
            tv.setTextColor(getResources().getColor(R.color.text_primary));
            tv.setPadding(0, 4, 0, 4);

            String urgency = rec.getUrgency() != null ? " [" + rec.getUrgency() + "]" : "";
            String rationale = rec.getRationale() != null ? "\n  " + rec.getRationale() : "";
            tv.setText("- " + rec.getTest() + urgency + rationale);
            layoutTestRecsList.addView(tv);
        }
        cardTestRecs.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        stopHistoryPolling();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isInQueue) {
            loadConsultationHistory();
            startHistoryPolling();
        } else {
            stopHistoryPolling();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHistoryPolling();
    }
}

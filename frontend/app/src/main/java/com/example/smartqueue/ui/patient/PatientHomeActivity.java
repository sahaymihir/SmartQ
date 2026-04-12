package com.example.smartqueue.ui.patient;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

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
    private TextInputEditText etSymptoms, etDoctorSearch;
    private MaterialButton btnFindDoctor, btnVoiceInput;
    private LinearLayout layoutAiResult, layoutDoctorList;
    private TextView tvAiPickTitle, tvAiPickMeta, tvAiPickReasoning, tvSelectedDoctor, tvDoctorListLoading;

    // Visit type views
    private RadioGroup rgVisitType;
    private LinearLayout layoutFollowUpNote;
    private TextView tvFollowUpNote;
    private MaterialButton btnChooseFollowUp;
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
    private String selectedDoctorId   = null;
    private String selectedDoctorName = null;
    private String autoRecommendedId  = null;
    private final List<ConsultationHistoryResponse.Consultation> consultationHistory = new ArrayList<>();

    // Visit intent state
    private String selectedVisitType  = "new";     // "new" or "follow_up"
    private String followUpTokenId    = null;       // linked prior token chosen by the patient
    private ConsultationHistoryResponse.Consultation selectedFollowUpConsultation = null;

    // Polling handler
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private boolean isInQueue = false;
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
        setupGreeting();
        setupClickListeners();
        loadDoctors();
        loadConsultationHistory(false);
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
        etDoctorSearch      = findViewById(R.id.etDoctorSearch);
        btnFindDoctor       = findViewById(R.id.btnFindDoctor);
        btnVoiceInput       = findViewById(R.id.btnVoiceInput);
        layoutAiResult      = findViewById(R.id.layoutAiResult);
        layoutDoctorList    = findViewById(R.id.layoutDoctorList);
        tvAiPickTitle       = findViewById(R.id.tvAiPickTitle);
        tvAiPickMeta        = findViewById(R.id.tvAiPickMeta);
        tvAiPickReasoning   = findViewById(R.id.tvAiPickReasoning);
        tvSelectedDoctor    = findViewById(R.id.tvSelectedDoctor);
        tvDoctorListLoading = findViewById(R.id.tvDoctorListLoading);

        // Visit type views
        rgVisitType         = findViewById(R.id.rgVisitType);
        layoutFollowUpNote  = findViewById(R.id.layoutFollowUpNote);
        tvFollowUpNote      = findViewById(R.id.tvFollowUpNote);
        btnChooseFollowUp   = findViewById(R.id.btnChooseFollowUp);
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

        // Visit type selection — show/hide follow-up note
        rgVisitType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbVisitFollowUp) {
                selectedVisitType = "follow_up";
                layoutFollowUpNote.setVisibility(View.VISIBLE);
                if (consultationHistory.isEmpty()) {
                    loadConsultationHistory(true);
                } else {
                    showFollowUpPicker();
                }
            } else {
                selectedVisitType = "new";
                followUpTokenId = null;
                selectedFollowUpConsultation = null;
                tvFollowUpNote.setText(getString(R.string.follow_up_note));
                layoutFollowUpNote.setVisibility(View.GONE);
            }
        });

        btnChooseFollowUp.setOnClickListener(v -> {
            if (consultationHistory.isEmpty()) {
                loadConsultationHistory(true);
            } else {
                showFollowUpPicker();
            }
        });

        // Dismiss test recommendations
        btnDismissTestRecs.setOnClickListener(v -> cardTestRecs.setVisibility(View.GONE));

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

        // Doctor list search filter
        etDoctorSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterAndRenderDoctors(s.toString().trim().toLowerCase());
            }
        });

        // Join Queue
        btnJoinQueue.setOnClickListener(v -> {
            if (selectedDoctorId == null) {
                Toast.makeText(this, "Please select a doctor first", Toast.LENGTH_SHORT).show();
                return;
            }
            if ("follow_up".equals(selectedVisitType) && followUpTokenId == null) {
                Toast.makeText(this, "Please choose a previous visit for follow-up", Toast.LENGTH_SHORT).show();
                return;
            }
            btnJoinQueue.setEnabled(false);
            btnJoinQueue.setText("Joining...");

            JoinQueueRequest joinRequest = buildJoinQueueRequest();
            apiService.joinQueue(selectedDoctorId, joinRequest).enqueue(new Callback<TokenResponse>() {
                @Override
                public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                    if (response.code() == 401) { handleUnauthorized(); return; }
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        TokenResponse body = response.body();
                        isInQueue = true;
                        showInQueueState();
                        updateQueueUI(body.getPosition(), body.getTokenNumber(),
                                body.getEtaMinutes(), "waiting", false, body.getSnoozeCount(),
                                isImmediateReview(body.getRoutingLane(), body.isImmediateReviewRequired()));
                        tvDoctorName.setText(selectedDoctorName);
                        startPolling();
                        // Show test recommendations if backend suggested them for a long wait
                        if (body.hasTestRecommendations()) {
                            showTestRecommendations(body.getTestRecommendations());
                        }
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
        layoutDoctorList.setVisibility(View.GONE);

        apiService.getDoctors().enqueue(new Callback<DoctorsResponse>() {
            @Override
            public void onResponse(Call<DoctorsResponse> call, Response<DoctorsResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()
                        && response.body().getDoctors() != null
                        && !response.body().getDoctors().isEmpty()) {
                    allDoctors = response.body().getDoctors();
                    tvDoctorListLoading.setVisibility(View.GONE);
                    layoutDoctorList.setVisibility(View.VISIBLE);
                    filterAndRenderDoctors("");
                    if (selectedFollowUpConsultation != null) {
                        applyDoctorSelectionForFollowUp(selectedFollowUpConsultation, false);
                    }
                } else {
                    tvDoctorListLoading.setText("No doctors found. Ask admin to run Seed Data.");
                }
            }
            @Override
            public void onFailure(Call<DoctorsResponse> call, Throwable t) {
                tvDoctorListLoading.setText("Could not load doctors. Is backend running?");
            }
        });
    }

    // Filter and render doctor list based on search query
    private void filterAndRenderDoctors(String query) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        List<DoctorsResponse.Doctor> filtered = new ArrayList<>();
        for (DoctorsResponse.Doctor doc : allDoctors) {
            String name = doc.getName() == null ? "" : doc.getName().toLowerCase();
            String specialty = doc.getSpecialty() == null ? "" : doc.getSpecialty().toLowerCase();
            if (normalizedQuery.isEmpty()
                    || name.contains(normalizedQuery)
                    || specialty.contains(normalizedQuery)) {
                filtered.add(doc);
            }
        }
        renderDoctorList(filtered);
    }

    // Inflate and render doctor list items into layoutDoctorList
    private void renderDoctorList(List<DoctorsResponse.Doctor> doctors) {
        layoutDoctorList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        if (doctors.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("No doctors match your search");
            tv.setTextSize(13f);
            tv.setPadding(0, 24, 0, 24);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            layoutDoctorList.addView(tv);
            return;
        }

        for (DoctorsResponse.Doctor doctor : doctors) {
            View item = inflater.inflate(R.layout.item_doctor, layoutDoctorList, false);

            CardView card        = item.findViewById(R.id.cardDoctorItem);
            TextView tvInitial   = item.findViewById(R.id.tvDoctorInitial);
            TextView tvName      = item.findViewById(R.id.tvDoctorItemName);
            TextView tvSpecialty = item.findViewById(R.id.tvDoctorItemSpecialty);
            TextView tvAiBadge   = item.findViewById(R.id.tvAiPick);
            TextView tvCheck     = item.findViewById(R.id.tvDoctorSelectedMark);

            String displayName = textOrDefault(doctor.getName(), "Doctor");
            String displaySpecialty = textOrDefault(doctor.getSpecialty(), "General OPD");

            tvName.setText(displayName);
            tvSpecialty.setText(displaySpecialty);
            tvInitial.setText(specialtyInitial(displaySpecialty));

            boolean isSelected = doctor.getId() != null && doctor.getId().equals(selectedDoctorId);
            boolean isAiPick   = doctor.getId() != null && doctor.getId().equals(autoRecommendedId);

            tvCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            tvAiBadge.setVisibility(isAiPick ? View.VISIBLE : View.GONE);
            card.setCardBackgroundColor(getResources().getColor(
                    isSelected ? R.color.primary_container : R.color.surface_container_low));

            final String docId   = doctor.getId();
            final String docName = displayName;

            card.setOnClickListener(v -> {
                selectedDoctorId   = docId;
                selectedDoctorName = docName;
                tvSelectedDoctor.setText(docName);
                // Keep predicted badge but user may override selection
                filterAndRenderDoctors(
                        etDoctorSearch.getText() != null
                                ? etDoctorSearch.getText().toString().trim().toLowerCase() : "");
            });

            layoutDoctorList.addView(item);
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
                            filterAndRenderDoctors(
                                    etDoctorSearch.getText() != null
                                            ? etDoctorSearch.getText().toString().trim().toLowerCase() : "");
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

    private JoinQueueRequest buildJoinQueueRequest() {
        String symptoms = etSymptoms.getText() != null
                ? etSymptoms.getText().toString().trim() : "";
        return new JoinQueueRequest(symptoms, selectedVisitType, followUpTokenId, "en");
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
                    showInQueueState();
                    updateQueueUI(body.getPosition(), body.getTokenNumber(),
                            body.getEtaMinutes(), body.getStatus(), body.isCheckedIn(), body.getSnoozeCount(),
                            isImmediateReview(body.getRoutingLane(), body.isImmediateReviewRequired()));
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
                    pollHandler.postDelayed(this, 10000);
                }
            }
        };
        pollHandler.postDelayed(pollRunnable, 10000);
    }

    private void stopPolling() {
        if (pollRunnable != null) pollHandler.removeCallbacks(pollRunnable);
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
        loadConsultationHistory(false);
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
    }

    private void showInQueueState() {
        layoutNotInQueue.setVisibility(View.GONE);
        layoutInQueue.setVisibility(View.VISIBLE);
        cardCalled.setVisibility(View.GONE);
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

    // Return first letter of specialty for the avatar circle
    private String specialtyInitial(String specialty) {
        if (specialty == null || specialty.isEmpty()) return "?";
        return String.valueOf(specialty.charAt(0)).toUpperCase();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    stopPolling();
                    sessionManager.clearSession();
                    ApiClient.setAuthToken(null);
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadConsultationHistory(boolean openPickerAfterLoad) {
        apiService.getConsultationHistory().enqueue(new Callback<ConsultationHistoryResponse>() {
            @Override
            public void onResponse(Call<ConsultationHistoryResponse> call, Response<ConsultationHistoryResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getHistory() != null) {
                    consultationHistory.clear();
                    consultationHistory.addAll(response.body().getHistory());
                    renderConsultationHistory();

                    if ("follow_up".equals(selectedVisitType)) {
                        if (consultationHistory.isEmpty()) {
                            tvFollowUpNote.setText(getString(R.string.follow_up_none));
                        } else if (openPickerAfterLoad || selectedFollowUpConsultation == null) {
                            showFollowUpPicker();
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<ConsultationHistoryResponse> call, Throwable t) {
                tvHistoryEmpty.setText(getString(R.string.history_empty));
                if ("follow_up".equals(selectedVisitType)) {
                    tvFollowUpNote.setText(getString(R.string.follow_up_none));
                }
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

            tvDate.setText(formatHistoryDate(consultation.getDate()));
            tvDoctor.setText(consultation.getDoctorName());
            tvType.setText(formatVisitTypeLabel(consultation.getVisitType(), consultation.getDoctorSpecialty()));
            tvSummary.setText(!isBlank(consultation.getSymptomsSummary())
                    ? consultation.getSymptomsSummary()
                    : (!isBlank(consultation.getSymptoms()) ? consultation.getSymptoms() : "No symptom summary recorded."));
            tvOutcome.setText(!isBlank(consultation.getConclusionPreview())
                    ? consultation.getConclusionPreview()
                    : (!isBlank(consultation.getDiagnosis()) ? consultation.getDiagnosis() : "Open for full prescription details."));

            card.setOnClickListener(v -> openPrescriptionScreen(consultation.getTokenId(), true));
            layoutHistoryList.addView(card);
        }
    }

    private void showFollowUpPicker() {
        if (consultationHistory.isEmpty()) {
            tvFollowUpNote.setText(getString(R.string.follow_up_none));
            Toast.makeText(this, getString(R.string.follow_up_none), Toast.LENGTH_SHORT).show();
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
                .setTitle(getString(R.string.follow_up_choose_visit))
                .setItems(options, (dialog, which) -> applyFollowUpSelection(consultationHistory.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyFollowUpSelection(ConsultationHistoryResponse.Consultation consultation) {
        selectedFollowUpConsultation = consultation;
        followUpTokenId = consultation.getTokenId();
        tvFollowUpNote.setText(getString(R.string.follow_up_note_linked)
                + consultation.getDoctorName()
                + " • " + formatHistoryDate(consultation.getDate()));
        applyDoctorSelectionForFollowUp(consultation, true);
    }

    private void applyDoctorSelectionForFollowUp(ConsultationHistoryResponse.Consultation consultation,
                                                 boolean showFeedback) {
        if (consultation == null || allDoctors == null || allDoctors.isEmpty()) {
            return;
        }

        DoctorsResponse.Doctor sameDoctor = null;
        DoctorsResponse.Doctor sameSpecialtyDoctor = null;
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (doctor.getId() != null && doctor.getId().equals(consultation.getDoctorId())) {
                sameDoctor = doctor;
                break;
            }
            if (sameSpecialtyDoctor == null
                    && !isBlank(consultation.getDoctorSpecialty())
                    && consultation.getDoctorSpecialty().equalsIgnoreCase(doctor.getSpecialty())) {
                sameSpecialtyDoctor = doctor;
            }
        }

        autoRecommendedId = null;
        if (sameDoctor != null) {
            selectedDoctorId = sameDoctor.getId();
            selectedDoctorName = sameDoctor.getName();
            tvSelectedDoctor.setText(sameDoctor.getName());
            if (showFeedback) {
                Toast.makeText(this, "Follow-up matched to the same doctor", Toast.LENGTH_SHORT).show();
            }
        } else if (sameSpecialtyDoctor != null) {
            selectedDoctorId = sameSpecialtyDoctor.getId();
            selectedDoctorName = sameSpecialtyDoctor.getName();
            tvSelectedDoctor.setText(sameSpecialtyDoctor.getName());
            if (showFeedback) {
                Toast.makeText(this, "Previous doctor unavailable. Routed to the same specialty.", Toast.LENGTH_SHORT).show();
            }
        } else {
            selectedDoctorId = null;
            selectedDoctorName = null;
            tvSelectedDoctor.setText("Select doctor manually");
            if (showFeedback) {
                Toast.makeText(this, "Previous doctor unavailable. Please pick a doctor manually.", Toast.LENGTH_SHORT).show();
            }
        }

        filterAndRenderDoctors(
                etDoctorSearch.getText() != null
                        ? etDoctorSearch.getText().toString().trim().toLowerCase() : "");
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

    /**
     * Populate and show the test recommendations card.
     * Called after a successful queue join when the server returns suggestions.
     */
    private void showTestRecommendations(
            List<TestRecommendationResponse.Recommendation> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) return;

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
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isInQueue) {
            loadConsultationHistory(false);
        }
    }
}

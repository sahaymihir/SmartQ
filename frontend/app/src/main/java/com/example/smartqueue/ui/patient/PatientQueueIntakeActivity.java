package com.example.smartqueue.ui.patient;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.request.SymptomRequest;
import com.example.smartqueue.models.response.CheckInStatusResponse;
import com.example.smartqueue.models.response.DoctorsResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.SymptomPredictResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.utils.ApiErrorParser;
import com.example.smartqueue.utils.RoleNavigationHelper;
import com.example.smartqueue.utils.SessionFlowHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PatientQueueIntakeActivity extends AppCompatActivity {

    private TextView tvIntakeEyebrow;
    private TextView tvIntakeTitle;
    private TextView tvIntakeSubtitle;
    private androidx.cardview.widget.CardView cardFollowUpContext;
    private TextView tvFollowUpMeta;
    private TextView tvFollowUpResolution;
    private TextInputEditText etSymptoms;
    private MaterialButton btnVoiceInput;
    private MaterialButton btnFindDoctor;
    private LinearLayout layoutAiResult;
    private TextView tvAiTitle;
    private TextView tvAiMeta;
    private TextView tvAiReasoning;
    private TextView tvSelectedDoctor;
    private AutoCompleteTextView dropdownDoctorPicker;
    private TextView tvDoctorListLoading;
    private TextView tvArrivalChip;
    private TextView tvArrivalMeta;
    private MaterialButton btnCheckIn;
    private MaterialButton btnJoinQueue;

    private SessionManager sessionManager;
    private ApiService apiService;
    private PatientQueueFlowHelper.QueueLaunchArgs launchArgs;

    private final List<DoctorsResponse.Doctor> allDoctors = new ArrayList<>();
    private final List<String> doctorPickerOptions = new ArrayList<>();
    private ArrayAdapter<String> doctorPickerAdapter;
    private String selectedDoctorId;
    private String selectedDoctorName;
    private boolean hasCheckedInToday = false;
    private boolean isDoctorListLoading = false;
    private PendingJoinRequest pendingJoinRequest;
    private ActivityResultLauncher<Intent> speechInputLauncher;

    private static class PendingJoinRequest {
        final String doctorId;
        final String doctorName;
        final String symptoms;
        final String visitType;
        final String followUpTokenId;

        PendingJoinRequest(String doctorId, String doctorName, String symptoms, String visitType, String followUpTokenId) {
            this.doctorId = doctorId;
            this.doctorName = doctorName;
            this.symptoms = symptoms;
            this.visitType = visitType;
            this.followUpTokenId = followUpTokenId;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_queue_intake);

        sessionManager = new SessionManager(this);
        String role = RoleNavigationHelper.normalizeRole(sessionManager.getRole());
        if (!"patient".equals(role)) {
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
                    if (etSymptoms.getText() != null) {
                        etSymptoms.setSelection(etSymptoms.getText().length());
                    }
                }
        );

        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);
        launchArgs = PatientQueueFlowHelper.readArgs(getIntent());

        bindViews();
        configureEntryMode();
        setupClickListeners();
        updateJoinButtonState();
        fetchCheckInStatus(true);
        loadDoctors();
    }

    private void bindViews() {
        MaterialButton btnBack = findViewById(R.id.btnIntakeBack);
        btnBack.setOnClickListener(v -> finish());

        tvIntakeEyebrow = findViewById(R.id.tvIntakeEyebrow);
        tvIntakeTitle = findViewById(R.id.tvIntakeTitle);
        tvIntakeSubtitle = findViewById(R.id.tvIntakeSubtitle);
        cardFollowUpContext = findViewById(R.id.cardFollowUpContext);
        tvFollowUpMeta = findViewById(R.id.tvFollowUpMeta);
        tvFollowUpResolution = findViewById(R.id.tvFollowUpResolution);
        etSymptoms = findViewById(R.id.etQueueSymptoms);
        btnVoiceInput = findViewById(R.id.btnQueueVoiceInput);
        btnFindDoctor = findViewById(R.id.btnQueueFindDoctor);
        layoutAiResult = findViewById(R.id.layoutQueueAiResult);
        tvAiTitle = findViewById(R.id.tvQueueAiTitle);
        tvAiMeta = findViewById(R.id.tvQueueAiMeta);
        tvAiReasoning = findViewById(R.id.tvQueueAiReasoning);
        tvSelectedDoctor = findViewById(R.id.tvQueueSelectedDoctor);
        dropdownDoctorPicker = findViewById(R.id.dropdownQueueDoctorPicker);
        tvDoctorListLoading = findViewById(R.id.tvQueueDoctorListLoading);
        tvArrivalChip = findViewById(R.id.tvQueueArrivalChip);
        tvArrivalMeta = findViewById(R.id.tvQueueArrivalMeta);
        btnCheckIn = findViewById(R.id.btnQueueIntakeCheckIn);
        btnJoinQueue = findViewById(R.id.btnQueueIntakeJoin);
    }

    private void configureEntryMode() {
        if (launchArgs.isFollowUp()) {
            tvIntakeEyebrow.setText("Follow-Up Intake");
            tvIntakeTitle.setText("Continue this care plan");
            tvIntakeSubtitle.setText("Review today's symptoms, confirm the doctor, and send the request back into the live queue.");
            cardFollowUpContext.setVisibility(View.VISIBLE);
            tvFollowUpMeta.setText(
                    "Last visit: "
                            + PatientQueueFlowHelper.textOrDefault(launchArgs.previousDoctorName, "Doctor")
                            + " | "
                            + PatientQueueFlowHelper.formatHistoryDate(launchArgs.previousVisitDate)
            );
            tvFollowUpResolution.setText(getString(R.string.follow_up_same_doctor_hint));
            if (!PatientQueueFlowHelper.isBlank(launchArgs.seededSymptoms)) {
                etSymptoms.setText(launchArgs.seededSymptoms);
            }
        } else {
            tvIntakeEyebrow.setText("New Visit");
            tvIntakeTitle.setText("Tell SmartQ what's going on today");
            tvIntakeSubtitle.setText("Describe symptoms, review the live doctor suggestion, and request a queue slot when you're ready.");
            cardFollowUpContext.setVisibility(View.GONE);
        }
    }

    private void setupClickListeners() {
        btnVoiceInput.setOnClickListener(v -> startVoiceInput());

        btnFindDoctor.setOnClickListener(v -> {
            String symptoms = etSymptoms.getText() != null ? etSymptoms.getText().toString().trim() : "";
            if (PatientQueueFlowHelper.isBlank(symptoms)) {
                Toast.makeText(this, "Please describe your symptoms first", Toast.LENGTH_SHORT).show();
                return;
            }
            predictDoctor(symptoms);
        });

        dropdownDoctorPicker.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLabel = (String) parent.getItemAtPosition(position);
            DoctorsResponse.Doctor selectedDoctor = findDoctorByDisplayLabel(selectedLabel);
            if (selectedDoctor == null) {
                return;
            }
            if (!selectedDoctor.isAvailable()) {
                clearSelectedDoctor("Pick an available doctor");
                Toast.makeText(this, "Selected doctor is unavailable right now", Toast.LENGTH_SHORT).show();
                return;
            }
            applySelectedDoctor(selectedDoctor);
        });

        btnCheckIn.setOnClickListener(v -> promptForHospitalArrival());

        btnJoinQueue.setOnClickListener(v -> {
            if (PatientQueueFlowHelper.isBlank(selectedDoctorId)) {
                Toast.makeText(this, "Please select a doctor first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isDoctorAvailable(selectedDoctorId)) {
                Toast.makeText(this, "This doctor is unavailable right now. Pick another doctor.", Toast.LENGTH_SHORT).show();
                return;
            }
            String symptoms = etSymptoms.getText() != null ? etSymptoms.getText().toString().trim() : "";
            joinQueueWithContext(
                    selectedDoctorId,
                    selectedDoctorName,
                    symptoms,
                    launchArgs.visitType,
                    launchArgs.followUpTokenId
            );
        });
    }

    private void loadDoctors() {
        setDoctorLoadingState(true, getString(R.string.join_queue_loading_doctors));

        apiService.getDoctors().enqueue(new Callback<DoctorsResponse>() {
            @Override
            public void onResponse(Call<DoctorsResponse> call, Response<DoctorsResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()
                        && response.body().getDoctors() != null
                        && !response.body().getDoctors().isEmpty()) {
                    allDoctors.clear();
                    allDoctors.addAll(response.body().getDoctors());
                    updateDoctorPickerOptions();
                    setDoctorLoadingState(false, "");
                    applyFollowUpDoctorDefaults();
                } else {
                    allDoctors.clear();
                    doctorPickerOptions.clear();
                    if (doctorPickerAdapter != null) {
                        doctorPickerAdapter.notifyDataSetChanged();
                    }
                    clearSelectedDoctor("No live doctor roster");
                    setDoctorLoadingState(false, "No doctors found. Ask admin to run Seed Data.");
                }
            }

            @Override
            public void onFailure(Call<DoctorsResponse> call, Throwable t) {
                allDoctors.clear();
                doctorPickerOptions.clear();
                if (doctorPickerAdapter != null) {
                    doctorPickerAdapter.notifyDataSetChanged();
                }
                clearSelectedDoctor("Doctor list unavailable");
                setDoctorLoadingState(false, "Could not load doctors. Is backend running?");
            }
        });
    }

    private void updateDoctorPickerOptions() {
        doctorPickerOptions.clear();
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            doctorPickerOptions.add(PatientQueueFlowHelper.buildDoctorDisplayLabel(doctor));
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

    private void applyFollowUpDoctorDefaults() {
        if (!launchArgs.isFollowUp()) {
            return;
        }

        DoctorsResponse.Doctor followUpDoctor = PatientQueueFlowHelper.resolveFollowUpDoctor(
                allDoctors,
                launchArgs.previousDoctorId,
                launchArgs.previousDoctorSpecialty
        );
        tvFollowUpResolution.setText(
                PatientQueueFlowHelper.buildFollowUpResolutionText(this, launchArgs, followUpDoctor)
        );

        if (followUpDoctor != null) {
            applySelectedDoctor(followUpDoctor);
            dropdownDoctorPicker.setText(PatientQueueFlowHelper.buildDoctorDisplayLabel(followUpDoctor), false);
        }
    }

    private DoctorsResponse.Doctor findDoctorByDisplayLabel(String label) {
        if (PatientQueueFlowHelper.isBlank(label)) {
            return null;
        }
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (PatientQueueFlowHelper.buildDoctorDisplayLabel(doctor).equals(label)) {
                return doctor;
            }
        }
        return null;
    }

    private boolean isDoctorAvailable(String doctorId) {
        if (PatientQueueFlowHelper.isBlank(doctorId)) {
            return false;
        }
        for (DoctorsResponse.Doctor doctor : allDoctors) {
            if (doctorId.equals(doctor.getId())) {
                return doctor.isAvailable();
            }
        }
        return false;
    }

    private void applySelectedDoctor(DoctorsResponse.Doctor doctor) {
        if (doctor == null) {
            clearSelectedDoctor("No doctor selected yet");
            return;
        }
        selectedDoctorId = doctor.getId();
        selectedDoctorName = PatientQueueFlowHelper.textOrDefault(doctor.getName(), "Doctor");
        tvSelectedDoctor.setText(
                selectedDoctorName + " | " + PatientQueueFlowHelper.textOrDefault(doctor.getSpecialty(), "General OPD")
        );
        updateJoinButtonState();
    }

    private void clearSelectedDoctor(String prompt) {
        selectedDoctorId = null;
        selectedDoctorName = null;
        tvSelectedDoctor.setText(prompt);
        updateJoinButtonState();
    }

    private void setDoctorLoadingState(boolean loading, String message) {
        isDoctorListLoading = loading;
        dropdownDoctorPicker.setEnabled(!loading);
        tvDoctorListLoading.setText(message);
        tvDoctorListLoading.setVisibility(
                loading || !PatientQueueFlowHelper.isBlank(message) ? View.VISIBLE : View.GONE
        );
        updateJoinButtonState();
    }

    private void updateJoinButtonState() {
        boolean hasValidDoctor = !PatientQueueFlowHelper.isBlank(selectedDoctorId) && isDoctorAvailable(selectedDoctorId);
        boolean canJoin = !isDoctorListLoading && hasValidDoctor;
        btnJoinQueue.setEnabled(canJoin);

        if (isDoctorListLoading) {
            btnJoinQueue.setText(getString(R.string.join_queue_loading_doctors));
        } else if (!hasValidDoctor) {
            btnJoinQueue.setText(getString(R.string.join_queue_select_doctor));
        } else if (!hasCheckedInToday) {
            btnJoinQueue.setText(getString(R.string.join_queue_save_request));
        } else {
            btnJoinQueue.setText(getString(R.string.join_queue_request_slot));
        }
    }

    private void predictDoctor(String symptoms) {
        btnFindDoctor.setEnabled(false);
        btnFindDoctor.setText("Checking...");

        apiService.predictDoctor(new SymptomRequest(symptoms)).enqueue(new Callback<SymptomPredictResponse>() {
            @Override
            public void onResponse(Call<SymptomPredictResponse> call, Response<SymptomPredictResponse> response) {
                btnFindDoctor.setEnabled(true);
                btnFindDoctor.setText("Get Suggestion");
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    SymptomPredictResponse body = response.body();
                    SymptomPredictResponse.Doctor rec = body.getRecommendedDoctor();
                    String recName = rec != null
                            ? PatientQueueFlowHelper.textOrDefault(rec.getName(), "Recommended doctor")
                            : "No confident doctor suggestion";
                    String recSpecialty = rec != null
                            ? PatientQueueFlowHelper.textOrDefault(rec.getSpecialty(), "General OPD")
                            : "";

                    if (rec != null && rec.getId() != null && isDoctorAvailable(rec.getId())) {
                        for (DoctorsResponse.Doctor doctor : allDoctors) {
                            if (rec.getId().equals(doctor.getId())) {
                                applySelectedDoctor(doctor);
                                dropdownDoctorPicker.setText(PatientQueueFlowHelper.buildDoctorDisplayLabel(doctor), false);
                                break;
                            }
                        }
                    }

                    tvAiTitle.setText(rec != null
                            ? "Suggested doctor: " + recName + " (" + recSpecialty + ")"
                            : "No confident doctor suggestion");

                    StringBuilder meta = new StringBuilder();
                    if (!PatientQueueFlowHelper.isBlank(body.getPrimarySpecialist())) {
                        meta.append("Clinical fit: ").append(body.getPrimarySpecialist());
                    }
                    if (!PatientQueueFlowHelper.isBlank(body.getRoutedSpecialty())) {
                        if (meta.length() > 0) meta.append(" | ");
                        meta.append("SmartQ route: ").append(body.getRoutedSpecialty());
                    }
                    if (body.isLowConfidence()) {
                        if (meta.length() > 0) meta.append(" | ");
                        meta.append("You can change this");
                    }
                    tvAiMeta.setText(meta.length() > 0 ? meta.toString() : "Doctor suggestion");

                    String reasoning = body.getReasoning() != null ? body.getReasoning() : "No additional reasoning available.";
                    if (body.isLowConfidence()) {
                        reasoning += " You can still pick any available doctor.";
                    }
                    tvAiReasoning.setText(reasoning);
                    layoutAiResult.setVisibility(View.VISIBLE);
                } else {
                    Toast.makeText(PatientQueueIntakeActivity.this, "Could not predict doctor. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SymptomPredictResponse> call, Throwable t) {
                btnFindDoctor.setEnabled(true);
                btnFindDoctor.setText("Get Suggestion");
                Toast.makeText(PatientQueueIntakeActivity.this, "Server error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void joinQueueWithContext(
            String doctorId,
            String doctorName,
            String symptoms,
            String visitType,
            String followUpTokenId
    ) {
        if (PatientQueueFlowHelper.isBlank(doctorId)) {
            Toast.makeText(this, "Please select a doctor first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hasCheckedInToday) {
            pendingJoinRequest = new PendingJoinRequest(
                    doctorId,
                    doctorName,
                    symptoms,
                    visitType,
                    followUpTokenId
            );
            showDeferredJoinDialog(doctorName);
            return;
        }

        btnJoinQueue.setEnabled(false);
        btnJoinQueue.setText("Joining...");
        JoinQueueRequest joinRequest = PatientQueueFlowHelper.buildJoinQueueRequest(symptoms, visitType, followUpTokenId);

        apiService.joinQueue(doctorId, joinRequest).enqueue(new Callback<TokenResponse>() {
            @Override
            public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(
                            PatientQueueIntakeActivity.this,
                            response.body().getMessage() != null
                                    ? response.body().getMessage()
                                    : "Queue request submitted.",
                            Toast.LENGTH_SHORT
                    ).show();
                    openDashboard();
                } else {
                    btnJoinQueue.setEnabled(true);
                    updateJoinButtonState();
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(
                            PatientQueueIntakeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not join queue. Try again.",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            }

            @Override
            public void onFailure(Call<TokenResponse> call, Throwable t) {
                btnJoinQueue.setEnabled(true);
                updateJoinButtonState();
                Toast.makeText(PatientQueueIntakeActivity.this, "Server error. Is backend running?", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showDeferredJoinDialog(String doctorName) {
        String targetDoctor = PatientQueueFlowHelper.textOrDefault(doctorName, selectedDoctorName);
        new AlertDialog.Builder(this)
                .setTitle("Queue request saved")
                .setMessage("Your queue request is ready for " + targetDoctor + ". Check in when you arrive to activate it.")
                .setPositiveButton("I'm At Hospital", (dialog, which) -> promptForHospitalArrival())
                .setNegativeButton("Later", null)
                .show();
    }

    private void fetchCheckInStatus(boolean silent) {
        apiService.getCheckInStatus().enqueue(new Callback<CheckInStatusResponse>() {
            @Override
            public void onResponse(Call<CheckInStatusResponse> call, Response<CheckInStatusResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    hasCheckedInToday = response.body().isCheckedIn();
                    updateArrivalState(hasCheckedInToday);
                } else if (!silent) {
                    Toast.makeText(PatientQueueIntakeActivity.this, "Could not fetch check-in status.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<CheckInStatusResponse> call, Throwable t) {
                if (!silent) {
                    Toast.makeText(PatientQueueIntakeActivity.this, "Network error while fetching check-in status.", Toast.LENGTH_SHORT).show();
                }
            }
        });
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
                    boolean hasPending = pendingJoinRequest != null;
                    Toast.makeText(
                            PatientQueueIntakeActivity.this,
                            hasPending
                                    ? "Checked in. Activating your saved queue request."
                                    : "Arrival confirmed. You can request a queue slot now.",
                            Toast.LENGTH_SHORT
                    ).show();
                    activatePendingJoinAfterCheckIn();
                } else {
                    btnCheckIn.setEnabled(true);
                    MessageResponse error = ApiErrorParser.parseMessage(response);
                    Toast.makeText(
                            PatientQueueIntakeActivity.this,
                            error != null && error.getMessage() != null
                                    ? error.getMessage()
                                    : "Could not check in. Try again.",
                            Toast.LENGTH_SHORT
                    ).show();
                    fetchCheckInStatus(true);
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                btnCheckIn.setEnabled(true);
                Toast.makeText(PatientQueueIntakeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void activatePendingJoinAfterCheckIn() {
        if (pendingJoinRequest == null) {
            return;
        }
        PendingJoinRequest request = pendingJoinRequest;
        pendingJoinRequest = null;
        joinQueueWithContext(
                request.doctorId,
                request.doctorName,
                request.symptoms,
                request.visitType,
                request.followUpTokenId
        );
    }

    private void updateArrivalState(boolean checkedIn) {
        if (checkedIn) {
            tvArrivalChip.setText("Arrival confirmed");
            tvArrivalChip.setBackgroundResource(R.drawable.bg_arrival_chip_active);
            tvArrivalChip.setTextColor(getColor(R.color.status_active));
            tvArrivalMeta.setText("Your queue request can go live immediately.");
            btnCheckIn.setEnabled(false);
            btnCheckIn.setText("Checked In");
        } else {
            tvArrivalChip.setText("Not arrived");
            tvArrivalChip.setBackgroundResource(R.drawable.bg_arrival_chip_idle);
            tvArrivalChip.setTextColor(getColor(R.color.on_surface_variant));
            tvArrivalMeta.setText("Save the request now, then confirm only once you reach the hospital.");
            btnCheckIn.setEnabled(true);
            btnCheckIn.setText("I'm At Hospital");
        }
        updateJoinButtonState();
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

    private void openDashboard() {
        Intent intent = new Intent(this, PatientHomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void handleUnauthorized() {
        if (!isFinishing() && sessionManager.isLoggedIn()) {
            SessionFlowHelper.logoutToLogin(this, sessionManager, "Session expired. Please log in again.");
        }
    }
}

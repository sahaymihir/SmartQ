package com.example.smartqueue.ui.patient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.request.NotificationRegistrationRequest;
import com.example.smartqueue.models.response.DoctorListResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PatientHomeActivity extends AppCompatActivity {

    // ── Speech recognition ────────────────────────────────────
    // Voice transcript kept separately so we know whether the user used
    // voice or typed their symptoms.
    private String voiceTranscript = "";
    private boolean isVoiceInput = false;

    // Views
    private TextView tvGreeting, tvPatientName;
    private LinearLayout layoutNotInQueue, layoutInQueue;
    private CardView cardCalled;
    private RadioGroup rgDoctor;
    private MaterialButton btnJoinQueue, btnVoiceInput;
    private TextInputEditText etSymptoms;
    private TextView tvTokenNumber, tvDoctorName, tvPosition, tvETA, tvQueueStatus;
    private TextView tvCheckinTitle, tvCheckinSubtitle, tvSnoozeCount;
    private CardView cardCheckin, cardSnooze;
    private MaterialButton btnCheckIn, btnSnooze, btnLeaveQueue, btnDone;

    private SessionManager sessionManager;
    private ApiService apiService;

    // Polling handler — refreshes queue status every 10s
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private boolean isInQueue = false;
    private final List<DoctorListResponse.Doctor> availableDoctors = new ArrayList<>();

    // ── Speech-to-text launcher (modern Activity Result API) ──
    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            List<String> results = result.getData()
                                    .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                            if (results != null && !results.isEmpty()) {
                                voiceTranscript = results.get(0);
                                isVoiceInput = true;
                                // Pre-fill text field so the patient can review / edit
                                etSymptoms.setText(voiceTranscript);
                                Toast.makeText(this,
                                        "Voice captured — review and join queue",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patient_home);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        setupGreeting();
        setupClickListeners();
        animateEntrance();
        btnJoinQueue.setEnabled(false);
        loadDoctors();
        checkExistingToken();
        registerFcmToken();
    }

    private void bindViews() {
        tvGreeting        = findViewById(R.id.tvGreeting);
        tvPatientName     = findViewById(R.id.tvPatientName);
        layoutNotInQueue  = findViewById(R.id.layoutNotInQueue);
        layoutInQueue     = findViewById(R.id.layoutInQueue);
        cardCalled        = findViewById(R.id.cardCalled);
        rgDoctor          = findViewById(R.id.rgDoctor);
        btnJoinQueue      = findViewById(R.id.btnJoinQueue);
        btnVoiceInput     = findViewById(R.id.btnVoiceInput);
        etSymptoms        = findViewById(R.id.etSymptoms);
        tvTokenNumber     = findViewById(R.id.tvTokenNumber);
        tvDoctorName      = findViewById(R.id.tvDoctorName);
        tvPosition        = findViewById(R.id.tvPosition);
        tvETA             = findViewById(R.id.tvETA);
        tvQueueStatus     = findViewById(R.id.tvQueueStatus);
        tvCheckinTitle    = findViewById(R.id.tvCheckinTitle);
        tvCheckinSubtitle = findViewById(R.id.tvCheckinSubtitle);
        tvSnoozeCount     = findViewById(R.id.tvSnoozeCount);
        cardCheckin       = findViewById(R.id.cardCheckin);
        cardSnooze        = findViewById(R.id.cardSnooze);
        btnCheckIn        = findViewById(R.id.btnCheckIn);
        btnSnooze         = findViewById(R.id.btnSnooze);
        btnLeaveQueue     = findViewById(R.id.btnLeaveQueue);
        btnDone           = findViewById(R.id.btnDone);
        MaterialButton btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    private void setupGreeting() {
        tvPatientName.setText(sessionManager.getName());
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        tvGreeting.setText(hour < 12 ? "Good morning," : hour < 17 ? "Good afternoon," : "Good evening,");
    }

    private void animateEntrance() {
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_enter);

        Handler handler = new Handler(Looper.getMainLooper());

        int childCount = layoutNotInQueue.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = layoutNotInQueue.getChildAt(i);
            int delay = i * 120;
            handler.postDelayed(() -> {
                child.setAlpha(1f);
                child.startAnimation(scaleUp);
            }, delay);
        }
    }

    private void setupClickListeners() {

        // ── Voice Input ────────────────────────────────────────
        btnVoiceInput.setOnClickListener(v -> launchSpeechRecognizer());

        // ── Join Queue ────────────────────────────────────────
        btnJoinQueue.setOnClickListener(v -> {
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();

            String doctorId = getSelectedDoctorId();
            if (doctorId == null) {
                Toast.makeText(PatientHomeActivity.this,
                        "No doctor is available right now.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Build multimodal intake request
            String typedText = etSymptoms.getText() != null
                    ? etSymptoms.getText().toString().trim() : "";
            JoinQueueRequest body;
            if (isVoiceInput && !voiceTranscript.isEmpty()) {
                // Voice path: the text field may have been edited after STT
                body = new JoinQueueRequest(typedText, voiceTranscript, "en");
            } else {
                body = new JoinQueueRequest(typedText);
            }

            btnJoinQueue.setEnabled(false);
            btnJoinQueue.setText("Joining...");

            apiService.joinQueue(doctorId, body).enqueue(new Callback<TokenResponse>() {
                @Override
                public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                    if (response.code() == 401) {
                        handleUnauthorized();
                        return;
                    }

                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        TokenResponse tokenBody = response.body();
                        isInQueue = true;
                        voiceTranscript = "";
                        isVoiceInput = false;
                        showInQueueState();
                        updateQueueUI(tokenBody.getPosition(), tokenBody.getTokenNumber(),
                                tokenBody.getEtaMinutes(), "waiting", false, 0);
                        tvDoctorName.setText(getSelectedDoctorName());
                        startPolling();
                        Toast.makeText(PatientHomeActivity.this,
                                "Token #" + tokenBody.getTokenNumber() + " issued!", Toast.LENGTH_SHORT).show();
                    } else {
                        btnJoinQueue.setEnabled(true);
                        btnJoinQueue.setText("Join Queue");
                        Toast.makeText(PatientHomeActivity.this,
                                "Could not join queue. Try again.", Toast.LENGTH_SHORT).show();
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

        // ── Check In ──────────────────────────────────────────
        btnCheckIn.setOnClickListener(v -> {
            btnCheckIn.setEnabled(false);
            apiService.checkIn().enqueue(new Callback<MessageResponse>() {
                @Override
                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                    if (response.code() == 401) {
                        handleUnauthorized();
                        return;
                    }
                    if (response.isSuccessful() && response.body() != null) {
                        updateCheckinUI(true);
                        Toast.makeText(PatientHomeActivity.this,
                                "Checked in! Hospital notified.", Toast.LENGTH_SHORT).show();
                    } else {
                        btnCheckIn.setEnabled(true);
                    }
                }
                @Override
                public void onFailure(Call<MessageResponse> call, Throwable t) {
                    btnCheckIn.setEnabled(true);
                    Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // ── Snooze ────────────────────────────────────────────
        btnSnooze.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Snooze Queue")
                        .setMessage("You'll be pushed back 2 spots. Max 2 snoozes.")
                        .setPositiveButton("Snooze", (dialog, which) -> {
                            apiService.snoozeQueue(2).enqueue(new Callback<MessageResponse>() {
                                @Override
                                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                                    if (response.code() == 401) {
                                        handleUnauthorized();
                                        return;
                                    }
                                    if (response.isSuccessful() && response.body() != null) {
                                        Toast.makeText(PatientHomeActivity.this,
                                                response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                        fetchQueueStatus();
                                    }
                                }
                                @Override
                                public void onFailure(Call<MessageResponse> call, Throwable t) {
                                    Toast.makeText(PatientHomeActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                                }
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        // ── Leave Queue ───────────────────────────────────────
        btnLeaveQueue.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Leave Queue?")
                        .setMessage("Cancel your token?")
                        .setPositiveButton("Leave", (dialog, which) -> {
                            apiService.leaveQueue().enqueue(new Callback<MessageResponse>() {
                                @Override
                                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                                    if (response.code() == 401) {
                                        handleUnauthorized();
                                        return;
                                    }

                                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                        stopPolling();
                                        isInQueue = false;
                                        showNotInQueueState();
                                        btnJoinQueue.setEnabled(!availableDoctors.isEmpty());
                                        btnJoinQueue.setText("Join Queue");
                                        Toast.makeText(PatientHomeActivity.this,
                                                response.body().getMessage(), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(PatientHomeActivity.this,
                                                "Could not leave queue right now.", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<MessageResponse> call, Throwable t) {
                                    Toast.makeText(PatientHomeActivity.this,
                                            "Network error while leaving queue", Toast.LENGTH_SHORT).show();
                                }
                            });
                        })
                        .setNegativeButton("Stay", null)
                        .show()
        );

        // ── Done ──────────────────────────────────────────────
        btnDone.setOnClickListener(v -> {
            stopPolling();
            isInQueue = false;
            showNotInQueueState();
            btnJoinQueue.setEnabled(!availableDoctors.isEmpty());
            btnJoinQueue.setText("Join Queue");
        });
    }

    // ─── Voice input ──────────────────────────────────────────

    private void launchSpeechRecognizer() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your symptoms");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        try {
            speechLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this,
                    "Voice input is not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    // ─── FCM token registration ───────────────────────────────

    /** Fetch the current FCM token and register it with the backend. */
    private void registerFcmToken() {
        if (!sessionManager.isLoggedIn()) return;
        FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(fcmToken -> {
                    if (fcmToken == null || fcmToken.isEmpty()) return;
                    apiService.registerDeviceToken(new NotificationRegistrationRequest(fcmToken))
                            .enqueue(new Callback<MessageResponse>() {
                                @Override public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {}
                                @Override public void onFailure(Call<MessageResponse> call, Throwable t) {}
                            });
                })
                .addOnFailureListener(e -> { /* non-fatal — polling is the fallback */ });
    }

    private void checkExistingToken() {
        apiService.getQueueStatus().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.code() == 401) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    isInQueue = true;
                    showInQueueState();
                    updateQueueUI(body.getPosition(), body.getTokenNumber(),
                            body.getEtaMinutes(), body.getStatus(), body.isCheckedIn(), 0);
                    if (body.getDoctorName() != null) tvDoctorName.setText(body.getDoctorName());
                    if ("called".equals(body.getStatus())) showCalledState();
                    else startPolling();
                } else {
                    showNotInQueueState();
                }
            }
            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                showNotInQueueState();
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

    private void loadDoctors() {
        apiService.getDoctors().enqueue(new Callback<DoctorListResponse>() {
            @Override
            public void onResponse(Call<DoctorListResponse> call, Response<DoctorListResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<DoctorListResponse.Doctor> doctors = response.body().getDoctors();
                    availableDoctors.clear();
                    if (doctors != null) availableDoctors.addAll(doctors);
                    renderDoctorOptions();
                } else {
                    btnJoinQueue.setEnabled(false);
                    Toast.makeText(PatientHomeActivity.this,
                            "Could not load available doctors.", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<DoctorListResponse> call, Throwable t) {
                btnJoinQueue.setEnabled(false);
                Toast.makeText(PatientHomeActivity.this,
                        "Failed to load doctors", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderDoctorOptions() {
        rgDoctor.removeAllViews();
        if (availableDoctors.isEmpty()) {
            btnJoinQueue.setEnabled(false);
            Toast.makeText(this, "No doctors are available yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        for (int index = 0; index < availableDoctors.size(); index++) {
            DoctorListResponse.Doctor doctor = availableDoctors.get(index);
            MaterialRadioButton radioButton = new MaterialRadioButton(this);
            radioButton.setId(View.generateViewId());
            radioButton.setTag(doctor.getId());
            radioButton.setText(doctor.getName());
            radioButton.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            radioButton.setButtonTintList(ContextCompat.getColorStateList(this, R.color.primary));
            radioButton.setPadding(8, 8, 8, 8);
            if (index == 0) radioButton.setChecked(true);
            rgDoctor.addView(radioButton);
        }
        btnJoinQueue.setEnabled(true);
    }

    private void stopPolling() {
        if (pollRunnable != null) pollHandler.removeCallbacks(pollRunnable);
    }

    private void fetchQueueStatus() {
        apiService.getQueueStatus().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    if ("called".equals(body.getStatus())) {
                        stopPolling();
                        showCalledState();
                    } else {
                        updateQueueUI(body.getPosition(), body.getTokenNumber(),
                                body.getEtaMinutes(), body.getStatus(), body.isCheckedIn(), 0);
                    }
                }
            }
            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) { }
        });
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
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
            finish();
        }
    }

    private void updateQueueUI(int position, int token, int eta,
                               String status, boolean checkedIn, int snoozeCount) {
        tvTokenNumber.setText("#" + token);
        tvPosition.setText(String.valueOf(position));
        tvETA.setText(eta == 0 ? "Next up!" : "~" + eta + " min");
        tvQueueStatus.setText(formatStatus(status));
        updateCheckinUI(checkedIn);
        tvSnoozeCount.setText("Push back 2 spots (" + snoozeCount + "/2 used)");
    }

    private void updateCheckinUI(boolean isCheckedIn) {
        if (isCheckedIn) {
            tvCheckinTitle.setText("✓ Checked in at hospital");
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
        layoutInQueue.animate().alpha(0).setDuration(200).withEndAction(() -> {
            layoutNotInQueue.setVisibility(View.VISIBLE);
            layoutInQueue.setVisibility(View.GONE);
            cardCalled.setVisibility(View.GONE);
            layoutNotInQueue.setAlpha(0);
            layoutNotInQueue.animate().alpha(1).setDuration(300).start();
        }).start();
    }

    private void showInQueueState() {
        layoutNotInQueue.animate().alpha(0).setDuration(200).withEndAction(() -> {
            layoutNotInQueue.setVisibility(View.GONE);
            layoutInQueue.setVisibility(View.VISIBLE);
            cardCalled.setVisibility(View.GONE);
            layoutInQueue.setAlpha(0);
            layoutInQueue.animate().alpha(1).setDuration(300).start();
        }).start();
    }

    private void showCalledState() {
        layoutNotInQueue.setVisibility(View.GONE);
        layoutInQueue.animate().alpha(0).setDuration(200).withEndAction(() -> {
            layoutInQueue.setVisibility(View.GONE);
            cardCalled.setVisibility(View.VISIBLE);
            cardCalled.setAlpha(0);
            cardCalled.setScaleX(0.9f);
            cardCalled.setScaleY(0.9f);
            cardCalled.animate().alpha(1).scaleX(1).scaleY(1).setDuration(400).start();
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
            cardCalled.startAnimation(pulse);
        }).start();
    }

    private String getSelectedDoctorId() {
        int selectedId = rgDoctor.getCheckedRadioButtonId();
        View selectedView = rgDoctor.findViewById(selectedId);
        if (selectedView == null) return null;
        Object tag = selectedView.getTag();
        return tag instanceof String ? (String) tag : null;
    }

    private String getSelectedDoctorName() {
        int selectedId = rgDoctor.getCheckedRadioButtonId();
        View selectedView = rgDoctor.findViewById(selectedId);
        if (selectedView instanceof MaterialRadioButton) {
            return ((MaterialRadioButton) selectedView).getText().toString();
        }
        return "Selected doctor";
    }

    private String formatStatus(String status) {
        switch (status) {
            case "waiting":   return "Waiting";
            case "called":    return "Called!";
            case "checkedin": return "Arrived";
            default:          return "Waiting";
        }
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    stopPolling();
                    // Unregister FCM token before clearing session
                    apiService.unregisterDeviceToken().enqueue(new Callback<MessageResponse>() {
                        @Override public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {}
                        @Override public void onFailure(Call<MessageResponse> call, Throwable t) {}
                    });
                    sessionManager.clearSession();
                    ApiClient.setAuthToken(null);
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        super.onDestroy();
    }
}

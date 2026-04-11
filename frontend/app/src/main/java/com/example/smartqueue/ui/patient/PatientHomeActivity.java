package com.example.smartqueue.ui.patient;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.JoinQueueRequest;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.TokenResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PatientHomeActivity extends AppCompatActivity {

    // Views
    private TextView tvGreeting, tvPatientName;
    private LinearLayout layoutNotInQueue, layoutInQueue;
    private CardView cardCalled;
    private RadioGroup rgDoctor;
    private MaterialButton btnJoinQueue;
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

    // Doctor IDs — replace with real MongoDB _id values after creating admin accounts
    private static final String[] DOCTOR_IDS = {
            "69d74ea332ca11b9fd32772f",  // Dr. Nisha Shetty
            "69d4dd2e3a9c3c82e6baa59f",  // Dr. Pawan Kumar
            "69d4df3fff45ca443418bd31"   // Dr. Meena Rao
    };

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
        checkExistingToken(); // check if patient already has an active token
    }

    private void bindViews() {
        tvGreeting       = findViewById(R.id.tvGreeting);
        tvPatientName    = findViewById(R.id.tvPatientName);
        layoutNotInQueue = findViewById(R.id.layoutNotInQueue);
        layoutInQueue    = findViewById(R.id.layoutInQueue);
        cardCalled       = findViewById(R.id.cardCalled);
        rgDoctor         = findViewById(R.id.rgDoctor);
        btnJoinQueue     = findViewById(R.id.btnJoinQueue);
        etSymptoms       = findViewById(R.id.etSymptoms);
        tvTokenNumber    = findViewById(R.id.tvTokenNumber);
        tvDoctorName     = findViewById(R.id.tvDoctorName);
        tvPosition       = findViewById(R.id.tvPosition);
        tvETA            = findViewById(R.id.tvETA);
        tvQueueStatus    = findViewById(R.id.tvQueueStatus);
        tvCheckinTitle   = findViewById(R.id.tvCheckinTitle);
        tvCheckinSubtitle = findViewById(R.id.tvCheckinSubtitle);
        tvSnoozeCount    = findViewById(R.id.tvSnoozeCount);
        cardCheckin      = findViewById(R.id.cardCheckin);
        cardSnooze       = findViewById(R.id.cardSnooze);
        btnCheckIn       = findViewById(R.id.btnCheckIn);
        btnSnooze        = findViewById(R.id.btnSnooze);
        btnLeaveQueue    = findViewById(R.id.btnLeaveQueue);
        btnDone          = findViewById(R.id.btnDone);
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

        // Animate cards in the not-in-queue state
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

        // ── Join Queue ────────────────────────────────────
        btnJoinQueue.setOnClickListener(v -> {
            // Button press animation
            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()).start();

            String doctorId = getSelectedDoctorId();
            String symptoms = etSymptoms.getText() != null ?
                    etSymptoms.getText().toString().trim() : "";
            btnJoinQueue.setEnabled(false);
            btnJoinQueue.setText("Joining...");

            JoinQueueRequest body = new JoinQueueRequest(symptoms);
            apiService.joinQueue(doctorId, body).enqueue(new Callback<TokenResponse>() {
                @Override
                public void onResponse(Call<TokenResponse> call, Response<TokenResponse> response) {
                    if (response.code() == 401) {
                        handleUnauthorized();
                        return;
                    }

                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        TokenResponse body = response.body();
                        isInQueue = true;
                        showInQueueState();
                        updateQueueUI(body.getPosition(), body.getTokenNumber(),
                                body.getEtaMinutes(), "waiting", false, 0);
                        tvDoctorName.setText(getSelectedDoctorName());
                        startPolling();
                        Toast.makeText(PatientHomeActivity.this,
                                "Token #" + body.getTokenNumber() + " issued!", Toast.LENGTH_SHORT).show();
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

        // ── Check In ──────────────────────────────────────
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

        // ── Snooze ────────────────────────────────────────
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
                                        fetchQueueStatus(); // refresh immediately
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

        // ── Leave Queue ───────────────────────────────────
        btnLeaveQueue.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Leave Queue?")
                        .setMessage("Cancel your token?")
                        .setPositiveButton("Leave", (dialog, which) -> {
                            stopPolling();
                            isInQueue = false;
                            showNotInQueueState();
                            btnJoinQueue.setEnabled(true);
                            btnJoinQueue.setText("Join Queue");
                        })
                        .setNegativeButton("Stay", null)
                        .show()
        );

        // ── Done ──────────────────────────────────────────
        btnDone.setOnClickListener(v -> {
            stopPolling();
            isInQueue = false;
            showNotInQueueState();
            btnJoinQueue.setEnabled(true);
            btnJoinQueue.setText("Join Queue");
        });
    }

    private void checkExistingToken() {
        apiService.getQueueStatus().enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.code() == 401) {
                    return;
                }
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
        stopPolling(); // Clear existing
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
        // Fade transition between states
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
            cardCalled.animate().alpha(1).scaleX(1).scaleY(1)
                    .setDuration(400).start();
            // Pulse animation on called card
            Animation pulse = AnimationUtils.loadAnimation(this, R.anim.pulse);
            cardCalled.startAnimation(pulse);
        }).start();
    }

    private String getSelectedDoctorId() {
        int selectedId = rgDoctor.getCheckedRadioButtonId();
        if (selectedId == R.id.rbDoctor2) return DOCTOR_IDS[1];
        if (selectedId == R.id.rbDoctor3) return DOCTOR_IDS[2];
        return DOCTOR_IDS[0];
    }

    private String getSelectedDoctorName() {
        int selectedId = rgDoctor.getCheckedRadioButtonId();
        if (selectedId == R.id.rbDoctor2) return "Dr. Pawan Kumar";
        if (selectedId == R.id.rbDoctor3) return "Dr. Meena Rao";
        return "Dr. Nisha Shetty";
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

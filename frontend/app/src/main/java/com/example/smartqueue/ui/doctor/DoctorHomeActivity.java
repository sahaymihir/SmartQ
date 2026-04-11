package com.example.smartqueue.ui.doctor;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.PrescriptionRequest;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
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
    private SwitchMaterial switchAvailability;
    private MaterialButton btnCallNext, btnPrescribe, btnLogout;
    private RecyclerView rvQueue;
    private ProgressBar progressBar;

    private QueueAdapter adapter;
    private SessionManager sessionManager;
    private ApiService apiService;

    private String currentTokenId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_home);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);

        initViews();
        setupRecyclerView();
        setupListeners();
        animateEntrance();

        fetchQueueData();
    }

    private void initViews() {
        tvDoctorName = findViewById(R.id.tvDoctorName);
        tvCurrentPatientName = findViewById(R.id.tvCurrentPatientName);
        tvCurrentPatientToken = findViewById(R.id.tvCurrentPatientToken);
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
        rvQueue.setLayoutManager(new LinearLayoutManager(this));
        rvQueue.setAdapter(adapter);
    }

    private void animateEntrance() {
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_enter);
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);

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

        btnPrescribe.setOnClickListener(v -> showPrescriptionDialog());

        switchAvailability.setOnCheckedChangeListener((buttonView, isChecked) -> {
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

    private void fetchQueueData() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getAdminQueue(sessionManager.getUserId()).enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    List<QueueResponse.QueueEntry> queue = response.body().getQueue();
                    adapter.setQueueList(queue != null ? queue : new ArrayList<>());

                    switchAvailability.setChecked(!response.body().isPaused());

                    if (queue != null && !queue.isEmpty()) {
                        QueueResponse.QueueEntry calledPatient = null;
                        for (QueueResponse.QueueEntry entry : queue) {
                            if ("called".equals(entry.getStatus())) {
                                calledPatient = entry;
                                break;
                            }
                        }

                        if (calledPatient != null) {
                            tvCurrentPatientName.setText(calledPatient.getPatientName());
                            tvCurrentPatientToken.setText("Token #" + calledPatient.getTokenNumber());
                            currentTokenId = calledPatient.getTokenId();
                            btnPrescribe.setVisibility(View.VISIBLE);
                        } else {
                            resetCurrentPatientUI();
                        }
                    } else {
                        resetCurrentPatientUI();
                    }
                }
            }

            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(DoctorHomeActivity.this, "Failed to load queue", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetCurrentPatientUI() {
        tvCurrentPatientName.setText("No patient called");
        tvCurrentPatientToken.setText("Token #--");
        currentTokenId = null;
        btnPrescribe.setVisibility(View.GONE);
    }

    private void callNextPatient() {
        apiService.callNextPatient(sessionManager.getUserId()).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(DoctorHomeActivity.this, "Next patient called", Toast.LENGTH_SHORT).show();
                    fetchQueueData();
                } else {
                    Toast.makeText(DoctorHomeActivity.this, "Queue is empty", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(DoctorHomeActivity.this, "Error calling next", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleAvailability(boolean paused) {
        apiService.togglePause(sessionManager.getUserId(), paused).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful()) {
                    String status = paused ? "Unavailable" : "Available";
                    Toast.makeText(DoctorHomeActivity.this, "Status: " + status, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(DoctorHomeActivity.this, "Failed to update status", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPrescriptionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_prescription, null);
        builder.setView(dialogView);

        EditText etDiagnosis = dialogView.findViewById(R.id.etDiagnosis);
        EditText etMedicines = dialogView.findViewById(R.id.etMedicines);
        EditText etNotes = dialogView.findViewById(R.id.etNotes);

        builder.setTitle("Write Prescription")
               .setPositiveButton("Save", (dialog, which) -> {
                   String diagnosis = etDiagnosis.getText().toString();
                   String medicines = etMedicines.getText().toString();
                   String notes = etNotes.getText().toString();
                   savePrescription(diagnosis, medicines, notes);
               })
               .setNegativeButton("Cancel", null)
               .show();
    }

    private void savePrescription(String diagnosis, String medicines, String notes) {
        if (currentTokenId == null) return;

        PrescriptionRequest request = new PrescriptionRequest(currentTokenId, diagnosis, medicines, notes);
        apiService.savePrescription(request).enqueue(new Callback<MessageResponse>() {
            @Override
            public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(DoctorHomeActivity.this, "Prescription saved", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<MessageResponse> call, Throwable t) {
                Toast.makeText(DoctorHomeActivity.this, "Failed to save", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

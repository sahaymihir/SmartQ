package com.example.smartqueue.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.DoctorListResponse;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView tvAdminName, tvCurrentlyServing, tvPausedBadge;
    private TextView tvStatWaiting, tvStatDone, tvStatAvg, tvQueueLabel, tvSelectedDoctor;
    private MaterialButton btnCallNext, btnPause, btnLogout, btnSwitchDoctor;
    private LinearLayout layoutQueueList;

    private SessionManager sessionManager;
    private ApiService apiService;
    private boolean isPaused = false;
    private int consultationsDone = 0;
    private final List<DoctorListResponse.Doctor> doctorOptions = new ArrayList<>();
    private int currentDoctorIndex = 0;
    private String doctorId;
    private String selectedDoctorName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);
        doctorId = null;

        bindViews();
        setupClickListeners();
        animateEntrance();

        if ("doctor".equals(sessionManager.getRole())) {
            doctorId = sessionManager.getUserId();
            selectedDoctorName = sessionManager.getName();
            updateDoctorContextUI();
            btnSwitchDoctor.setVisibility(View.GONE);
            btnCallNext.setEnabled(true);
            btnPause.setEnabled(true);
            loadQueue();
        } else {
            btnSwitchDoctor.setVisibility(View.VISIBLE);
            btnCallNext.setEnabled(false);
            btnPause.setEnabled(false);
            btnSwitchDoctor.setEnabled(false);
            loadDoctors();
        }
    }

    private void bindViews() {
        tvAdminName        = findViewById(R.id.tvAdminName);
        tvCurrentlyServing = findViewById(R.id.tvCurrentlyServing);
        tvPausedBadge      = findViewById(R.id.tvPausedBadge);
        tvStatWaiting      = findViewById(R.id.tvStatWaiting);
        tvStatDone         = findViewById(R.id.tvStatDone);
        tvStatAvg          = findViewById(R.id.tvStatAvg);
        tvQueueLabel       = findViewById(R.id.tvQueueLabel);
        tvSelectedDoctor   = findViewById(R.id.tvSelectedDoctor);
        layoutQueueList    = findViewById(R.id.layoutQueueList);
        btnCallNext        = findViewById(R.id.btnCallNext);
        btnPause           = findViewById(R.id.btnPause);
        btnSwitchDoctor    = findViewById(R.id.btnSwitchDoctor);
        btnLogout          = findViewById(R.id.btnLogout);

        tvAdminName.setText(sessionManager.getName());
        tvAdminName.setVisibility(View.VISIBLE);
    }

    private void animateEntrance() {
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_enter);
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);

        Handler handler = new Handler(Looper.getMainLooper());

        // Stagger stat cards entrance
        handler.postDelayed(() -> {
            if (tvStatWaiting.getParent() != null) {
                View statsRow = (View) ((View) tvStatWaiting.getParent()).getParent().getParent();
                statsRow.startAnimation(scaleUp);
            }
        }, 200);
    }

    private void setupClickListeners() {

        // ── Call Next ─────────────────────────────────────
        btnCallNext.setOnClickListener(v -> {
            if (!hasDoctorContext()) {
                Toast.makeText(this, "No doctor queue selected yet.", Toast.LENGTH_SHORT).show();
                return;
            }

            v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                    }).start();

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
                    }
                }
                @Override
                public void onFailure(Call<MessageResponse> call, Throwable t) {
                    btnCallNext.setEnabled(true);
                    Toast.makeText(AdminDashboardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // ── Pause / Resume ────────────────────────────────
        btnPause.setOnClickListener(v -> {
            if (!hasDoctorContext()) {
                Toast.makeText(this, "No doctor queue selected yet.", Toast.LENGTH_SHORT).show();
                return;
            }

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

        btnSwitchDoctor.setOnClickListener(v -> {
            if (doctorOptions.isEmpty()) {
                Toast.makeText(this, "No doctors available.", Toast.LENGTH_SHORT).show();
                return;
            }

            currentDoctorIndex = (currentDoctorIndex + 1) % doctorOptions.size();
            applyDoctorSelection(doctorOptions.get(currentDoctorIndex));
            loadQueue();
        });

        // ── Logout ────────────────────────────────────────
        btnLogout.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Logout")
                        .setMessage("Are you sure?")
                        .setPositiveButton("Logout", (d, w) -> {
                            sessionManager.clearSession();
                            startActivity(new Intent(this, LoginActivity.class));
                            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );
    }

    private boolean hasDoctorContext() {
        return doctorId != null && !doctorId.isEmpty();
    }

    private void loadDoctors() {
        apiService.getDoctors().enqueue(new Callback<DoctorListResponse>() {
            @Override
            public void onResponse(Call<DoctorListResponse> call, Response<DoctorListResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    doctorOptions.clear();
                    if (response.body().getDoctors() != null) {
                        doctorOptions.addAll(response.body().getDoctors());
                    }

                    if (doctorOptions.isEmpty()) {
                        doctorId = null;
                        selectedDoctorName = null;
                        updateDoctorContextUI();
                        btnCallNext.setEnabled(false);
                        btnPause.setEnabled(false);
                        btnSwitchDoctor.setEnabled(false);
                        Toast.makeText(AdminDashboardActivity.this,
                                "No doctor accounts found.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    btnCallNext.setEnabled(true);
                    btnPause.setEnabled(true);
                    btnSwitchDoctor.setEnabled(doctorOptions.size() > 1);
                    applyDoctorSelection(doctorOptions.get(0));
                    loadQueue();
                } else {
                    Toast.makeText(AdminDashboardActivity.this,
                            "Could not load doctor list", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DoctorListResponse> call, Throwable t) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Failed to load doctors", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyDoctorSelection(DoctorListResponse.Doctor doctor) {
        if (doctor == null) {
            doctorId = null;
            selectedDoctorName = null;
        } else {
            doctorId = doctor.getId();
            selectedDoctorName = doctor.getName();
        }
        updateDoctorContextUI();
    }

    private void updateDoctorContextUI() {
        if (selectedDoctorName == null || selectedDoctorName.isEmpty()) {
            tvSelectedDoctor.setText("No doctor queue selected");
            return;
        }
        if ("doctor".equals(sessionManager.getRole())) {
            tvSelectedDoctor.setText("Managing your queue");
        } else {
            tvSelectedDoctor.setText("Viewing queue for " + selectedDoctorName);
        }
    }

    private void loadQueue() {
        if (!hasDoctorContext()) {
            return;
        }

        apiService.getAdminQueue(doctorId).enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    List<QueueResponse.QueueEntry> queue = body.getQueue();

                    tvStatWaiting.setText(String.valueOf(queue != null ? queue.size() : 0));
                    tvStatAvg.setText(body.getAvgConsultationMinutes() + "m");
                    tvQueueLabel.setText((queue != null ? queue.size() : 0) + " waiting");
                    updateCurrentlyServing(queue);

                    if (queue != null) renderQueueList(queue);
                }
            }
            @Override
            public void onFailure(Call<QueueResponse> call, Throwable t) {
                Toast.makeText(AdminDashboardActivity.this,
                        "Could not load queue", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCurrentlyServing(List<QueueResponse.QueueEntry> queue) {
        if (queue == null || queue.isEmpty()) {
            tvCurrentlyServing.setText("No patient called yet");
            return;
        }

        for (QueueResponse.QueueEntry entry : queue) {
            if ("called".equals(entry.getStatus())) {
                tvCurrentlyServing.setText("Token #" + entry.getTokenNumber() + " — " + entry.getPatientName());
                return;
            }
        }

        tvCurrentlyServing.setText("No patient called yet");
    }

    private void renderQueueList(List<QueueResponse.QueueEntry> queue) {
        layoutQueueList.removeAllViews();

        if (queue.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("🎉 Queue is empty!");
            tv.setTextSize(16f);
            tv.setPadding(0, 80, 0, 80);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTextColor(getResources().getColor(R.color.text_secondary));
            layoutQueueList.addView(tv);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        Animation scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up);

        int index = 0;
        for (QueueResponse.QueueEntry entry : queue) {
            View row = inflater.inflate(R.layout.item_patient_queue, layoutQueueList, false);

            TextView tvPos      = row.findViewById(R.id.tvItemPosition);
            TextView tvName     = row.findViewById(R.id.tvItemName);
            TextView tvPriority = row.findViewById(R.id.tvItemPriority);
            TextView tvEta      = row.findViewById(R.id.tvItemEta);
            MaterialButton btnNoShow = row.findViewById(R.id.btnItemNoShow);

            tvPos.setText(String.valueOf(entry.getPosition()));
            tvName.setText(entry.getPatientName());
            int eta = entry.getEtaMinutes();
            tvEta.setText(eta == 0 ? "Next up" : "~" + eta + " min");

            String priority = entry.getPriority();
            tvPriority.setText(capitalize(priority));
            switch (priority) {
                case "high":   tvPriority.setBackgroundResource(R.drawable.badge_high); row.findViewById(R.id.tvItemPosition_bg).setBackgroundResource(R.drawable.circle_priority_high); break;
                case "medium": tvPriority.setBackgroundResource(R.drawable.badge_medium); row.findViewById(R.id.tvItemPosition_bg).setBackgroundResource(R.drawable.circle_primary); break;
                default:       tvPriority.setBackgroundResource(R.drawable.badge_normal); row.findViewById(R.id.tvItemPosition_bg).setBackgroundResource(R.drawable.circle_primary); break;
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

            // Staggered entrance animation for each queue item
            int delay = index * 80;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                row.startAnimation(scaleUp);
            }, delay);

            layoutQueueList.addView(row);
            index++;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}

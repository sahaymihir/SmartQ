package com.example.smartqueue.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.MessageResponse;
import com.example.smartqueue.models.response.QueueResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AdminDashboardActivity extends AppCompatActivity {

    private TextView tvAdminName, tvCurrentlyServing, tvPausedBadge;
    private TextView tvStatWaiting, tvStatDone, tvStatAvg, tvQueueLabel;
    private MaterialButton btnCallNext, btnPause, btnLogout, btnModelEval, btnSeedData;
    private LinearLayout layoutQueueList;

    private SessionManager sessionManager;
    private ApiService apiService;
    private boolean isPaused = false;
    private int consultationsDone = 0;

    // The admin's own doctor ID — set after login
    // This is the MongoDB _id of the logged-in admin user
    private String doctorId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        sessionManager = new SessionManager(this);
        apiService = ApiClient.getInstance().create(ApiService.class);
        doctorId = sessionManager.getUserId(); // admin's own _id is their doctorId

        bindViews();
        setupClickListeners();
        loadQueue();
    }

    private void bindViews() {
        tvAdminName        = findViewById(R.id.tvAdminName);
        tvCurrentlyServing = findViewById(R.id.tvCurrentlyServing);
        tvPausedBadge      = findViewById(R.id.tvPausedBadge);
        tvStatWaiting      = findViewById(R.id.tvStatWaiting);
        tvStatDone         = findViewById(R.id.tvStatDone);
        tvStatAvg          = findViewById(R.id.tvStatAvg);
        tvQueueLabel       = findViewById(R.id.tvQueueLabel);
        layoutQueueList    = findViewById(R.id.layoutQueueList);
        btnCallNext        = findViewById(R.id.btnCallNext);
        btnPause           = findViewById(R.id.btnPause);
        btnLogout          = findViewById(R.id.btnLogout);

        tvAdminName.setText(sessionManager.getName());
        btnModelEval   = findViewById(R.id.btnModelEval);
        btnSeedData    = findViewById(R.id.btnSeedData);
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
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show()
        );

        // ── Model Evaluation ──────────────────────────────
        btnModelEval.setOnClickListener(v ->
                startActivity(new Intent(this, ModelEvalActivity.class))
        );

        // ── Seed Dummy Data ───────────────────────────────
        btnSeedData.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Seed Demo Data")
                        .setMessage("This will create 8 Indian doctors and 12 Indian patient accounts. Existing accounts will be skipped. Continue?")
                        .setPositiveButton("Seed", (d, w) -> {
                            btnSeedData.setEnabled(false);
                            apiService.seedDummyData().enqueue(new Callback<MessageResponse>() {
                                @Override
                                public void onResponse(Call<MessageResponse> call, Response<MessageResponse> response) {
                                    btnSeedData.setEnabled(true);
                                    if (response.isSuccessful() && response.body() != null) {
                                        Toast.makeText(AdminDashboardActivity.this,
                                                response.body().getMessage(), Toast.LENGTH_LONG).show();
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

    private void loadQueue() {
        apiService.getAdminQueue(doctorId).enqueue(new Callback<QueueResponse>() {
            @Override
            public void onResponse(Call<QueueResponse> call, Response<QueueResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    QueueResponse body = response.body();
                    List<QueueResponse.QueueEntry> queue = body.getQueue();

                    tvStatWaiting.setText(String.valueOf(queue != null ? queue.size() : 0));
                    tvStatAvg.setText(body.getAvgConsultationMinutes() + "m");
                    tvQueueLabel.setText((queue != null ? queue.size() : 0) + " waiting");

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
                case "high":   tvPriority.setBackgroundResource(R.drawable.badge_high); tvPos.setBackgroundResource(R.drawable.circle_priority_high); break;
                case "medium": tvPriority.setBackgroundResource(R.drawable.badge_medium); tvPos.setBackgroundResource(R.drawable.circle_primary); break;
                default:       tvPriority.setBackgroundResource(R.drawable.badge_normal); tvPos.setBackgroundResource(R.drawable.circle_primary); break;
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

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
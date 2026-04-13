package com.example.smartqueue.ui.doctor;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.response.ConsultationHistoryResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.ui.prescription.PrescriptionActivity;
import com.example.smartqueue.utils.SessionFlowHelper;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DoctorVisitHistoryActivity extends AppCompatActivity {

    public static final String EXTRA_PATIENT_ID = "extra_patient_id";
    public static final String EXTRA_PATIENT_NAME = "extra_patient_name";

    private SessionManager sessionManager;
    private ApiService apiService;
    private String patientId;

    private TextView tvHistorySubtitle;
    private TextView tvHistoryEmpty;
    private LinearLayout layoutHistoryList;
    private ProgressBar historyProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_doctor_visit_history);

        sessionManager = new SessionManager(this);
        patientId = getIntent().getStringExtra(EXTRA_PATIENT_ID);
        String patientName = getIntent().getStringExtra(EXTRA_PATIENT_NAME);

        if (TextUtils.isEmpty(patientId)) {
            Toast.makeText(this, "Patient record unavailable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        tvHistorySubtitle.setText("Visit history for " + textOrDefault(patientName, "patient"));
        loadPatientHistory();
    }

    private void bindViews() {
        MaterialButton btnBack = findViewById(R.id.btnHistoryBack);
        tvHistorySubtitle = findViewById(R.id.tvHistorySubtitle);
        tvHistoryEmpty = findViewById(R.id.tvHistoryEmpty);
        layoutHistoryList = findViewById(R.id.layoutHistoryList);
        historyProgress = findViewById(R.id.historyProgress);

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadPatientHistory() {
        historyProgress.setVisibility(View.VISIBLE);
        tvHistoryEmpty.setVisibility(View.GONE);

        apiService.getPatientHistory(patientId, 50).enqueue(new Callback<ConsultationHistoryResponse>() {
            @Override
            public void onResponse(Call<ConsultationHistoryResponse> call,
                                   Response<ConsultationHistoryResponse> response) {
                historyProgress.setVisibility(View.GONE);

                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    tvHistoryEmpty.setVisibility(View.VISIBLE);
                    tvHistoryEmpty.setText("Could not load visit history.");
                    return;
                }

                List<ConsultationHistoryResponse.Consultation> history = response.body().getHistory();
                if (history == null || history.isEmpty()) {
                    tvHistoryEmpty.setVisibility(View.VISIBLE);
                    tvHistoryEmpty.setText("No completed visits found for this patient yet.");
                    return;
                }

                renderHistory(history);
            }

            @Override
            public void onFailure(Call<ConsultationHistoryResponse> call, Throwable t) {
                historyProgress.setVisibility(View.GONE);
                tvHistoryEmpty.setVisibility(View.VISIBLE);
                tvHistoryEmpty.setText("Network error while loading visit history.");
            }
        });
    }

    private void renderHistory(List<ConsultationHistoryResponse.Consultation> history) {
        layoutHistoryList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ConsultationHistoryResponse.Consultation consultation : history) {
            View card = inflater.inflate(R.layout.item_consultation_history, layoutHistoryList, false);
            TextView tvDate = card.findViewById(R.id.tvHistoryDate);
            TextView tvDoctor = card.findViewById(R.id.tvHistoryDoctor);
            TextView tvType = card.findViewById(R.id.tvHistoryType);
            TextView tvSummary = card.findViewById(R.id.tvHistorySummary);
            TextView tvOutcome = card.findViewById(R.id.tvHistoryOutcome);

            tvDate.setText(formatHistoryDate(consultation.getDate()));
            tvDoctor.setText(textOrDefault(consultation.getDoctorName(), "Doctor"));
            tvType.setText(formatVisitTypeLabel(consultation.getVisitType(), consultation.getDoctorSpecialty()));
            tvSummary.setText(!isBlank(consultation.getSymptomsSummary())
                    ? consultation.getSymptomsSummary()
                    : (!isBlank(consultation.getSymptoms()) ? consultation.getSymptoms() : "No symptom summary recorded."));
            tvOutcome.setText(!isBlank(consultation.getConclusionPreview())
                    ? consultation.getConclusionPreview()
                    : (!isBlank(consultation.getDiagnosis()) ? consultation.getDiagnosis() : "Open for full prescription details."));

            card.setOnClickListener(v -> openPrescriptionScreen(consultation.getTokenId()));
            layoutHistoryList.addView(card);
        }
    }

    private void openPrescriptionScreen(String tokenId) {
        if (isBlank(tokenId)) {
            Toast.makeText(this, "Prescription record not available", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, PrescriptionActivity.class);
        intent.putExtra(PrescriptionActivity.EXTRA_TOKEN_ID, tokenId);
        intent.putExtra(PrescriptionActivity.EXTRA_READ_ONLY, true);
        startActivity(intent);
    }

    private void handleUnauthorized() {
        if (!isFinishing()) {
            SessionFlowHelper.logoutToLogin(this, sessionManager, "Session expired. Please log in again.");
        }
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
}

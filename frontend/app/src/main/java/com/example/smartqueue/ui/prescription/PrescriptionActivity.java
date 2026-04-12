package com.example.smartqueue.ui.prescription;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.PrescriptionRequest;
import com.example.smartqueue.models.response.PrescriptionResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.example.smartqueue.ui.auth.LoginActivity;
import com.example.smartqueue.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PrescriptionActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN_ID = "extra_token_id";
    public static final String EXTRA_READ_ONLY = "extra_read_only";

    private TextView tvPrescriptionTitle;
    private TextView tvPrescriptionSubtitle;
    private TextView tvPrescriptionPatient;
    private TextView tvPrescriptionMeta;
    private TextView tvPrescriptionStatus;
    private TextInputEditText etSymptomsSummary;
    private TextInputEditText etTestsDone;
    private TextInputEditText etMedications;
    private TextInputEditText etConclusion;
    private TextInputEditText etAdviceNotes;
    private MaterialButton btnSaveDraft;
    private MaterialButton btnFinalize;
    private MaterialButton btnBack;
    private View layoutPrescriptionActions;
    private ProgressBar progressBar;

    private SessionManager sessionManager;
    private ApiService apiService;
    private String tokenId;
    private boolean readOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prescription);

        sessionManager = new SessionManager(this);
        tokenId = getIntent().getStringExtra(EXTRA_TOKEN_ID);
        readOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);

        if (TextUtils.isEmpty(tokenId)) {
            Toast.makeText(this, getString(R.string.prescription_missing_token), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ApiClient.setAuthToken(sessionManager.getToken());
        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        setupListeners();
        loadPrescription();
    }

    private void bindViews() {
        tvPrescriptionTitle = findViewById(R.id.tvPrescriptionTitle);
        tvPrescriptionSubtitle = findViewById(R.id.tvPrescriptionSubtitle);
        tvPrescriptionPatient = findViewById(R.id.tvPrescriptionPatient);
        tvPrescriptionMeta = findViewById(R.id.tvPrescriptionMeta);
        tvPrescriptionStatus = findViewById(R.id.tvPrescriptionStatus);
        etSymptomsSummary = findViewById(R.id.etSymptomsSummary);
        etTestsDone = findViewById(R.id.etTestsDone);
        etMedications = findViewById(R.id.etMedications);
        etConclusion = findViewById(R.id.etConclusion);
        etAdviceNotes = findViewById(R.id.etAdviceNotes);
        btnSaveDraft = findViewById(R.id.btnSaveDraft);
        btnFinalize = findViewById(R.id.btnFinalize);
        btnBack = findViewById(R.id.btnPrescriptionBack);
        layoutPrescriptionActions = findViewById(R.id.layoutPrescriptionActions);
        progressBar = findViewById(R.id.prescriptionProgress);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSaveDraft.setOnClickListener(v -> submitPrescription("draft"));
        btnFinalize.setOnClickListener(v -> submitPrescription("finalized"));
    }

    private void loadPrescription() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getPrescription(tokenId).enqueue(new Callback<PrescriptionResponse>() {
            @Override
            public void onResponse(Call<PrescriptionResponse> call, Response<PrescriptionResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    bindPrescription(response.body());
                } else {
                    Toast.makeText(PrescriptionActivity.this,
                            "Could not load prescription", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onFailure(Call<PrescriptionResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(PrescriptionActivity.this,
                        "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindPrescription(PrescriptionResponse body) {
        tvPrescriptionTitle.setText(readOnly
                ? getString(R.string.prescription_title)
                : getString(R.string.prescription_title));
        tvPrescriptionSubtitle.setText(readOnly
                ? getString(R.string.prescription_read_only)
                : getString(R.string.prescription_editable));
        tvPrescriptionPatient.setText(body.getPatientName());
        tvPrescriptionMeta.setText(buildMeta(body));
        tvPrescriptionStatus.setText("finalized".equals(body.getStatus())
                ? getString(R.string.prescription_status_finalized)
                : getString(R.string.prescription_status_draft));

        String summary = !TextUtils.isEmpty(body.getSymptomsSummary())
                ? body.getSymptomsSummary()
                : body.getReportedSymptoms();

        etSymptomsSummary.setText(summary);
        etTestsDone.setText(body.getTestsDone());
        etMedications.setText(body.getMedications());
        etConclusion.setText(body.getConclusion());
        etAdviceNotes.setText(body.getAdviceNotes());

        boolean editable = !readOnly && body.canEdit();
        setEditorEnabled(editable);
    }

    private void setEditorEnabled(boolean editable) {
        setFieldEditable(etSymptomsSummary, editable);
        setFieldEditable(etTestsDone, editable);
        setFieldEditable(etMedications, editable);
        setFieldEditable(etConclusion, editable);
        setFieldEditable(etAdviceNotes, editable);
        layoutPrescriptionActions.setVisibility(editable ? View.VISIBLE : View.GONE);
    }

    private void setFieldEditable(TextInputEditText field, boolean editable) {
        field.setEnabled(editable);
        field.setFocusable(editable);
        field.setFocusableInTouchMode(editable);
        field.setCursorVisible(editable);
    }

    private void submitPrescription(String status) {
        if (!hasAnyInput()) {
            Toast.makeText(this, getString(R.string.prescription_empty_error), Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        PrescriptionRequest request = new PrescriptionRequest(
                textOf(etSymptomsSummary),
                textOf(etTestsDone),
                textOf(etMedications),
                textOf(etConclusion),
                textOf(etAdviceNotes),
                status
        );

        apiService.savePrescription(tokenId, request).enqueue(new Callback<PrescriptionResponse>() {
            @Override
            public void onResponse(Call<PrescriptionResponse> call, Response<PrescriptionResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.code() == 401) {
                    handleUnauthorized();
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    PrescriptionResponse body = response.body();
                    bindPrescription(body);
                    Toast.makeText(PrescriptionActivity.this,
                            body.getMessage(), Toast.LENGTH_SHORT).show();
                    if ("finalized".equals(body.getStatus())) {
                        setResult(RESULT_OK);
                        finish();
                    }
                } else {
                    Toast.makeText(PrescriptionActivity.this,
                            "Could not save prescription", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<PrescriptionResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(PrescriptionActivity.this,
                        "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean hasAnyInput() {
        return !TextUtils.isEmpty(textOf(etSymptomsSummary))
                || !TextUtils.isEmpty(textOf(etTestsDone))
                || !TextUtils.isEmpty(textOf(etMedications))
                || !TextUtils.isEmpty(textOf(etConclusion))
                || !TextUtils.isEmpty(textOf(etAdviceNotes));
    }

    private String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String buildMeta(PrescriptionResponse body) {
        StringBuilder meta = new StringBuilder();
        if (!TextUtils.isEmpty(body.getDoctorName())) {
            meta.append(body.getDoctorName());
        }
        if (!TextUtils.isEmpty(body.getDoctorSpecialty())) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(body.getDoctorSpecialty());
        }
        if (!TextUtils.isEmpty(body.getVisitType())) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(formatVisitType(body.getVisitType()));
        }

        String displayDate = !TextUtils.isEmpty(body.getCompletedAt())
                ? body.getCompletedAt() : body.getCreatedAt();
        if (!TextUtils.isEmpty(displayDate)) {
            if (meta.length() > 0) meta.append(" • ");
            meta.append(formatDate(displayDate));
        }
        return meta.toString();
    }

    private String formatVisitType(String visitType) {
        if ("follow_up".equals(visitType)) {
            return getString(R.string.visit_type_follow_up);
        }
        return getString(R.string.visit_type_new);
    }

    private String formatDate(String rawDate) {
        if (TextUtils.isEmpty(rawDate)) {
            return "";
        }
        int separator = rawDate.indexOf('T');
        return separator > 0 ? rawDate.substring(0, separator) : rawDate;
    }

    private void handleUnauthorized() {
        if (!isFinishing()) {
            sessionManager.clearSession();
            ApiClient.setAuthToken(null);
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}

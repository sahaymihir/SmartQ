package com.example.smartqueue.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.smartqueue.R;
import com.example.smartqueue.models.request.SymptomRequest;
import com.example.smartqueue.models.response.ModelEvalHistoryResponse;
import com.example.smartqueue.models.response.SymptomPredictResponse;
import com.example.smartqueue.network.ApiClient;
import com.example.smartqueue.network.ApiService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ModelEvalActivity extends AppCompatActivity {

    private static final String[] SEX_OPTIONS = {"Female", "Male", "Other"};
    private static final String[] CHIEF_COMPLAINT_OPTIONS = {
            "Chest / heart",
            "Breathing",
            "Head / brain",
            "Stomach / abdomen",
            "Skin / rash",
            "Injury / bones",
            "Urine / kidney",
            "Sugar / hormones",
            "Not sure"
    };
    private static final String[] MENTAL_STATUS_OPTIONS = {"Alert", "Drowsy", "Unresponsive"};

    private ApiService apiService;

    private TextInputEditText etTestSymptoms, etTestAge, etTestTemperature, etTestPainScore, etTestSpo2,
            etTestRespiratoryRate, etTestHeartRate, etTestSystolicBp, etTestDiastolicBp,
            etTestGcs, etTestNews2;
    private AutoCompleteTextView etTestChiefComplaint, etTestSex, etTestMentalStatus;
    private MaterialButton btnRunTest, btnBack, btnRefreshHistory;
    private MaterialButton btnExampleCardio, btnExampleFever, btnExampleRash;
    private LinearLayout layoutTestResult, layoutTestScores, layoutEvalHistory, layoutNoHistory;
    private TextView tvTestPrioritySummary, tvTestPriorityBreakdown, tvTestSafetySummary,
            tvTestQueueSummary, tvTestSuggestedTests;
    private TextView tvTestPrimarySpecialist, tvTestRoutedSpecialty, tvTestNormalizedSymptoms;
    private TextView tvTestPatientWait;
    private TextView tvTestFactors, tvTestDoctor, tvTestReasoning, tvTestModelSource, tvHistoryCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_eval);

        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
        setupInputOptions();
        setupClickListeners();
        loadHistory();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btnBack);
        btnRunTest = findViewById(R.id.btnRunTest);
        btnRefreshHistory = findViewById(R.id.btnRefreshHistory);
        btnExampleCardio = findViewById(R.id.btnExampleCardio);
        btnExampleFever = findViewById(R.id.btnExampleFever);
        btnExampleRash = findViewById(R.id.btnExampleRash);
        etTestSymptoms = findViewById(R.id.etTestSymptoms);
        etTestAge = findViewById(R.id.etTestAge);
        etTestChiefComplaint = findViewById(R.id.etTestChiefComplaint);
        etTestSex = findViewById(R.id.etTestSex);
        etTestMentalStatus = findViewById(R.id.etTestMentalStatus);
        etTestTemperature = findViewById(R.id.etTestTemperature);
        etTestPainScore = findViewById(R.id.etTestPainScore);
        etTestSpo2 = findViewById(R.id.etTestSpo2);
        etTestRespiratoryRate = findViewById(R.id.etTestRespiratoryRate);
        etTestHeartRate = findViewById(R.id.etTestHeartRate);
        etTestSystolicBp = findViewById(R.id.etTestSystolicBp);
        etTestDiastolicBp = findViewById(R.id.etTestDiastolicBp);
        etTestGcs = findViewById(R.id.etTestGcs);
        etTestNews2 = findViewById(R.id.etTestNews2);
        layoutTestResult = findViewById(R.id.layoutTestResult);
        layoutTestScores = findViewById(R.id.layoutTestScores);
        layoutEvalHistory = findViewById(R.id.layoutEvalHistory);
        layoutNoHistory = findViewById(R.id.layoutNoHistory);
        tvTestPrioritySummary = findViewById(R.id.tvTestPrioritySummary);
        tvTestPriorityBreakdown = findViewById(R.id.tvTestPriorityBreakdown);
        tvTestSafetySummary = findViewById(R.id.tvTestSafetySummary);
        tvTestQueueSummary = findViewById(R.id.tvTestQueueSummary);
        tvTestSuggestedTests = findViewById(R.id.tvTestSuggestedTests);
        tvTestPrimarySpecialist = findViewById(R.id.tvTestPrimarySpecialist);
        tvTestRoutedSpecialty = findViewById(R.id.tvTestRoutedSpecialty);
        tvTestNormalizedSymptoms = findViewById(R.id.tvTestNormalizedSymptoms);
        tvTestPatientWait = findViewById(R.id.tvTestPatientWait);
        tvTestFactors = findViewById(R.id.tvTestFactors);
        tvTestDoctor = findViewById(R.id.tvTestDoctor);
        tvTestReasoning = findViewById(R.id.tvTestReasoning);
        tvTestModelSource = findViewById(R.id.tvTestModelSource);
        tvHistoryCount = findViewById(R.id.tvHistoryCount);
    }

    private void setupInputOptions() {
        attachDropdown(etTestSex, SEX_OPTIONS);
        attachDropdown(etTestChiefComplaint, CHIEF_COMPLAINT_OPTIONS);
        attachDropdown(etTestMentalStatus, MENTAL_STATUS_OPTIONS);
    }

    private void attachDropdown(AutoCompleteTextView field, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                values
        );
        field.setAdapter(adapter);
        field.setThreshold(0);
        field.setOnClickListener(v -> field.showDropDown());
        field.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                field.showDropDown();
            }
        });
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        btnRefreshHistory.setOnClickListener(v -> loadHistory());

        btnExampleCardio.setOnClickListener(v ->
                applyExample(new EvalScenario(
                        "I feel pressure in my chest and I am short of breath",
                        67,
                        "Male",
                        "Alert",
                        "Chest / heart",
                        38.7,
                        8.0,
                        91.0,
                        26.0,
                        118.0,
                        92.0,
                        58.0,
                        15,
                        7.0
                )));

        btnExampleFever.setOnClickListener(v ->
                applyExample(new EvalScenario(
                        "I have fever and cold for two days with body ache",
                        29,
                        "Female",
                        "Alert",
                        "Breathing",
                        39.1,
                        4.0,
                        92.0,
                        24.0,
                        110.0,
                        104.0,
                        68.0,
                        15,
                        5.0
                )));

        btnExampleRash.setOnClickListener(v ->
                applyExample(new EvalScenario(
                        "I have an itchy rash on my arms and neck",
                        34,
                        "Female",
                        "Alert",
                        "Skin / rash",
                        36.8,
                        2.0,
                        98.0,
                        16.0,
                        82.0,
                        118.0,
                        76.0,
                        15,
                        1.0
                )));

        btnRunTest.setOnClickListener(v -> {
            String symptoms = etTestSymptoms.getText() != null
                    ? etTestSymptoms.getText().toString().trim() : "";
            if (TextUtils.isEmpty(symptoms)) {
                Toast.makeText(this, "Please enter symptoms to test", Toast.LENGTH_SHORT).show();
                return;
            }

            SymptomRequest request = buildAdminEvalRequest();
            if (request == null) {
                return;
            }

            runTestPrediction(request);
        });
    }

    private void applyExample(EvalScenario scenario) {
        etTestSymptoms.setText(scenario.symptoms);
        etTestSymptoms.setSelection(scenario.symptoms.length());
        etTestAge.setText(String.valueOf(scenario.age));
        etTestSex.setText(scenario.sex, false);
        etTestMentalStatus.setText(scenario.mentalStatus, false);
        etTestChiefComplaint.setText(scenario.chiefComplaint, false);
        etTestTemperature.setText(formatOptionalDouble(scenario.temperatureC));
        etTestPainScore.setText(formatOptionalDouble(scenario.painScore));
        etTestSpo2.setText(formatOptionalDouble(scenario.spo2));
        etTestRespiratoryRate.setText(formatOptionalDouble(scenario.respiratoryRate));
        etTestHeartRate.setText(formatOptionalDouble(scenario.heartRate));
        etTestSystolicBp.setText(formatOptionalDouble(scenario.systolicBp));
        etTestDiastolicBp.setText(formatOptionalDouble(scenario.diastolicBp));
        etTestGcs.setText(scenario.gcsTotal != null ? String.valueOf(scenario.gcsTotal) : "");
        etTestNews2.setText(formatOptionalDouble(scenario.news2Score));

        SymptomRequest request = buildAdminEvalRequest();
        if (request != null) {
            runTestPrediction(request);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private SymptomRequest buildAdminEvalRequest() {
        String symptoms = getTextValue(etTestSymptoms);
        if (TextUtils.isEmpty(symptoms)) {
            etTestSymptoms.setError("Required");
            etTestSymptoms.requestFocus();
            Toast.makeText(this, "Please enter symptoms to test", Toast.LENGTH_SHORT).show();
            return null;
        }

        Integer age = parseRequiredInteger(etTestAge, "Age", 0, 130);
        if (age == null) {
            return null;
        }

        Integer gcsTotal = parseOptionalInteger(etTestGcs, "consciousness check score", 0, 15);
        if (gcsTotal == null && !TextUtils.isEmpty(getTextValue(etTestGcs))) {
            return null;
        }

        Double temperature = parseOptionalDouble(etTestTemperature, "body temperature", 0, 50);
        if (temperature == null && !TextUtils.isEmpty(getTextValue(etTestTemperature))) {
            return null;
        }

        Double painScore = parseOptionalDouble(etTestPainScore, "pain level", 0, 10);
        if (painScore == null && !TextUtils.isEmpty(getTextValue(etTestPainScore))) {
            return null;
        }

        Double spo2 = parseOptionalDouble(etTestSpo2, "oxygen level", 0, 100);
        if (spo2 == null && !TextUtils.isEmpty(getTextValue(etTestSpo2))) {
            return null;
        }

        Double respiratoryRate = parseOptionalDouble(etTestRespiratoryRate, "breaths per minute", 0, 80);
        if (respiratoryRate == null && !TextUtils.isEmpty(getTextValue(etTestRespiratoryRate))) {
            return null;
        }

        Double heartRate = parseOptionalDouble(etTestHeartRate, "pulse / heart rate", 0, 250);
        if (heartRate == null && !TextUtils.isEmpty(getTextValue(etTestHeartRate))) {
            return null;
        }

        Double systolicBp = parseOptionalDouble(etTestSystolicBp, "upper blood pressure number", 0, 300);
        if (systolicBp == null && !TextUtils.isEmpty(getTextValue(etTestSystolicBp))) {
            return null;
        }

        Double diastolicBp = parseOptionalDouble(etTestDiastolicBp, "lower blood pressure number", 0, 200);
        if (diastolicBp == null && !TextUtils.isEmpty(getTextValue(etTestDiastolicBp))) {
            return null;
        }

        Double news2 = parseOptionalDouble(etTestNews2, "nurse urgency score", 0, 25);
        if (news2 == null && !TextUtils.isEmpty(getTextValue(etTestNews2))) {
            return null;
        }

        return new SymptomRequest(symptoms, age)
                .setChiefComplaintSystem(normalizeChiefComplaintSelection(getTextValue(etTestChiefComplaint)))
                .setSex(normalizeSexSelection(getTextValue(etTestSex)))
                .setMentalStatusTriage(normalizeMentalStatusSelection(getTextValue(etTestMentalStatus)))
                .setTemperatureC(temperature)
                .setPainScore(painScore)
                .setSpo2(spo2)
                .setRespiratoryRate(respiratoryRate)
                .setHeartRate(heartRate)
                .setSystolicBp(systolicBp)
                .setDiastolicBp(diastolicBp)
                .setGcsTotal(gcsTotal)
                .setNews2Score(news2);
    }

    private String getTextValue(TextView field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }

    private String emptyToNull(String value) {
        return TextUtils.isEmpty(value) ? null : value;
    }

    private Integer parseRequiredInteger(TextView field, String label, int min, int max) {
        String value = getTextValue(field);
        if (TextUtils.isEmpty(value)) {
            field.setError("Required");
            field.requestFocus();
            Toast.makeText(this, label + " is required", Toast.LENGTH_SHORT).show();
            return null;
        }
        return parseOptionalInteger(field, label, min, max);
    }

    private Integer parseOptionalInteger(TextView field, String label, int min, int max) {
        String value = getTextValue(field);
        if (TextUtils.isEmpty(value)) {
            field.setError(null);
            return null;
        }

        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                field.setError(label + " must be between " + min + " and " + max);
                field.requestFocus();
                Toast.makeText(this, label + " must be between " + min + " and " + max, Toast.LENGTH_SHORT).show();
                return null;
            }
            field.setError(null);
            return parsed;
        } catch (NumberFormatException e) {
            field.setError("Invalid " + label);
            field.requestFocus();
            Toast.makeText(this, "Invalid " + label, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private Double parseOptionalDouble(TextView field, String label, double min, double max) {
        String value = getTextValue(field);
        if (TextUtils.isEmpty(value)) {
            field.setError(null);
            return null;
        }

        try {
            double parsed = Double.parseDouble(value);
            if (parsed < min || parsed > max) {
                field.setError(label + " must be between " + formatScore(min) + " and " + formatScore(max));
                field.requestFocus();
                Toast.makeText(
                        this,
                        label + " must be between " + formatScore(min) + " and " + formatScore(max),
                        Toast.LENGTH_SHORT
                ).show();
                return null;
            }
            field.setError(null);
            return parsed;
        } catch (NumberFormatException e) {
            field.setError("Invalid " + label);
            field.requestFocus();
            Toast.makeText(this, "Invalid " + label, Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    private String formatOptionalDouble(Double value) {
        return value == null ? "" : formatScore(value);
    }

    private String normalizeSexSelection(String value) {
        if (TextUtils.isEmpty(value)) return null;

        String lower = value.toLowerCase(Locale.getDefault());
        if (lower.startsWith("f")) return "F";
        if (lower.startsWith("m")) return "M";
        return "Other";
    }

    private String normalizeMentalStatusSelection(String value) {
        if (TextUtils.isEmpty(value)) return null;

        String lower = value.toLowerCase(Locale.getDefault());
        if (lower.startsWith("alert")) return "alert";
        if (lower.startsWith("drows")) return "drowsy";
        if (lower.startsWith("unresponsive")) return "unresponsive";
        return value.toLowerCase(Locale.getDefault());
    }

    private String normalizeChiefComplaintSelection(String value) {
        if (TextUtils.isEmpty(value)) return null;

        switch (value) {
            case "Chest / heart":
                return "cardiac";
            case "Breathing":
                return "respiratory";
            case "Head / brain":
                return "neurological";
            case "Stomach / abdomen":
                return "gastrointestinal";
            case "Skin / rash":
                return "dermatological";
            case "Injury / bones":
                return "trauma";
            case "Urine / kidney":
                return "renal";
            case "Sugar / hormones":
                return "endocrine";
            case "Not sure":
                return "other";
            default:
                return value.toLowerCase(Locale.getDefault());
        }
    }

    private void runTestPrediction(SymptomRequest request) {
        btnRunTest.setEnabled(false);
        btnRunTest.setText("Running...");

        apiService.runAdminModelEval(request)
                .enqueue(new Callback<SymptomPredictResponse>() {
                    @Override
                    public void onResponse(Call<SymptomPredictResponse> call,
                                           Response<SymptomPredictResponse> response) {
                        btnRunTest.setEnabled(true);
                        btnRunTest.setText("Run Prediction");

                        if (response.isSuccessful() && response.body() != null
                                && response.body().isSuccess()) {
                            SymptomPredictResponse body = response.body();
                            showTestResult(body);
                            loadHistory();
                        } else {
                            Toast.makeText(ModelEvalActivity.this,
                                    "Prediction failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<SymptomPredictResponse> call, Throwable t) {
                        btnRunTest.setEnabled(true);
                        btnRunTest.setText("Run Prediction");
                        Toast.makeText(ModelEvalActivity.this,
                                "Network error", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showTestResult(SymptomPredictResponse body) {
        // ── 🟦 PATIENT SUMMARY ──────────────────────────────────────────
        tvTestNormalizedSymptoms.setText(!TextUtils.isEmpty(body.getNormalizedSymptoms())
                ? body.getNormalizedSymptoms() : "—");
        tvTestPatientWait.setText(buildPatientWaitText(
                body.getQueueSelectedRoute(),
                body.getQueueAvgWaitMinutes()
        ));

        // ── 🟩 NURSE / TRIAGE ───────────────────────────────────────────
        tvTestPrioritySummary.setText(buildPrioritySummary(body));
        tvTestPriorityBreakdown.setText(buildPriorityBreakdown(
                body.getPriorityComponents(),
                body.getModelPriorityClass(),
                body.getGuardrailedPriorityClass(),
                body.getDerivedChiefComplaintSystem(),
                body.getTriageSource(),
                body.getTriageRecommendation(),
                body.getPriorityDecisionTrace()
        ));
        tvTestSafetySummary.setText(buildPredictionSafetySummary(body.getSafetyMatches()));
        tvTestQueueSummary.setText(buildQueueSummary(
                body.getQueueSelectedRoute(),
                body.getQueueRouteType(),
                body.getQueueCurrentLength(),
                body.getQueueAvailableDoctors(),
                body.getQueueAvgWaitMinutes(),
                body.getQueueRationale()
        ));

        // ── 🟥 DOCTOR CLINICAL ──────────────────────────────────────────
        int confidencePct = (int) Math.round(body.getConfidence() * 100);
        String primary = !TextUtils.isEmpty(body.getPrimarySpecialist())
                ? body.getPrimarySpecialist() : "General Practice";
        if (body.isLowConfidence()) {
            primary += "  •  " + confidencePct + "% route confidence  •  Low confidence";
        } else {
            primary += "  •  " + confidencePct + "% route confidence";
        }
        tvTestPrimarySpecialist.setText(primary);

        String routed = !TextUtils.isEmpty(body.getRoutedSpecialty())
                ? body.getRoutedSpecialty() : "General OPD";
        if (body.isLowConfidence()) {
            routed += "  •  manual review recommended";
        }
        tvTestRoutedSpecialty.setText(routed);

        List<String> factors = body.getExtractedFactors();
        tvTestFactors.setText(factors != null && !factors.isEmpty()
                ? TextUtils.join(", ", factors)
                : "No strong signals detected");

        renderPredictionScores(body.getSpecialtyScores(), layoutTestScores);

        SymptomPredictResponse.Doctor doc = body.getRecommendedDoctor();
        tvTestDoctor.setText(doc != null
                ? doc.getName() + " (" + doc.getSpecialty() + ")"
                : "—");
        tvTestSuggestedTests.setText(buildPredictionTestSummary(
                body.getTestRecommendations(),
                body.getTestSource(),
                body.isTestLowConfidence()
        ));
        tvTestReasoning.setText(body.getReasoning() != null ? body.getReasoning() : "—");
        tvTestModelSource.setText("Sources: " + buildModelSourceText(
                body.getFlowSource(),
                body.getModelSource(),
                body.getTriageSource(),
                body.getTestSource()
        ));

        layoutTestResult.setVisibility(View.VISIBLE);
    }

    private void loadHistory() {
        apiService.getModelEvalHistory().enqueue(new Callback<ModelEvalHistoryResponse>() {
            @Override
            public void onResponse(Call<ModelEvalHistoryResponse> call,
                                   Response<ModelEvalHistoryResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess()) {
                    renderHistory(response.body().getHistory());
                }
            }

            @Override
            public void onFailure(Call<ModelEvalHistoryResponse> call, Throwable t) {
                Toast.makeText(ModelEvalActivity.this,
                        "Could not load history", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderHistory(List<ModelEvalHistoryResponse.EvalEntry> history) {
        layoutEvalHistory.removeAllViews();

        if (history == null || history.isEmpty()) {
            layoutNoHistory.setVisibility(View.VISIBLE);
            tvHistoryCount.setText("0 records");
            return;
        }

        layoutNoHistory.setVisibility(View.GONE);
        tvHistoryCount.setText(history.size() + " record" + (history.size() == 1 ? "" : "s"));

        LayoutInflater inflater = LayoutInflater.from(this);

        for (ModelEvalHistoryResponse.EvalEntry entry : history) {
            View card = inflater.inflate(R.layout.item_eval_card, layoutEvalHistory, false);

            TextView tvPatient = card.findViewById(R.id.tvEvalPatientName);
            TextView tvTime = card.findViewById(R.id.tvEvalTime);
            TextView tvSource = card.findViewById(R.id.tvEvalSource);
            TextView tvConf = card.findViewById(R.id.tvEvalConfidence);
            TextView tvSymptoms = card.findViewById(R.id.tvEvalSymptoms);
            TextView tvInputSnapshot = card.findViewById(R.id.tvEvalInputSnapshot);
            TextView tvNormalizedSymptoms = card.findViewById(R.id.tvEvalNormalizedSymptoms);
            TextView tvFactors = card.findViewById(R.id.tvEvalFactors);
            TextView tvPrioritySummary = card.findViewById(R.id.tvEvalPrioritySummary);
            TextView tvPriorityBreakdown = card.findViewById(R.id.tvEvalPriorityBreakdown);
            TextView tvSafetySummary = card.findViewById(R.id.tvEvalSafetySummary);
            TextView tvRouteSummary = card.findViewById(R.id.tvEvalRouteSummary);
            TextView tvQueueSummary = card.findViewById(R.id.tvEvalQueueSummary);
            LinearLayout lScores = card.findViewById(R.id.layoutEvalScores);
            TextView tvDoctor = card.findViewById(R.id.tvEvalDoctor);
            TextView tvSuggestedTests = card.findViewById(R.id.tvEvalSuggestedTests);
            TextView tvReasoning = card.findViewById(R.id.tvEvalReasoning);

            String patientLabel = !TextUtils.isEmpty(entry.getPatientName())
                    ? entry.getPatientName() : "Unknown";
            if (entry.getAge() != null) {
                patientLabel += "  •  Age " + entry.getAge();
            }
            tvPatient.setText(patientLabel);
            tvTime.setText(formatTimestamp(entry.getTimestamp()));
            tvSource.setText(buildModelSourceText(
                    entry.getFlowSource(),
                    entry.getModelSource(),
                    entry.getTriageSource(),
                    entry.getTestSource()
            ));

            int confPct = (int) Math.round(entry.getConfidence() * 100);
            tvConf.setText(entry.isLowConfidence()
                    ? "Low conf  " + confPct + "%"
                    : confPct + "% route");
            tvConf.setBackgroundResource(entry.isLowConfidence()
                    ? R.drawable.badge_medium
                    : R.drawable.badge_normal);

            tvSymptoms.setText(!TextUtils.isEmpty(entry.getSymptoms()) ? entry.getSymptoms() : "—");
            tvInputSnapshot.setText(buildInputSnapshot(entry));
            tvNormalizedSymptoms.setText(!TextUtils.isEmpty(entry.getNormalizedSymptoms())
                    ? entry.getNormalizedSymptoms() : "—");

            List<String> factors = entry.getExtractedFactors();
            tvFactors.setText(factors != null && !factors.isEmpty()
                    ? TextUtils.join(", ", factors)
                    : "No strong signals detected");

            tvPrioritySummary.setText(buildPrioritySummary(entry));
            tvPriorityBreakdown.setText(buildPriorityBreakdown(
                    entry.getPriorityComponents(),
                    entry.getModelPriorityClass(),
                    entry.getGuardrailedPriorityClass(),
                    entry.getDerivedChiefComplaintSystem(),
                    entry.getTriageSource(),
                    entry.getTriageRecommendation(),
                    entry.getPriorityDecisionTrace()
            ));
            tvSafetySummary.setText(buildHistorySafetySummary(entry.getSafetyMatches()));

            String routeSummary = "Clinical fit: "
                    + (!TextUtils.isEmpty(entry.getPrimarySpecialist()) ? entry.getPrimarySpecialist() : "General Practice")
                    + "  •  SmartQ route: "
                    + (!TextUtils.isEmpty(entry.getRoutedSpecialty()) ? entry.getRoutedSpecialty() : "General OPD");
            if (entry.isLowConfidence()) {
                routeSummary += "  •  manual review recommended";
            }
            tvRouteSummary.setText(routeSummary);
            tvQueueSummary.setText(buildQueueSummary(
                    entry.getQueueSelectedRoute(),
                    entry.getQueueRouteType(),
                    entry.getQueueCurrentLength(),
                    entry.getQueueAvailableDoctors(),
                    entry.getQueueAvgWaitMinutes(),
                    entry.getQueueRationale()
            ));

            renderHistoryScores(entry.getSpecialtyScores(), lScores);

            ModelEvalHistoryResponse.RecommendedDoctor doc = entry.getRecommendedDoctor();
            tvDoctor.setText(doc != null
                    ? doc.getName() + " (" + doc.getSpecialty() + ")"
                    : "—");
            tvSuggestedTests.setText(buildHistoryTestSummary(
                    entry.getTestRecommendations(),
                    entry.getTestSource(),
                    entry.isTestLowConfidence()
            ));
            tvReasoning.setText(entry.getReasoning() != null ? entry.getReasoning() : "—");

            layoutEvalHistory.addView(card);
        }
    }

    private void renderPredictionScores(List<SymptomPredictResponse.SpecialtyScore> scores, LinearLayout container) {
        container.removeAllViews();

        if (scores == null || scores.isEmpty()) {
            addScoreRow(container, "No strong specialty signals detected", null, false, true);
            return;
        }

        int shown = 0;
        for (SymptomPredictResponse.SpecialtyScore score : scores) {
            if (score.getScore() <= 0 || shown >= 4) continue;
            addScoreRow(
                    container,
                    score.getSpecialty(),
                    buildScoreMeta(score.getScore(), score.getRoutedSpecialty(), score.getMatchedKeywords()),
                    false,
                    false
            );
            shown++;
        }

        if (shown == 0) {
            addScoreRow(container, "No strong specialty signals detected", null, false, true);
        }
    }

    private void renderHistoryScores(List<ModelEvalHistoryResponse.SpecialtyScore> scores, LinearLayout container) {
        container.removeAllViews();

        if (scores == null || scores.isEmpty()) {
            addScoreRow(container, "No strong specialty signals detected", null, true, true);
            return;
        }

        int shown = 0;
        for (ModelEvalHistoryResponse.SpecialtyScore score : scores) {
            if (score.getScore() <= 0 || shown >= 4) continue;
            addScoreRow(
                    container,
                    score.getSpecialty(),
                    buildScoreMeta(score.getScore(), score.getRoutedSpecialty(), score.getMatchedKeywords()),
                    true,
                    false
            );
            shown++;
        }

        if (shown == 0) {
            addScoreRow(container, "No strong specialty signals detected", null, true, true);
        }
    }

    private void addScoreRow(LinearLayout container, String title, String meta, boolean compact, boolean muted) {
        TextView tv = new TextView(this);
        StringBuilder text = new StringBuilder("• ").append(title);
        if (!TextUtils.isEmpty(meta)) {
            text.append("  ").append(meta);
        }
        tv.setText(text.toString());
        tv.setTextSize(compact ? 12f : 13f);
        tv.setTextColor(getResources().getColor(muted ? R.color.text_secondary : R.color.text_primary));
        tv.setPadding(0, compact ? 3 : 4, 0, compact ? 3 : 4);
        container.addView(tv);
    }

    private String buildScoreMeta(double score, String routedSpecialty, List<String> matchedSignals) {
        int pct = (int) Math.round(score * 100);
        StringBuilder meta = new StringBuilder(pct + "%");
        if (!TextUtils.isEmpty(routedSpecialty)) {
            meta.append(" → ").append(routedSpecialty);
        }
        if (matchedSignals != null && !matchedSignals.isEmpty()) {
            meta.append(" ← ").append(TextUtils.join(", ", matchedSignals));
        }
        return meta.toString();
    }

    private String formatTimestamp(String iso) {
        if (iso == null) return "—";
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            input.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = input.parse(iso);
            SimpleDateFormat output = new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault());
            return output.format(date);
        } catch (Exception e) {
            return iso;
        }
    }

    private String buildModelSourceText(String flowSource, String specialtySource, String triageSource, String testSource) {
        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(flowSource)) {
            parts.add(flowSource);
        }
        if (!TextUtils.isEmpty(specialtySource)) {
            parts.add(specialtySource);
        }
        if (!TextUtils.isEmpty(triageSource)) {
            parts.add(triageSource);
        }
        if (!TextUtils.isEmpty(testSource)) {
            parts.add(testSource);
        }
        if (parts.isEmpty()) {
            return "patient_flow_v1";
        }
        return TextUtils.join("  •  ", parts);
    }

    private String buildPrioritySummary(SymptomPredictResponse body) {
        if (body == null || (TextUtils.isEmpty(body.getPriorityLabel()) && body.getTriagePriorityClass() == null)) {
            return "Priority model data unavailable";
        }

        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(body.getPriorityLabel())) {
            parts.add(capitalize(body.getPriorityLabel()) + " priority");
        }
        if (body.getModelPriorityClass() != null) {
            parts.add("raw KTAS " + body.getModelPriorityClass());
        }
        if (body.getTriagePriorityClass() != null) {
            parts.add("final KTAS " + body.getTriagePriorityClass());
        }
        if (body.getPriorityFinalScore() != null) {
            parts.add("score " + formatScore(body.getPriorityFinalScore()));
        }
        if (body.getTriageConfidence() > 0) {
            parts.add(Math.round(body.getTriageConfidence() * 100) + "% ML confidence");
        }
        if (body.isTriageLowConfidence()) {
            parts.add("low confidence");
        }
        return TextUtils.join("  •  ", parts);
    }

    private String buildPrioritySummary(ModelEvalHistoryResponse.EvalEntry entry) {
        if (entry == null || (TextUtils.isEmpty(entry.getPriorityLabel()) && entry.getTriagePriorityClass() == null)) {
            return "Priority model data not recorded";
        }

        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(entry.getPriorityLabel())) {
            parts.add(capitalize(entry.getPriorityLabel()) + " priority");
        }
        if (entry.getModelPriorityClass() != null) {
            parts.add("raw KTAS " + entry.getModelPriorityClass());
        }
        if (entry.getTriagePriorityClass() != null) {
            parts.add("final KTAS " + entry.getTriagePriorityClass());
        }
        if (entry.getPriorityFinalScore() != null) {
            parts.add("score " + formatScore(entry.getPriorityFinalScore()));
        }
        if (entry.getTriageConfidence() > 0) {
            parts.add(Math.round(entry.getTriageConfidence() * 100) + "% ML confidence");
        }
        if (entry.isTriageLowConfidence()) {
            parts.add("low confidence");
        }
        return TextUtils.join("  •  ", parts);
    }

    private String buildPriorityBreakdown(
            SymptomPredictResponse.PriorityComponents components,
            Integer modelPriorityClass,
            Integer finalPriorityClass,
            String derivedComplaint,
            String triageSource,
            String triageRecommendation,
            String decisionTrace
    ) {
        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(derivedComplaint)) {
            parts.add("complaint " + derivedComplaint);
        }
        if (components != null) {
            parts.add("age " + formatScore(components.getAge()));
            parts.add("triage " + formatScore(components.getTriage()));
            if (components.getClinicianOverride() > 0) {
                parts.add("safety override +" + formatScore(components.getClinicianOverride()));
            }
        }
        if (modelPriorityClass != null && finalPriorityClass != null && !modelPriorityClass.equals(finalPriorityClass)) {
            parts.add("raw " + modelPriorityClass + " → final " + finalPriorityClass);
        }
        if (!TextUtils.isEmpty(triageSource)) {
            parts.add(triageSource);
        }
        if (!TextUtils.isEmpty(triageRecommendation)) {
            parts.add(triageRecommendation);
        }
        if (parts.isEmpty() && !TextUtils.isEmpty(decisionTrace)) {
            return decisionTrace;
        }
        return TextUtils.join("  •  ", parts);
    }

    private String buildPriorityBreakdown(
            ModelEvalHistoryResponse.PriorityComponents components,
            Integer modelPriorityClass,
            Integer finalPriorityClass,
            String derivedComplaint,
            String triageSource,
            String triageRecommendation,
            String decisionTrace
    ) {
        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(derivedComplaint)) {
            parts.add("complaint " + derivedComplaint);
        }
        if (components != null) {
            parts.add("age " + formatScore(components.getAge()));
            parts.add("triage " + formatScore(components.getTriage()));
            if (components.getClinicianOverride() > 0) {
                parts.add("safety override +" + formatScore(components.getClinicianOverride()));
            }
        }
        if (modelPriorityClass != null && finalPriorityClass != null && !modelPriorityClass.equals(finalPriorityClass)) {
            parts.add("raw " + modelPriorityClass + " → final " + finalPriorityClass);
        }
        if (!TextUtils.isEmpty(triageSource)) {
            parts.add(triageSource);
        }
        if (!TextUtils.isEmpty(triageRecommendation)) {
            parts.add(triageRecommendation);
        }
        if (parts.isEmpty() && !TextUtils.isEmpty(decisionTrace)) {
            return decisionTrace;
        }
        return TextUtils.join("  •  ", parts);
    }

    private String buildInputSnapshot(ModelEvalHistoryResponse.EvalEntry entry) {
        ArrayList<String> parts = new ArrayList<>();
        if (entry.getAge() != null) parts.add("Age " + entry.getAge());
        if (!TextUtils.isEmpty(entry.getSex())) parts.add("Sex " + toReadableSex(entry.getSex()));
        if (!TextUtils.isEmpty(entry.getChiefComplaintSystem())) {
            parts.add("Problem area " + toReadableComplaint(entry.getChiefComplaintSystem()));
        }
        if (!TextUtils.isEmpty(entry.getMentalStatusTriage())) {
            parts.add("Alertness " + toReadableMentalStatus(entry.getMentalStatusTriage()));
        }
        if (entry.getTemperatureC() != null) parts.add("Temperature " + formatScore(entry.getTemperatureC()) + "°C");
        if (entry.getPainScore() != null) parts.add("Pain " + formatScore(entry.getPainScore()) + "/10");
        if (entry.getSpo2() != null) parts.add("Oxygen " + formatScore(entry.getSpo2()) + "%");
        if (entry.getRespiratoryRate() != null) parts.add("Breathing " + formatScore(entry.getRespiratoryRate()) + "/min");
        if (entry.getHeartRate() != null) parts.add("Pulse " + formatScore(entry.getHeartRate()) + "/min");
        if (entry.getSystolicBp() != null || entry.getDiastolicBp() != null) {
            parts.add("Blood pressure "
                    + (entry.getSystolicBp() != null ? formatScore(entry.getSystolicBp()) : "—")
                    + "/"
                    + (entry.getDiastolicBp() != null ? formatScore(entry.getDiastolicBp()) : "—"));
        }
        if (entry.getGcsTotal() != null) parts.add("Consciousness check " + entry.getGcsTotal());
        if (entry.getNews2Score() != null) parts.add("Urgency score " + formatScore(entry.getNews2Score()));

        return parts.isEmpty() ? "Only symptoms and age were provided" : TextUtils.join("  •  ", parts);
    }

    private String toReadableSex(String value) {
        if (TextUtils.isEmpty(value)) return value;

        String lower = value.toLowerCase(Locale.getDefault());
        if (lower.equals("f") || lower.equals("female")) return "Female";
        if (lower.equals("m") || lower.equals("male")) return "Male";
        return capitalize(value);
    }

    private String toReadableMentalStatus(String value) {
        if (TextUtils.isEmpty(value)) return value;

        String lower = value.toLowerCase(Locale.getDefault());
        if (lower.equals("alert")) return "Alert";
        if (lower.equals("drowsy")) return "Drowsy";
        if (lower.equals("unresponsive")) return "Unresponsive";
        return capitalize(value);
    }

    private String toReadableComplaint(String value) {
        if (TextUtils.isEmpty(value)) return value;

        switch (value.toLowerCase(Locale.getDefault())) {
            case "cardiac":
                return "Chest / heart";
            case "respiratory":
                return "Breathing";
            case "neurological":
                return "Head / brain";
            case "gastrointestinal":
                return "Stomach / abdomen";
            case "dermatological":
                return "Skin / rash";
            case "trauma":
                return "Injury / bones";
            case "renal":
                return "Urine / kidney";
            case "endocrine":
                return "Sugar / hormones";
            case "other":
                return "Not sure";
            default:
                return capitalize(value.replace('_', ' '));
        }
    }

    private String buildPredictionSafetySummary(List<SymptomPredictResponse.SafetyMatch> safetyMatches) {
        if (safetyMatches == null || safetyMatches.isEmpty()) {
            return "No hard safety override fired";
        }

        ArrayList<String> parts = new ArrayList<>();
        for (SymptomPredictResponse.SafetyMatch match : safetyMatches) {
            StringBuilder item = new StringBuilder(capitalize(match.getSeverity()));
            if (!TextUtils.isEmpty(match.getRuleId())) {
                item.append(": ").append(match.getRuleId().replace('_', ' '));
            }
            if (match.getForcedPriorityClass() != null) {
                item.append(" → KTAS ").append(match.getForcedPriorityClass());
            }
            if (!TextUtils.isEmpty(match.getPreferredRoute())) {
                item.append(" → ").append(match.getPreferredRoute());
            }
            parts.add(item.toString());
        }
        return TextUtils.join("\n", parts);
    }

    private String buildHistorySafetySummary(List<ModelEvalHistoryResponse.SafetyMatch> safetyMatches) {
        if (safetyMatches == null || safetyMatches.isEmpty()) {
            return "No hard safety override fired";
        }

        ArrayList<String> parts = new ArrayList<>();
        for (ModelEvalHistoryResponse.SafetyMatch match : safetyMatches) {
            StringBuilder item = new StringBuilder(capitalize(match.getSeverity()));
            if (!TextUtils.isEmpty(match.getRuleId())) {
                item.append(": ").append(match.getRuleId().replace('_', ' '));
            }
            if (match.getForcedPriorityClass() != null) {
                item.append(" → KTAS ").append(match.getForcedPriorityClass());
            }
            if (!TextUtils.isEmpty(match.getPreferredRoute())) {
                item.append(" → ").append(match.getPreferredRoute());
            }
            parts.add(item.toString());
        }
        return TextUtils.join("\n", parts);
    }

    private String buildQueueSummary(
            String selectedRoute,
            String routeType,
            Integer queueLength,
            Integer availableDoctors,
            Double avgWaitMinutes,
            String rationale
    ) {
        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(selectedRoute)) {
            parts.add(selectedRoute);
        }
        if (!TextUtils.isEmpty(routeType)) {
            parts.add(routeType.replace('_', ' '));
        }
        if (queueLength != null) {
            parts.add("queue " + queueLength);
        }
        if (availableDoctors != null) {
            parts.add("doctors " + availableDoctors);
        }
        if (avgWaitMinutes != null) {
            parts.add("wait " + formatScore(avgWaitMinutes) + "m");
        }

        String summary = parts.isEmpty() ? "Queue routing unavailable" : TextUtils.join("  •  ", parts);
        if (!TextUtils.isEmpty(rationale)) {
            summary += "\n" + rationale;
        }
        return summary;
    }

    private String buildPredictionTestSummary(List<SymptomPredictResponse.TestRecommendation> recommendations,
                                              String testSource,
                                              boolean lowConfidence) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "No test suggestions returned";
        }

        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < recommendations.size() && i < 5; i++) {
            SymptomPredictResponse.TestRecommendation recommendation = recommendations.get(i);
            String line = recommendation.getUrgency() + " • " + recommendation.getTest();
            if (!TextUtils.isEmpty(recommendation.getRationale())) {
                line += " — " + recommendation.getRationale();
            }
            lines.add(line);
        }
        if (!TextUtils.isEmpty(testSource)) {
            lines.add("source " + testSource + (lowConfidence ? " • low confidence" : ""));
        }
        return TextUtils.join("\n", lines);
    }

    private String buildHistoryTestSummary(List<ModelEvalHistoryResponse.TestRecommendation> recommendations,
                                           String testSource,
                                           boolean lowConfidence) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "No test suggestions returned";
        }

        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < recommendations.size() && i < 5; i++) {
            ModelEvalHistoryResponse.TestRecommendation recommendation = recommendations.get(i);
            String line = recommendation.getUrgency() + " • " + recommendation.getTest();
            if (!TextUtils.isEmpty(recommendation.getRationale())) {
                line += " — " + recommendation.getRationale();
            }
            lines.add(line);
        }
        if (!TextUtils.isEmpty(testSource)) {
            lines.add("source " + testSource + (lowConfidence ? " • low confidence" : ""));
        }
        return TextUtils.join("\n", lines);
    }

    private String buildPatientWaitText(String route, Double avgWaitMinutes) {
        if (avgWaitMinutes == null) {
            return "Wait time not yet available";
        }
        long mins = Math.round(avgWaitMinutes);
        String dept = TextUtils.isEmpty(route) ? "the assigned department" : route;
        if (mins == 0) {
            return "Seen immediately at " + dept;
        }
        return "Seen within ~" + mins + " minute" + (mins == 1 ? "" : "s") + " at " + dept;
    }

    private String formatScore(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05d) {
            return String.format(Locale.getDefault(), "%.0f", value);
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    private String capitalize(String value) {
        if (TextUtils.isEmpty(value)) return "";
        return value.substring(0, 1).toUpperCase(Locale.getDefault()) + value.substring(1);
    }

    private static class EvalScenario {
        final String symptoms;
        final int age;
        final String sex;
        final String mentalStatus;
        final String chiefComplaint;
        final Double temperatureC;
        final Double painScore;
        final Double spo2;
        final Double respiratoryRate;
        final Double heartRate;
        final Double systolicBp;
        final Double diastolicBp;
        final Integer gcsTotal;
        final Double news2Score;

        EvalScenario(
                String symptoms,
                int age,
                String sex,
                String mentalStatus,
                String chiefComplaint,
                Double temperatureC,
                Double painScore,
                Double spo2,
                Double respiratoryRate,
                Double heartRate,
                Double systolicBp,
                Double diastolicBp,
                Integer gcsTotal,
                Double news2Score
        ) {
            this.symptoms = symptoms;
            this.age = age;
            this.sex = sex;
            this.mentalStatus = mentalStatus;
            this.chiefComplaint = chiefComplaint;
            this.temperatureC = temperatureC;
            this.painScore = painScore;
            this.spo2 = spo2;
            this.respiratoryRate = respiratoryRate;
            this.heartRate = heartRate;
            this.systolicBp = systolicBp;
            this.diastolicBp = diastolicBp;
            this.gcsTotal = gcsTotal;
            this.news2Score = news2Score;
        }
    }
}

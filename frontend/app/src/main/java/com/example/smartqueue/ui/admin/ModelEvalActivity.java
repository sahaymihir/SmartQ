package com.example.smartqueue.ui.admin;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
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

    private ApiService apiService;

    private TextInputEditText etTestSymptoms, etTestAge;
    private MaterialButton btnRunTest, btnBack, btnRefreshHistory;
    private MaterialButton btnExampleCardio, btnExampleFever, btnExampleRash;
    private LinearLayout layoutTestResult, layoutTestScores, layoutEvalHistory, layoutNoHistory;
    private TextView tvTestPrioritySummary, tvTestPriorityBreakdown;
    private TextView tvTestPrimarySpecialist, tvTestRoutedSpecialty, tvTestNormalizedSymptoms;
    private TextView tvTestFactors, tvTestDoctor, tvTestReasoning, tvTestModelSource, tvHistoryCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_eval);

        apiService = ApiClient.getInstance().create(ApiService.class);

        bindViews();
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
        layoutTestResult = findViewById(R.id.layoutTestResult);
        layoutTestScores = findViewById(R.id.layoutTestScores);
        layoutEvalHistory = findViewById(R.id.layoutEvalHistory);
        layoutNoHistory = findViewById(R.id.layoutNoHistory);
        tvTestPrioritySummary = findViewById(R.id.tvTestPrioritySummary);
        tvTestPriorityBreakdown = findViewById(R.id.tvTestPriorityBreakdown);
        tvTestPrimarySpecialist = findViewById(R.id.tvTestPrimarySpecialist);
        tvTestRoutedSpecialty = findViewById(R.id.tvTestRoutedSpecialty);
        tvTestNormalizedSymptoms = findViewById(R.id.tvTestNormalizedSymptoms);
        tvTestFactors = findViewById(R.id.tvTestFactors);
        tvTestDoctor = findViewById(R.id.tvTestDoctor);
        tvTestReasoning = findViewById(R.id.tvTestReasoning);
        tvTestModelSource = findViewById(R.id.tvTestModelSource);
        tvHistoryCount = findViewById(R.id.tvHistoryCount);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        btnRefreshHistory.setOnClickListener(v -> loadHistory());

        btnExampleCardio.setOnClickListener(v ->
                applyExample("I feel pressure in my chest and I am short of breath", 67));

        btnExampleFever.setOnClickListener(v ->
                applyExample("I have fever and cold for two days with body ache", 29));

        btnExampleRash.setOnClickListener(v ->
                applyExample("I have an itchy rash on my arms and neck", 34));

        btnRunTest.setOnClickListener(v -> {
            String symptoms = etTestSymptoms.getText() != null
                    ? etTestSymptoms.getText().toString().trim() : "";
            if (TextUtils.isEmpty(symptoms)) {
                Toast.makeText(this, "Please enter symptoms to test", Toast.LENGTH_SHORT).show();
                return;
            }

            Integer age = parseAgeInput();
            if (age == null) {
                Toast.makeText(this, "Please enter a valid age", Toast.LENGTH_SHORT).show();
                return;
            }

            runTestPrediction(symptoms, age);
        });
    }

    private void applyExample(String symptoms, int age) {
        etTestSymptoms.setText(symptoms);
        etTestSymptoms.setSelection(symptoms.length());
        etTestAge.setText(String.valueOf(age));
        runTestPrediction(symptoms, age);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private Integer parseAgeInput() {
        String ageText = etTestAge.getText() != null ? etTestAge.getText().toString().trim() : "";
        if (TextUtils.isEmpty(ageText)) return null;
        try {
            int age = Integer.parseInt(ageText);
            return (age >= 0 && age <= 130) ? age : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void runTestPrediction(String symptoms, int age) {
        btnRunTest.setEnabled(false);
        btnRunTest.setText("Running...");

        apiService.runAdminModelEval(new SymptomRequest(symptoms, age))
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
        tvTestPrioritySummary.setText(buildPrioritySummary(body));
        tvTestPriorityBreakdown.setText(buildPriorityBreakdown(
                body.getPriorityComponents(),
                body.getDerivedChiefComplaintSystem(),
                body.getTriageSource(),
                body.getTriageRecommendation(),
                body.getPriorityDecisionTrace()
        ));

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

        tvTestNormalizedSymptoms.setText(!TextUtils.isEmpty(body.getNormalizedSymptoms())
                ? body.getNormalizedSymptoms() : "—");

        List<String> factors = body.getExtractedFactors();
        tvTestFactors.setText(factors != null && !factors.isEmpty()
                ? TextUtils.join(", ", factors)
                : "No strong signals detected");

        renderPredictionScores(body.getSpecialtyScores(), layoutTestScores);

        SymptomPredictResponse.Doctor doc = body.getRecommendedDoctor();
        tvTestDoctor.setText(doc != null
                ? doc.getName() + " (" + doc.getSpecialty() + ")"
                : "—");
        tvTestReasoning.setText(body.getReasoning() != null ? body.getReasoning() : "—");
        tvTestModelSource.setText("Sources: " + buildModelSourceText(
                body.getModelSource(),
                body.getTriageSource()
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
            TextView tvNormalizedSymptoms = card.findViewById(R.id.tvEvalNormalizedSymptoms);
            TextView tvFactors = card.findViewById(R.id.tvEvalFactors);
            TextView tvPrioritySummary = card.findViewById(R.id.tvEvalPrioritySummary);
            TextView tvPriorityBreakdown = card.findViewById(R.id.tvEvalPriorityBreakdown);
            TextView tvRouteSummary = card.findViewById(R.id.tvEvalRouteSummary);
            LinearLayout lScores = card.findViewById(R.id.layoutEvalScores);
            TextView tvDoctor = card.findViewById(R.id.tvEvalDoctor);
            TextView tvReasoning = card.findViewById(R.id.tvEvalReasoning);

            String patientLabel = !TextUtils.isEmpty(entry.getPatientName())
                    ? entry.getPatientName() : "Unknown";
            if (entry.getAge() != null) {
                patientLabel += "  •  Age " + entry.getAge();
            }
            tvPatient.setText(patientLabel);
            tvTime.setText(formatTimestamp(entry.getTimestamp()));
            tvSource.setText(buildModelSourceText(
                    entry.getModelSource(),
                    entry.getTriageSource()
            ));

            int confPct = (int) Math.round(entry.getConfidence() * 100);
            tvConf.setText(entry.isLowConfidence()
                    ? "Low conf  " + confPct + "%"
                    : confPct + "% route");
            tvConf.setBackgroundResource(entry.isLowConfidence()
                    ? R.drawable.badge_medium
                    : R.drawable.badge_normal);

            tvSymptoms.setText(!TextUtils.isEmpty(entry.getSymptoms()) ? entry.getSymptoms() : "—");
            tvNormalizedSymptoms.setText(!TextUtils.isEmpty(entry.getNormalizedSymptoms())
                    ? entry.getNormalizedSymptoms() : "—");

            List<String> factors = entry.getExtractedFactors();
            tvFactors.setText(factors != null && !factors.isEmpty()
                    ? TextUtils.join(", ", factors)
                    : "No strong signals detected");

            tvPrioritySummary.setText(buildPrioritySummary(entry));
            tvPriorityBreakdown.setText(buildPriorityBreakdown(
                    entry.getPriorityComponents(),
                    entry.getDerivedChiefComplaintSystem(),
                    entry.getTriageSource(),
                    entry.getTriageRecommendation(),
                    entry.getPriorityDecisionTrace()
            ));

            String routeSummary = "Clinical fit: "
                    + (!TextUtils.isEmpty(entry.getPrimarySpecialist()) ? entry.getPrimarySpecialist() : "General Practice")
                    + "  •  SmartQ route: "
                    + (!TextUtils.isEmpty(entry.getRoutedSpecialty()) ? entry.getRoutedSpecialty() : "General OPD");
            if (entry.isLowConfidence()) {
                routeSummary += "  •  manual review recommended";
            }
            tvRouteSummary.setText(routeSummary);

            renderHistoryScores(entry.getSpecialtyScores(), lScores);

            ModelEvalHistoryResponse.RecommendedDoctor doc = entry.getRecommendedDoctor();
            tvDoctor.setText(doc != null
                    ? doc.getName() + " (" + doc.getSpecialty() + ")"
                    : "—");
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

    private String buildModelSourceText(String specialtySource, String triageSource) {
        ArrayList<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(specialtySource)) {
            parts.add(specialtySource);
        }
        if (!TextUtils.isEmpty(triageSource)) {
            parts.add(triageSource);
        }
        if (parts.isEmpty()) {
            return "specialty_hybrid_v1";
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
        if (body.getTriagePriorityClass() != null) {
            parts.add("KTAS " + body.getTriagePriorityClass());
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
        if (entry.getTriagePriorityClass() != null) {
            parts.add("KTAS " + entry.getTriagePriorityClass());
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
            if (components.getSymptomNlp() > 0) {
                parts.add("symptom boost " + formatScore(components.getSymptomNlp()));
            }
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
            if (components.getSymptomNlp() > 0) {
                parts.add("symptom boost " + formatScore(components.getSymptomNlp()));
            }
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
}

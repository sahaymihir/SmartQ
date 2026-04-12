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

    private TextInputEditText etTestSymptoms;
    private MaterialButton btnRunTest, btnBack, btnRefreshHistory;
    private MaterialButton btnExampleCardio, btnExampleFever, btnExampleRash;
    private LinearLayout layoutTestResult, layoutTestScores, layoutEvalHistory, layoutNoHistory;
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
        layoutTestResult = findViewById(R.id.layoutTestResult);
        layoutTestScores = findViewById(R.id.layoutTestScores);
        layoutEvalHistory = findViewById(R.id.layoutEvalHistory);
        layoutNoHistory = findViewById(R.id.layoutNoHistory);
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
                applyExample("I feel pressure in my chest and I am short of breath"));

        btnExampleFever.setOnClickListener(v ->
                applyExample("I have fever and cold for two days with body ache"));

        btnExampleRash.setOnClickListener(v ->
                applyExample("I have an itchy rash on my arms and neck"));

        btnRunTest.setOnClickListener(v -> {
            String symptoms = etTestSymptoms.getText() != null
                    ? etTestSymptoms.getText().toString().trim() : "";
            if (TextUtils.isEmpty(symptoms)) {
                Toast.makeText(this, "Please enter symptoms to test", Toast.LENGTH_SHORT).show();
                return;
            }
            runTestPrediction(symptoms);
        });
    }

    private void applyExample(String symptoms) {
        etTestSymptoms.setText(symptoms);
        etTestSymptoms.setSelection(symptoms.length());
        runTestPrediction(symptoms);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    private void runTestPrediction(String symptoms) {
        btnRunTest.setEnabled(false);
        btnRunTest.setText("Running...");

        apiService.predictDoctor(new SymptomRequest(symptoms))
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
        tvTestModelSource.setText("Source: "
                + (!TextUtils.isEmpty(body.getModelSource()) ? body.getModelSource() : "specialty_hybrid_v1"));

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
            TextView tvRouteSummary = card.findViewById(R.id.tvEvalRouteSummary);
            LinearLayout lScores = card.findViewById(R.id.layoutEvalScores);
            TextView tvDoctor = card.findViewById(R.id.tvEvalDoctor);
            TextView tvReasoning = card.findViewById(R.id.tvEvalReasoning);

            tvPatient.setText(!TextUtils.isEmpty(entry.getPatientName())
                    ? entry.getPatientName() : "Unknown");
            tvTime.setText(formatTimestamp(entry.getTimestamp()));
            tvSource.setText(!TextUtils.isEmpty(entry.getModelSource())
                    ? entry.getModelSource() : "specialty_hybrid_v1");

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
}

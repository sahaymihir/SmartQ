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

    // Test panel views
    private TextInputEditText etTestSymptoms;
    private MaterialButton btnRunTest, btnBack, btnRefreshHistory;
    private LinearLayout layoutTestResult, layoutTestScores, layoutEvalHistory, layoutNoHistory;
    private TextView tvTestFactors, tvTestDoctor, tvTestReasoning, tvHistoryCount;

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
        btnBack             = findViewById(R.id.btnBack);
        btnRunTest          = findViewById(R.id.btnRunTest);
        btnRefreshHistory   = findViewById(R.id.btnRefreshHistory);
        etTestSymptoms      = findViewById(R.id.etTestSymptoms);
        layoutTestResult    = findViewById(R.id.layoutTestResult);
        layoutTestScores    = findViewById(R.id.layoutTestScores);
        layoutEvalHistory   = findViewById(R.id.layoutEvalHistory);
        layoutNoHistory     = findViewById(R.id.layoutNoHistory);
        tvTestFactors       = findViewById(R.id.tvTestFactors);
        tvTestDoctor        = findViewById(R.id.tvTestDoctor);
        tvTestReasoning     = findViewById(R.id.tvTestReasoning);
        tvHistoryCount      = findViewById(R.id.tvHistoryCount);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            finish();
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        });

        btnRefreshHistory.setOnClickListener(v -> loadHistory());

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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    // ── Run a test prediction and show inline result ───────────
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
                            loadHistory(); // refresh history after test
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

    // ── Render inline test result ──────────────────────────────
    private void showTestResult(SymptomPredictResponse body) {
        // Extracted factors
        List<String> factors = body.getExtractedFactors();
        tvTestFactors.setText(factors != null && !factors.isEmpty()
                ? TextUtils.join(", ", factors) : "No specific keywords detected");

        // Top 4 specialty scores
        layoutTestScores.removeAllViews();
        List<SymptomPredictResponse.SpecialtyScore> scores = body.getSpecialtyScores();
        if (scores != null) {
            int shown = 0;
            for (SymptomPredictResponse.SpecialtyScore s : scores) {
                if (s.getScore() <= 0.03 || shown >= 4) continue;
                TextView tv = new TextView(this);
                int pct = (int) (s.getScore() * 100);
                String keywords = s.getMatchedKeywords() != null && !s.getMatchedKeywords().isEmpty()
                        ? " ← " + TextUtils.join(", ", s.getMatchedKeywords()) : "";
                tv.setText("• " + s.getSpecialty() + "  " + pct + "%" + keywords);
                tv.setTextSize(13f);
                tv.setTextColor(getResources().getColor(R.color.text_primary));
                tv.setPadding(0, 4, 0, 4);
                layoutTestScores.addView(tv);
                shown++;
            }
            if (shown == 0) {
                TextView tv = new TextView(this);
                tv.setText("• No strong specialty signals detected");
                tv.setTextSize(13f);
                tv.setTextColor(getResources().getColor(R.color.text_secondary));
                layoutTestScores.addView(tv);
            }
        }

        // Recommended doctor
        SymptomPredictResponse.Doctor doc = body.getRecommendedDoctor();
        tvTestDoctor.setText(doc != null
                ? doc.getName() + "  (" + doc.getSpecialty() + ")" : "—");
        tvTestReasoning.setText(body.getReasoning() != null ? body.getReasoning() : "—");

        layoutTestResult.setVisibility(View.VISIBLE);
    }

    // ── Load prediction history from server ────────────────────
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

    // ── Render history list ────────────────────────────────────
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

            TextView tvPatient    = card.findViewById(R.id.tvEvalPatientName);
            TextView tvTime       = card.findViewById(R.id.tvEvalTime);
            TextView tvConf       = card.findViewById(R.id.tvEvalConfidence);
            TextView tvSymptoms   = card.findViewById(R.id.tvEvalSymptoms);
            TextView tvFactors    = card.findViewById(R.id.tvEvalFactors);
            LinearLayout lScores  = card.findViewById(R.id.layoutEvalScores);
            TextView tvDoctor     = card.findViewById(R.id.tvEvalDoctor);
            TextView tvReasoning  = card.findViewById(R.id.tvEvalReasoning);

            tvPatient.setText(entry.getPatientName() != null ? entry.getPatientName() : "Unknown");
            tvTime.setText(formatTimestamp(entry.getTimestamp()));
            int confPct = (int) (entry.getConfidence() * 100);
            tvConf.setText(confPct + "% match");
            tvSymptoms.setText(entry.getSymptoms() != null ? entry.getSymptoms() : "—");

            // Factors
            List<String> factors = entry.getExtractedFactors();
            tvFactors.setText(factors != null && !factors.isEmpty()
                    ? TextUtils.join(", ", factors) : "No keywords detected");

            // Top specialty scores (top 3 non-trivial ones)
            lScores.removeAllViews();
            List<ModelEvalHistoryResponse.SpecialtyScore> scores = entry.getSpecialtyScores();
            if (scores != null) {
                int shown = 0;
                for (ModelEvalHistoryResponse.SpecialtyScore s : scores) {
                    if (s.getScore() <= 0.03 || shown >= 3) continue;
                    TextView tv = new TextView(this);
                    int pct = (int) (s.getScore() * 100);
                    String kw = s.getMatchedKeywords() != null && !s.getMatchedKeywords().isEmpty()
                            ? " ← " + TextUtils.join(", ", s.getMatchedKeywords()) : "";
                    tv.setText("• " + s.getSpecialty() + "  " + pct + "%" + kw);
                    tv.setTextSize(12f);
                    tv.setTextColor(getResources().getColor(R.color.text_primary));
                    tv.setPadding(0, 3, 0, 3);
                    lScores.addView(tv);
                    shown++;
                }
            }

            // Recommended doctor
            ModelEvalHistoryResponse.RecommendedDoctor doc = entry.getRecommendedDoctor();
            tvDoctor.setText(doc != null ? doc.getName() + " (" + doc.getSpecialty() + ")" : "—");
            tvReasoning.setText(entry.getReasoning() != null ? entry.getReasoning() : "—");

            layoutEvalHistory.addView(card);
        }
    }

    // ── Format ISO timestamp to human-readable ─────────────────
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

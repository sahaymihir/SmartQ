# SmartQ Backend v2 Report Notes

This note is meant to help rewrite the older April 10, 2026 report into a cleaner **SmartQ v1 -> SmartQ v2** story.

## 1. Project Evolution

### SmartQ v1
- Rule-based hospital queue system
- Age-based priority bump for senior citizens
- Moving-average ETA using the last 10 consultation durations
- Core queue lifecycle: join, status, snooze, check-in, no-show, pause/resume, call next

### SmartQ v2
- Node/Express + MongoDB remain the system of record for queues and timings
- FastAPI acts as a separate ML triage microservice
- Triage is now **visit-level**, not just user-level
- ETA is still **hybrid and deterministic in production**
- Backend now stores triage confidence, timing ground truth, and analytics-friendly token fields

## 2. What Improved in Backend v2

- Moved from static user priority to **token-level triage**
- Added ML-assisted urgency scoring using age + sparse vitals + contextual fields
- Kept a conservative age-based guardrail so elderly patients are never down-ranked by the model
- Persisted timing fields for future ETA model training:
  - `joinedAt`
  - `calledAt`
  - `consultationStartedAt`
  - `completedAt`
  - `predictedWaitMinutes`
  - `actualWaitMinutes`
  - `predictedConsultMinutes`
  - `actualConsultMinutes`
- Added backend analytics endpoints for:
  - daily queue metrics
  - triage class distribution
  - ETA accuracy tracking
  - low-confidence triage counts
  - no-show and snooze rates

## 3. Triage Dataset Statistics

The current local triage dataset is the correct dataset to justify the **triage model**, not the ETA model.

### Available files
- `train.csv`: **80,000 rows, 40 columns**
- `chief_complaints.csv`: **100,000 rows**
- `patient_history.csv`: **100,000 rows**

### Target distribution (`triage_acuity`)
- Class 1: **3,222** records (**4.03%**)
- Class 2: **13,439** records (**16.80%**)
- Class 3: **28,921** records (**36.15%**)
- Class 4: **23,020** records (**28.78%**)
- Class 5: **11,398** records (**14.25%**)

### Missing-data profile
- `systolic_bp`, `diastolic_bp`, `mean_arterial_pressure`, `shock_index`, `pulse_pressure`: **5.18%**
- `respiratory_rate`: **3.83%**
- `temperature_c`: **0.72%**

### Useful descriptive statistics
- Median age: **48**
- Median systolic BP: **123.1**
- Median diastolic BP: **75.3**
- Median heart rate: **89.6**
- Median respiratory rate: **17.3**
- Median temperature: **37.5 C**
- Median SpO2: **97**
- Median GCS: **15**
- Median pain score: **5**

## 4. Why This Dataset Was Chosen

### Why it is a good choice for triage
- It directly matches the SmartQ triage problem: **5-level acuity classification**
- It contains clinically meaningful fields that SmartQ can realistically capture during queue join:
  - vitals
  - pain score
  - mental status
  - age and demographics
  - arrival context
  - prior utilization
- It is large enough for multiclass training and class-imbalance handling
- It supports feature engineering aligned with backend use cases:
  - `shock_index`
  - `hypoxia_flag`
  - `spo2_resp_interaction`
  - `multi_risk_flag`

### Why it is not enough for ETA prediction
- It predicts **triage acuity**, not actual waiting time in SmartQ
- SmartQ wait time depends on:
  - doctor-specific pace
  - queue length
  - pauses
  - snoozes
  - no-shows
  - actual consultation durations
- Those timing signals come from SmartQ’s own token lifecycle, not from the current triage dataset

## 5. Current Model Results

The saved v3 triage model already gives a credible backend story:

- Model family: **XGBoost**
- Accuracy: **0.8501**
- Weighted ROC-AUC: **0.9697**
- Low-confidence cases at threshold `< 0.60`: **11.93%**
- Test errors: **2,399 / 16,000**
- Adjacent errors: **2,359**
- Dangerous errors (`>= 2` levels away): **40**

### Interpretation
- Most mistakes are **adjacent-class errors**, which is much safer than large misclassification jumps
- The low-confidence signal is useful for backend guardrails and admin review
- The model is strong enough to support **triage assistance now**
- It is **not** enough to justify replacing ETA with ML yet

## 6. ETA Dataset Positioning

### Production stance
- SmartQ production ETA remains the current **moving-average algorithm**
- This is the honest and defensible choice until SmartQ collects its own timing logs

### Research datasets for ETA experimentation
- **Primary**: MIMIC-IV-ED v2.2
  - Strong for ED timing experiments
  - Includes large-scale emergency department visit timing data
  - Good research benchmark, but not SmartQ-ready OPD production data
- **Supporting benchmark**: NHAMCS
  - Useful for public-health and literature positioning
  - Better for justification than for direct deployment

## 7. Suggested Report Language

### Backend contribution statement
SmartQ v2 upgrades the original rule-based queue engine by adding ML-assisted, visit-level triage while preserving a deterministic and auditable ETA engine. This creates a safer hybrid system in which machine learning improves prioritization, and the queue logic continues to operate with predictable timing estimates.

### Dataset justification statement
The selected triage dataset was chosen because it directly models the clinical acuity decision SmartQ needs to make at queue-entry time. It contains relevant vitals, demographics, utilization history, and contextual features, and its multiclass KTAS-style label structure maps naturally to SmartQ’s priority workflow. However, because it does not contain SmartQ-specific consultation timing and queue-event data, it is appropriate for triage prediction but not sufficient for production ETA prediction.

### Honest ETA statement
ETA prediction in SmartQ is currently implemented as a moving-average method based on recent consultation durations. This remains the production-ready ETA strategy. Machine-learning-based ETA prediction is positioned as a future phase that will be trained either on external hospital-timing benchmarks for experimentation or, preferably, on SmartQ’s own real usage logs for deployment.

## 8. Conclusion

### Recommended final conclusion
SmartQ v1 demonstrated that a hospital queue can be digitized into a reliable backend system with queue control, adaptive ETA, and role-based workflows. SmartQ v2 strengthens that system by introducing ML-assisted triage at the visit level, allowing urgency decisions to move beyond age-only rules while still preserving conservative safety guardrails. The result is a more clinically aware backend without overclaiming full AI automation. ETA remains hybrid for now, and the backend has been instrumented to collect the exact timing data needed for a future production-grade ETA model.

## 9. Important Note

- Do **not** cite `generate_dataset.py` as the main dataset source.
- Treat it only as an earlier synthetic experiment.
- Use the real local CSVs and the saved v3 model results in the report.

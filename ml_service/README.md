# SmartQ ML Service

FastAPI inference service for SmartQ triage priority prediction using the saved v3 KTAS/XGBoost bundle.

## What This Folder Contains

- `main.py`
  The live FastAPI inference service used by the Node backend.
- `auto_ml_pipeline_v3.py`
  The retained v3 training pipeline that produced the current saved bundle.
- `evaluate_saved_model.py`
  Offline evaluation script that regenerates metrics, figures, and a markdown report from the saved v3 model.
- `models/triage_model_v3.pkl`
  Saved model bundle with selected features, encoders, scaler metadata, and headline metrics.
- `models/feature_cols_v3.pkl`
  Ordered list of the 40 selected model features.
- `models/scaler_v3.pkl`
  StandardScaler fitted on the numeric subset of the training split.
- `data/`
  The local tabular triage dataset package retained for reproducibility and future retraining.
- `reports/`
  Generated model evaluation outputs, including graphs and a markdown summary.

## What Was Removed

The folder has been cleaned to remove obsolete prototype assets that no longer support the current service:

- legacy NLP-style service code
- legacy v1/v2 training scripts
- old non-v3 model artifacts
- the old synthetic prototype dataset files

## Runtime Endpoints

- `POST /predict`
- `GET /health`

## Local Runtime Setup

```bash
cd ml_service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

The service resolves the v3 artifacts from:

- `ml_service/models/`
- the project root, if a deployment layout places them there

## Example Request

```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "age": 72,
    "heart_rate": 124,
    "systolic_bp": 92,
    "diastolic_bp": 58,
    "respiratory_rate": 28,
    "spo2": 89,
    "temperature_c": 38.9,
    "pain_score": 9,
    "gcs_total": 13,
    "arrival_mode": "ambulance",
    "mental_status_triage": "confused",
    "chief_complaint_system": "respiratory",
    "sex": "F"
  }'
```

Example response:

```json
{
  "priority_class": 2,
  "confidence": 0.8734,
  "low_confidence": false,
  "recommendation": "Emergency — seen within 15 minutes",
  "all_class_probs": {
    "1": 0.1123,
    "2": 0.8734,
    "3": 0.0104,
    "4": 0.0021,
    "5": 0.0018
  }
}
```

## Health Check

```bash
curl http://localhost:8000/health
```

Expected response:

```json
{
  "status": "ok",
  "model_version": "v3"
}
```

## Offline Evaluation

Use the dev requirements when you want the graphs and markdown report:

```bash
cd ml_service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
python evaluate_saved_model.py
```

This produces:

- `reports/latest_model_evaluation.md`
- `reports/latest_model_metrics.json`
- `reports/figures/*.png`

## Docker

Build from the repository root so Docker can copy the service files and model artifacts:

```bash
docker build -f ml_service/Dockerfile -t smartq-ml-service .
docker run --rm -p 8000:8000 smartq-ml-service
```

## Backend Integration

- Missing numeric features are filled with v3 training-set medians.
- Runtime engineered features include `shock_index`, `hypoxia_flag`, `multi_risk_flag`, `mean_arterial_pressure`, `pulse_pressure`, `spo2_resp_interaction`, `age_group`, `shift`, and `arrival_season`.
- Engineered features are always recomputed inside the service so the live inference path matches the saved v3 training bundle.
- If a client sends engineered values such as `shock_index` or `multi_risk_flag`, the service treats them as advisory and overwrites them with server-side calculations.
- The Node backend uses the ML result as visit-level triage metadata, not as a permanent user-level attribute.
- SmartQ can treat `priority_class` values `1` or `2` as the top-priority override case.

## Deployment Note

Before production deployment, review the generated report in `reports/latest_model_evaluation.md`. It includes a consistency check between the saved training pipeline and the current inference service so you can catch feature drift before hosting the service publicly.

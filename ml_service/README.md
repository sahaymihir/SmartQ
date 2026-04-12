# SmartQ ML Service

FastAPI inference service with three specialized machine learning models for healthcare triage and diagnostics.

## Overview

SmartQ's ML pipeline consists of **three complementary models**:

1. **Triage Model (v3)** — Symptoms → Urgency/Priority ✅ Production
2. **Specialty Model (v2)** — Symptoms → Medical Specialty 🔨 In Development  
3. **Diagnostic Tests (v1)** — Symptoms → Recommended Tests 🎯 Planned

See `models/README.md` for detailed documentation on each model.

## Service Structure

```
ml_service/
├── main.py                      # FastAPI inference service
├── auto_ml_pipeline_v3.py       # v3 training pipeline (reference)
├── evaluate_saved_model.py      # v3 offline evaluation script
├── requirements.txt             # Production dependencies
├── requirements-dev.txt         # Dev + training dependencies
├── Dockerfile                   # Container build
├── models/
│   ├── config.py                # Shared configuration
│   ├── __init__.py
│   ├── README.md                # Model documentation
│   ├── triage_v3/
│   │   ├── training/
│   │   │   └── train_triage_v3.py
│   │   └── model/               # Production artifacts
│   │       ├── triage_model_v3.pkl ✅
│   │       ├── scaler_v3.pkl ✅
│   │       └── feature_cols_v3.pkl ✅
│   ├── specialty_v2/
│   │   ├── training/
│   │   │   └── train_specialty_v2.py
│   │   └── model/               # (coming soon)
│   └── tests_v1/
│       ├── training/
│       │   └── train_tests_v1.py
│       └── model/               # (coming soon)
└── src/                         # Utility modules (tracked)
    └── (future utilities)
```

## Current Production Endpoints

### `POST /predict` — Triage Prediction
Predicts KTAS priority class (1-5) from patient vitals and symptoms.

**Request:**
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

**Response:**
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

### `GET /health` — Health Check
```bash
curl http://localhost:8000/health
```

Response:
```json
{
  "status": "ok",
  "model_version": "v3"
}
```

### `GET /` — Service Info
Root endpoint with service metadata and available endpoints.

## Future Endpoints (In Development)

- `POST /specialty` — Route patient to appropriate specialty
- `POST /tests` — Get recommended diagnostic tests

## Local Setup

```bash
cd ml_service

# Create virtual environment
python3 -m venv .venv
source .venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Run service
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

## Retraining Models

To retrain any model locally, place dataset CSV files in the respective training directory:

```bash
# Triage model
cd models/triage_v3/training
mkdir -p datasets/
# Place train.csv, chief_complaints.csv, patient_history.csv in datasets/
python train_triage_v3.py --data-dir datasets/ --output-dir ../model/

# Specialty model (in development)
cd models/specialty_v2/training
mkdir -p datasets/
# Place specialty_train.csv in datasets/
python train_specialty_v2.py --data-dir datasets/ --output-dir ../model/

# Diagnostic tests model (planned)
cd models/tests_v1/training
mkdir -p datasets/
python train_tests_v1.py --data-dir datasets/ --output-dir ../model/
```

## Development

### Install Dev Dependencies
```bash
pip install -r requirements-dev.txt
```

### Run Evaluation
```bash
python evaluate_saved_model.py
```

Generates:
- `models/evaluation/latest_metrics.json` — Numeric metrics
- `models/evaluation/latest_report.md` — Full evaluation report
- `models/evaluation/figures/` — Plots and visualizations

## Data Policy

**Training datasets are NOT committed to the repository** to keep it lean:
- Raw CSV data lives locally in `{model}/training/datasets/`
- Only final model artifacts (`.pkl` files) are version-controlled
- `.gitignore` automatically excludes all dataset directories

To retrain locally without affecting the repo, simply place datasets in the training directory and run the training script.

## Docker Deployment

```bash
# Build from repository root
docker build -f ml_service/Dockerfile -t smartq-ml-service .

# Run
docker run --rm -p 8000:8000 smartq-ml-service

# Verify
curl http://localhost:8000/health
```

## Deployment Notes

- Model artifacts are lightweight (~3 MB total for v3)
- Service starts in ~2-3 seconds
- No GPU required for inference
- Supports horizontal scaling (stateless FastAPI app)

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

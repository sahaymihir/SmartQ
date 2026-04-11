# SmartQ ML Inference Service

FastAPI microservice for SmartQ triage priority prediction using the saved v3 KTAS model artifacts.

## Endpoints

- `POST /predict`
- `GET /health`

## Local Setup

```bash
cd ml_service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

The service looks for `triage_model_v3.pkl`, `feature_cols_v3.pkl`, and `scaler_v3.pkl` in either:

- the project root
- `ml_service/models/`

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

Example response shape:

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

## Docker

Build from the repository root so Docker can copy the service files and model artifacts:

```bash
docker build -f ml_service/Dockerfile -t smartq-ml-service .
docker run --rm -p 8000:8000 smartq-ml-service
```

## Integration Notes

- Missing numeric features are filled with v3 training-set medians.
- Derived features such as `shock_index`, `hypoxia_flag`, `multi_risk_flag`, `mean_arterial_pressure`, `pulse_pressure`, `spo2_resp_interaction`, `age_group`, `shift`, and `arrival_season` are computed inside the service.
- The Node.js backend can treat `priority_class` values `1` or `2` as the SmartQ override case and assign queue score `10`; otherwise it can fall back to the age-based score logic.

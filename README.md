# SmartQ — Hospital Queue Management with ML-Assisted Triage

SmartQ is a full-stack hospital queue-management system that reduces waiting-room
congestion and assists staff with patient prioritisation. It pairs a conventional
queue/token backend with a machine-learning microservice that predicts triage
acuity from arrival vitals and routes low-confidence cases to human review.

The system has three tiers:

1. **Android client** — native Java/XML app (Retrofit + OkHttp) used for the live demo.
2. **Backend API** — Node.js + Express + MongoDB, the source of truth for queues,
   tokens, ETA, prescriptions, and role-based access control.
3. **ML service** — a FastAPI microservice serving an XGBoost triage model plus
   rule-assisted specialty routing and test-recommendation helpers.

---

## What is real vs. demo scope

This section is deliberately honest so the capabilities are not overstated.

| Capability | Status |
| --- | --- |
| Triage acuity prediction (`POST /predict`) | **Live, model-backed** — saved XGBoost bundle in `ml_service/models/triage_v3/model/` |
| Queue / token / ETA / snooze / no-show logic | **Live** in the Node backend |
| Specialty routing & test recommendations | **Live but rule-assisted heuristics**, not separately trained production models |
| OCR prescription intake | Uses a real OCR service when `OCR_SERVICE_URL` is set; otherwise falls back to a structured placeholder flow |
| Firebase push notifications | Registration wired and persisted; treat end-to-end delivery as demo scope until device-tested |
| Geofence auto check-in | Scaffolded only — explicit "Check-In" is the production path |
| Compose/Kotlin screens | Exploratory prototype, **not** the live demo surface (Java/XML is) |

The dataset is a public/synthetic emergency-department–style dataset, not real
clinical data.

---

## Triage model — actual numbers

Trained by `ml_service/auto_ml_pipeline_v3.py`. Metrics below are from
`ml_service/reports/latest_model_metrics.json` (held-out test set, 16,000 rows).

**Model:** XGBoost (selected over LightGBM and a soft-voting ensemble), 40 selected
features, SMOTE applied for class imbalance.

### Overall

| Metric | Value |
| --- | --- |
| Accuracy | 0.8501 |
| Weighted ROC-AUC | 0.9697 |
| Macro F1 | 0.8681 |
| Weighted F1 | 0.8509 |
| Test rows | 16,000 |
| Prior baseline accuracy | 0.8499 |
| Accuracy delta vs. baseline | **+0.0002** |

> Flat accuracy barely moves the baseline. The value of this model is **not** the
> headline accuracy — it is the **safety-aware error profile and confidence
> calibration** described below.

### Error profile (why off-by-one matters in triage)

| Error type | Count | Share of test set |
| --- | --- | --- |
| Total errors | 2,399 | 15.0% |
| Adjacent errors (predicted ±1 acuity level) | 2,359 | — |
| **Dangerous errors (≥2 levels off)** | **40** | **0.25%** |

98.3% of all misclassifications are off by a single acuity level; only 40 of 16,000
predictions are off by two or more.

### Confidence calibration

The model exposes per-prediction confidence; cases below 0.60 are flagged for human
review (~11.9% of cases).

| Confidence bucket | Count | Accuracy in bucket |
| --- | --- | --- |
| < 0.60 (flagged for review) | 1,909 | 0.541 |
| 0.60–0.69 | 1,623 | 0.645 |
| 0.70–0.79 | 1,812 | 0.745 |
| 0.80–0.89 | 2,323 | 0.862 |
| 0.90–1.00 | 8,333 | 0.981 |

Accuracy rises monotonically with confidence — the model "knows when it is unsure,"
which is what makes confidence-gated human handoff meaningful.

### Per-class metrics

| Acuity (1 = most urgent) | Precision | Recall | F1 | Support |
| --- | --- | --- | --- | --- |
| 1 | 0.939 | 0.947 | 0.943 | 644 |
| 2 | 0.977 | 0.964 | 0.970 | 2,688 |
| 3 | 0.899 | 0.869 | 0.884 | 5,784 |
| 4 | 0.762 | 0.759 | 0.761 | 4,604 |
| 5 | 0.746 | 0.825 | 0.783 | 2,280 |

The most urgent classes (1 and 2) are predicted most reliably; confusion
concentrates between the lower-urgency classes 4 and 5.

---

## Architecture

```
Android (Java/XML, Retrofit)
        │  HTTPS / JWT
        ▼
Node.js + Express  ──── MongoDB (Mongoose)
        │  HTTP (with retry/timeout config)
        ▼
FastAPI ML service  ──── XGBoost triage bundle (joblib)
```

The backend calls the ML service over HTTP with configurable timeout/retry
(`TRIAGE_*`, `SPECIALTY_*`, `PATIENT_FLOW_*` env vars) and falls back gracefully so
queue operation never hard-depends on the model being up.

---

## Technology stack

**Backend** — Node.js, Express 4, MongoDB + Mongoose 8, JWT (`jsonwebtoken`),
`bcryptjs`, `express-rate-limit`, `multer`, `axios`, `dotenv`.

**ML service** — Python, FastAPI 0.115, Uvicorn, XGBoost 2.1, scikit-learn 1.5,
pandas 2.2, NumPy 1.26, imbalanced-learn (SMOTE), joblib. Containerised (`Dockerfile`,
exposes port 8000).

**Frontend** — Android (Java + XML), Retrofit2, OkHttp, Gson, Material Components,
Firebase Cloud Messaging.

---

## Repository layout

```
backend/        Node/Express API
  routes/       auth, queue, admin, doctors, notifications, prescriptions, users
  services/     triage, specialty, patientFlow, prescription, notification, demoSeed
  models/       User, Queue (+ Token), Prescription
  tests/        clinicalScoring.test.js
ml_service/     FastAPI app + training pipeline
  main.py                  serving app (/predict, /specialty, /patient-flow,
                           /test-recommendations, /health)
  auto_ml_pipeline_v3.py   training pipeline (clean → engineer → select → SMOTE →
                           train 3 models → tune → evaluate → save bundle)
  specialty_hybrid.py      rule-assisted specialty routing
  evaluate_saved_model.py  re-evaluate the saved bundle
  models/ data/ reports/   saved model, dataset, metrics + figures
frontend/       Android app (Java/XML primary; Compose/Kotlin prototype)
docs/           capability matrix, report sources, demo credentials
```

---

## API endpoints

### Backend (`/api`)
- `auth` — registration, JWT login (email/phone validation + normalisation)
- `queue` — join, status, snooze (capped at 2), check-in, leave
- `admin` — call next, no-show, pause/break, full queue list
- `doctors` — doctor directory, symptom-based specialty prediction
- `prescriptions` — create/finalise prescriptions, OCR intake
- `notifications` — push registration, queue-event notifications
- `users` — user management (admin / superuser)

### ML service
- `POST /predict` — triage acuity + confidence
- `POST /specialty` — rule-assisted specialty routing
- `POST /patient-flow` — combined flow helper
- `POST /test-recommendations` — suggested tests
- `GET /health`, `GET /` — health/root

---

## Running locally

### Backend
```bash
cd backend
npm install
# create backend/.env with at least: MONGO_URI, JWT_SECRET
# optional: PORT, TRIAGE_API_URL, SPECIALTY_API_URL, OCR_SERVICE_URL, FCM_SERVER_KEY
npm run dev          # nodemon
npm run seed:demo    # seed demo users (optional)
npm test             # clinical-scoring unit test
```
The server exits on startup if `MONGO_URI` or `JWT_SECRET` is missing.

### ML service
```bash
cd ml_service
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
# or: docker build -t smartq-ml . && docker run -p 8000:8000 smartq-ml
```

### Retrain the triage model
```bash
cd ml_service
python auto_ml_pipeline_v3.py      # writes models/triage_v3/model/ + reports/
python evaluate_saved_model.py     # re-evaluate the saved bundle
```

### Android
Open `frontend/` in Android Studio, set the backend base URL in the Retrofit
`ApiClient`, and run the Java/XML app.

---

## How priority and snooze work

**Priority preemption** — on join, the backend checks the token's priority score
(e.g. seniors get a bump). High-priority tokens are inserted ahead of the first
normal-priority token rather than appended FIFO.

**Snooze** — a late patient can push back a fixed number of positions (capped at 2
snoozes). Tokens between the old and new position shift forward by one and the
snoozing token is reinserted at the new index.

**ETA** — a moving average over the duration of the last 10 completed consultations.
ML-based ETA is explicitly a future research phase, not in production.

---

## Status & contributors

Primary developer: Mihir Sahay (~70 commits, all three tiers). Additional
contributions: Utkarsh Jha (2 commits) and automated code-review assistance
(~25 commits). Backend + ML service deployed on Render.

See [docs/CAPABILITY_MATRIX.md](docs/CAPABILITY_MATRIX.md) for the detailed
capability breakdown.

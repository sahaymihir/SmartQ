"""
SmartQ Triage Prediction API — v2 (Real Triagegeist Model)
============================================================
FastAPI microservice using the full Triagegeist-trained model.

The model uses 5000+ features:
  - TF-IDF on chief complaint text
  - Vital signs (BP, HR, SpO2, temp, etc.)
  - Demographics (age, sex)
  - Medical history flags
  - Engineered clinical indicators

Endpoints:
    GET  /                → Health check
    POST /predict-triage  → Full prediction (symptoms + optional vitals)
    POST /predict-simple  → Simple prediction (symptoms + age only — for Android app)

Usage:
    uvicorn app:app --host 0.0.0.0 --port 8000 --reload
"""

import os
import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional
from scipy.sparse import hstack, csr_matrix

# ── Paths ──────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "models", "triage_model.pkl")
VECTORIZER_PATH = os.path.join(BASE_DIR, "models", "tfidf_vectorizer.pkl")
ENCODERS_PATH = os.path.join(BASE_DIR, "models", "encoders.pkl")
FEATURE_COLS_PATH = os.path.join(BASE_DIR, "models", "feature_columns.pkl")
MAPPING_PATH = os.path.join(BASE_DIR, "models", "label_mapping.pkl")

# ── Load trained artifacts ─────────────────────────────────────
try:
    model = joblib.load(MODEL_PATH)
    vectorizer = joblib.load(VECTORIZER_PATH)
    label_encoders = joblib.load(ENCODERS_PATH)
    feature_columns = joblib.load(FEATURE_COLS_PATH)
    label_mapping = joblib.load(MAPPING_PATH)
    print(f"✅ Model loaded ({len(feature_columns)} features)")
except FileNotFoundError as e:
    print(f"⚠️  Model files not found. Run `python train.py` first.\n{e}")
    model = None
    vectorizer = None
    label_encoders = None
    feature_columns = None
    label_mapping = None

# ── Feature column definitions (must match train.py) ───────────
NUMERIC_FEATURES = [
    "age", "arrival_hour", "arrival_month",
    "num_prior_ed_visits_12m", "num_prior_admissions_12m",
    "num_active_medications", "num_comorbidities",
    "systolic_bp", "diastolic_bp", "mean_arterial_pressure", "pulse_pressure",
    "heart_rate", "respiratory_rate", "temperature_c", "spo2",
    "gcs_total", "pain_score", "weight_kg", "height_cm", "bmi",
    "shock_index", "news2_score",
]

CATEGORICAL_FEATURES = [
    "arrival_mode", "arrival_day", "arrival_season", "shift",
    "age_group", "sex", "language", "insurance_type",
    "transport_origin", "pain_location", "mental_status_triage",
    "chief_complaint_system",
]

HISTORY_FEATURES = [
    "hx_hypertension", "hx_diabetes_type2", "hx_diabetes_type1",
    "hx_asthma", "hx_copd", "hx_heart_failure", "hx_atrial_fibrillation",
    "hx_ckd", "hx_liver_disease", "hx_malignancy", "hx_obesity",
    "hx_depression", "hx_anxiety", "hx_dementia", "hx_epilepsy",
    "hx_hypothyroidism", "hx_hyperthyroidism", "hx_hiv",
    "hx_coagulopathy", "hx_immunosuppressed", "hx_pregnant",
    "hx_substance_use_disorder", "hx_coronary_artery_disease",
    "hx_stroke_prior", "hx_peripheral_vascular_disease",
]

# ── Default values for missing fields ──────────────────────────
NUMERIC_DEFAULTS = {
    "age": 40, "arrival_hour": 12, "arrival_month": 6,
    "num_prior_ed_visits_12m": 0, "num_prior_admissions_12m": 0,
    "num_active_medications": 0, "num_comorbidities": 0,
    "systolic_bp": 120.0, "diastolic_bp": 80.0,
    "mean_arterial_pressure": 93.0, "pulse_pressure": 40.0,
    "heart_rate": 75.0, "respiratory_rate": 16.0,
    "temperature_c": 37.0, "spo2": 98.0,
    "gcs_total": 15, "pain_score": 3,
    "weight_kg": 70.0, "height_cm": 170.0, "bmi": 24.0,
    "shock_index": 0.63, "news2_score": 1,
}

CATEGORICAL_DEFAULTS = {
    "arrival_mode": "walk-in", "arrival_day": "Monday",
    "arrival_season": "spring", "shift": "morning",
    "age_group": "middle_aged", "sex": "M", "language": "English",
    "insurance_type": "public", "transport_origin": "home",
    "pain_location": "unknown", "mental_status_triage": "alert",
    "chief_complaint_system": "other",
}


# ── FastAPI App ────────────────────────────────────────────────
app = FastAPI(
    title="SmartQ Triage AI",
    description="NLP + Clinical data-based triage prediction for SmartQ",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── Request / Response Models ──────────────────────────────────

class SimpleTriageRequest(BaseModel):
    """Minimal request — just symptoms and age (used by Android app)."""
    symptoms: str
    age: int = 30

    class Config:
        json_schema_extra = {
            "example": {
                "symptoms": "severe chest pain radiating to left arm",
                "age": 65
            }
        }


class FullTriageRequest(BaseModel):
    """Full clinical request with vitals, demographics, and medical history."""
    symptoms: str
    age: int = 40
    sex: str = "M"
    # Vitals
    systolic_bp: Optional[float] = None
    diastolic_bp: Optional[float] = None
    heart_rate: Optional[float] = None
    respiratory_rate: Optional[float] = None
    temperature_c: Optional[float] = None
    spo2: Optional[float] = None
    gcs_total: Optional[int] = None
    pain_score: Optional[int] = None
    # Context
    arrival_mode: Optional[str] = None
    mental_status_triage: Optional[str] = None
    pain_location: Optional[str] = None
    chief_complaint_system: Optional[str] = None
    # History flags
    hx_hypertension: int = 0
    hx_diabetes_type2: int = 0
    hx_heart_failure: int = 0
    hx_copd: int = 0
    hx_asthma: int = 0
    hx_malignancy: int = 0
    hx_dementia: int = 0
    hx_pregnant: int = 0


class TriageResponse(BaseModel):
    predicted_esi_level: int       # 1-5 ESI scale
    priority_label: str            # "high", "medium", "normal"
    priority_score: int            # 10, 7, or 5
    confidence: float              # max class probability
    all_probabilities: dict        # per-ESI-level probabilities
    model_version: str = "v2-triagegeist"


# ── Helper: build feature vector ──────────────────────────────

def build_features_simple(symptoms: str, age: int):
    """Build a feature vector using only symptoms + age (fill rest with defaults)."""
    text = symptoms.lower().strip()
    X_text = vectorizer.transform([text])

    # Numeric: fill all defaults, override age
    numeric_vals = [NUMERIC_DEFAULTS.get(f, 0) for f in NUMERIC_FEATURES]
    numeric_vals[NUMERIC_FEATURES.index("age")] = age

    # Determine age group
    if age <= 12:
        age_group = "pediatric"
    elif age <= 17:
        age_group = "adolescent"
    elif age <= 39:
        age_group = "young_adult"
    elif age <= 64:
        age_group = "middle_aged"
    else:
        age_group = "elderly"

    # Categorical: fill defaults, override age_group
    cat_vals = []
    defaults_override = dict(CATEGORICAL_DEFAULTS)
    defaults_override["age_group"] = age_group
    for col in CATEGORICAL_FEATURES:
        le = label_encoders[col]
        val = defaults_override.get(col, "unknown")
        if val not in le.classes_:
            val = "unknown"
            if "unknown" not in le.classes_:
                le.classes_ = np.append(le.classes_, "unknown")
        cat_vals.append(le.transform([val])[0])

    # History: all zeros
    hist_vals = [0] * len(HISTORY_FEATURES)

    # Engineered
    eng_vals = [
        sum(hist_vals),               # total_history_flags
        int(age >= 65),               # is_elderly
        int(age <= 12),               # is_pediatric
        int(NUMERIC_DEFAULTS["gcs_total"] <= 8),  # critical_gcs
        int(NUMERIC_DEFAULTS["spo2"] < 92),       # low_spo2
        int(NUMERIC_DEFAULTS["news2_score"] >= 7), # high_news2
        int(NUMERIC_DEFAULTS["heart_rate"] > 100),  # high_heart_rate
        int(NUMERIC_DEFAULTS["heart_rate"] < 50),   # low_heart_rate
        int(NUMERIC_DEFAULTS["pain_score"] >= 8),   # severe_pain
        len(text),                     # complaint_length
        len(text.split()),             # complaint_word_count
    ]

    tabular = np.array(numeric_vals + cat_vals + hist_vals + eng_vals).reshape(1, -1)
    X_combined = hstack([X_text, csr_matrix(tabular)])

    return X_combined.toarray()


def predict_from_features(X_dense):
    """Run prediction and format response."""
    predicted_class = model.predict(X_dense)[0]
    probas = model.predict_proba(X_dense)[0]
    classes = model.classes_

    prob_dict = {f"ESI-{cls}": round(float(p) * 100, 1) for cls, p in zip(classes, probas)}
    confidence = round(float(max(probas)) * 100, 1)

    # Map to SmartQ priority
    if predicted_class <= 2:
        label, score = "high", 10
    elif predicted_class == 3:
        label, score = "medium", 7
    else:
        label, score = "normal", 5

    return TriageResponse(
        predicted_esi_level=int(predicted_class),
        priority_label=label,
        priority_score=score,
        confidence=confidence,
        all_probabilities=prob_dict,
    )


# ── Endpoints ──────────────────────────────────────────────────

@app.get("/")
async def health_check():
    return {
        "status": "SmartQ Triage AI v2 is running 🧠",
        "model_loaded": model is not None,
        "features": len(feature_columns) if feature_columns else 0,
    }


@app.post("/predict-triage", response_model=TriageResponse)
async def predict_simple(request: SimpleTriageRequest):
    """Quick prediction using only symptoms + age (for Android app / Node backend)."""
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded. Run train.py first.")

    if not request.symptoms or len(request.symptoms.strip()) < 3:
        raise HTTPException(status_code=400, detail="Symptoms text too short (min 3 chars).")

    X = build_features_simple(request.symptoms, request.age)
    return predict_from_features(X)


@app.post("/predict-full", response_model=TriageResponse)
async def predict_full(request: FullTriageRequest):
    """Full prediction using symptoms + vitals + history (for future admin UI)."""
    if model is None:
        raise HTTPException(status_code=503, detail="Model not loaded. Run train.py first.")

    if not request.symptoms or len(request.symptoms.strip()) < 3:
        raise HTTPException(status_code=400, detail="Symptoms text too short (min 3 chars).")

    # For full prediction, override defaults with provided values
    text = request.symptoms.lower().strip()
    X_text = vectorizer.transform([text])

    numeric_vals = [NUMERIC_DEFAULTS.get(f, 0) for f in NUMERIC_FEATURES]
    # Override with provided values
    overrides = {
        "age": request.age,
        "systolic_bp": request.systolic_bp,
        "diastolic_bp": request.diastolic_bp,
        "heart_rate": request.heart_rate,
        "respiratory_rate": request.respiratory_rate,
        "temperature_c": request.temperature_c,
        "spo2": request.spo2,
        "gcs_total": request.gcs_total,
        "pain_score": request.pain_score,
    }
    for key, val in overrides.items():
        if val is not None and key in NUMERIC_FEATURES:
            numeric_vals[NUMERIC_FEATURES.index(key)] = val

    # Compute derived vitals
    sbp = numeric_vals[NUMERIC_FEATURES.index("systolic_bp")]
    dbp = numeric_vals[NUMERIC_FEATURES.index("diastolic_bp")]
    hr = numeric_vals[NUMERIC_FEATURES.index("heart_rate")]
    numeric_vals[NUMERIC_FEATURES.index("mean_arterial_pressure")] = (sbp + 2 * dbp) / 3
    numeric_vals[NUMERIC_FEATURES.index("pulse_pressure")] = sbp - dbp
    if sbp > 0:
        numeric_vals[NUMERIC_FEATURES.index("shock_index")] = hr / sbp

    # Age group
    age = request.age
    if age <= 12:
        age_group = "pediatric"
    elif age <= 17:
        age_group = "adolescent"
    elif age <= 39:
        age_group = "young_adult"
    elif age <= 64:
        age_group = "middle_aged"
    else:
        age_group = "elderly"

    cat_defaults = dict(CATEGORICAL_DEFAULTS)
    cat_defaults["age_group"] = age_group
    cat_defaults["sex"] = request.sex
    if request.arrival_mode:
        cat_defaults["arrival_mode"] = request.arrival_mode
    if request.mental_status_triage:
        cat_defaults["mental_status_triage"] = request.mental_status_triage
    if request.pain_location:
        cat_defaults["pain_location"] = request.pain_location
    if request.chief_complaint_system:
        cat_defaults["chief_complaint_system"] = request.chief_complaint_system

    cat_vals = []
    for col in CATEGORICAL_FEATURES:
        le = label_encoders[col]
        val = cat_defaults.get(col, "unknown")
        if val not in le.classes_:
            val = "unknown"
            if "unknown" not in le.classes_:
                le.classes_ = np.append(le.classes_, "unknown")
        cat_vals.append(le.transform([val])[0])

    # History
    hist_vals = [
        getattr(request, f, 0) if hasattr(request, f) else 0
        for f in HISTORY_FEATURES
    ]

    # Engineered
    gcs = numeric_vals[NUMERIC_FEATURES.index("gcs_total")]
    spo2 = numeric_vals[NUMERIC_FEATURES.index("spo2")]
    news2 = numeric_vals[NUMERIC_FEATURES.index("news2_score")]
    pain = numeric_vals[NUMERIC_FEATURES.index("pain_score")]

    eng_vals = [
        sum(hist_vals),
        int(age >= 65),
        int(age <= 12),
        int(gcs <= 8),
        int(spo2 < 92),
        int(news2 >= 7),
        int(hr > 100),
        int(hr < 50),
        int(pain >= 8),
        len(text),
        len(text.split()),
    ]

    tabular = np.array(numeric_vals + cat_vals + hist_vals + eng_vals).reshape(1, -1)
    X_combined = hstack([X_text, csr_matrix(tabular)]).toarray()

    return predict_from_features(X_combined)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000, reload=True)

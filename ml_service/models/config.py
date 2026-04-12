"""
Shared configuration for SmartQ ML models.

This module provides constants and utilities used across all three specialized models:
- Triage (symptoms → urgency/priority)
- Specialty (symptoms → medical specialty)
- Diagnostic Tests (symptoms → recommended tests)
"""

import logging
from pathlib import Path

# ─────────────────────────────────────────────────────────────
# Logging
# ─────────────────────────────────────────────────────────────

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("smartq-ml")

# ─────────────────────────────────────────────────────────────
# Paths
# ─────────────────────────────────────────────────────────────

ML_SERVICE_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = ML_SERVICE_DIR / "models"

TRIAGE_MODEL_DIR = MODELS_DIR / "triage_v3" / "model"
SPECIALTY_MODEL_DIR = MODELS_DIR / "specialty_v2" / "model"
TESTS_MODEL_DIR = MODELS_DIR / "tests_v1" / "model"

# ─────────────────────────────────────────────────────────────
# Triage Model Configuration (Symptoms → Urgency/Priority)
# ─────────────────────────────────────────────────────────────

TRIAGE_MODEL_VERSION = "v3"
TRIAGE_CONFIDENCE_THRESHOLD = 0.60

# KTAS Priority Classes
TRIAGE_CLASSES = {
    1: "Immediate — resuscitation required",
    2: "Emergency — seen within 15 minutes",
    3: "Urgent — seen within 30 minutes",
    4: "Less urgent — seen within 60 minutes",
    5: "Non-urgent — seen within 120 minutes",
}

# Numeric feature defaults (v3 training-set medians)
TRIAGE_NUMERIC_DEFAULTS = {
    "news2_score": 2.0,
    "gcs_total": 15.0,
    "pain_score": 5.0,
    "spo2": 97.0,
    "temperature_c": 37.5,
    "respiratory_rate": 17.3,
    "spo2_resp_interaction": 1671.42,
    "mean_arterial_pressure": 91.9,
    "shock_index": 0.7240272613983953,
    "num_prior_ed_visits_12m": 1.0,
    "heart_rate": 89.6,
    "diastolic_bp": 75.3,
    "multi_risk_flag": 0.0,
    "systolic_bp": 123.1,
    "pulse_pressure": 47.2,
    "hypoxia_flag": 0.0,
    "height_cm": 171.1,
    "weight_kg": 76.0,
    "bmi": 26.0,
    "age": 48.0,
    "arrival_hour": 11.0,
    "num_comorbidities": 5.0,
    "num_active_medications": 4.0,
    "arrival_month": 7.0,
    "high_fever_flag": 0.0,
    "tachycardia_flag": 0.0,
    "num_prior_admissions_12m": 0.0,
}

# Categorical feature defaults
TRIAGE_CATEGORICAL_DEFAULTS = {
    "mental_status_triage": "alert",
    "chief_complaint_system": "other",
    "pain_location": "unknown",
    "arrival_day": "Monday",
    "transport_origin": "home",
    "language": "English",
    "site_id": "SITE-TUR-01",
    "arrival_mode": "walk-in",
    "arrival_season": "summer",
    "insurance_type": "public",
    "shift": "morning",
    "age_group": "middle_aged",
    "sex": "F",
}

# ─────────────────────────────────────────────────────────────
# Specialty Model Configuration (Symptoms → Medical Specialty)
# ─────────────────────────────────────────────────────────────

SPECIALTY_MODEL_VERSION = "v2"

SPECIALTY_CLASSES = [
    "Cardiology",
    "Orthopaedics",
    "Neurology",
    "General OPD",
    "Dermatology",
    "Gastroenterology",
    "Paediatrics",
    "Pulmonology",
]

# ─────────────────────────────────────────────────────────────
# Diagnostic Tests Model Configuration (Symptoms → Tests)
# ─────────────────────────────────────────────────────────────

TESTS_MODEL_VERSION = "v1"

# Common diagnostic tests
DIAGNOSTIC_TESTS = {
    "blood": ["CBC", "CMP", "Blood cultures", "Troponin", "Lactate"],
    "imaging": ["Chest X-ray", "ECG", "CT scan", "Ultrasound", "MRI"],
    "specialty": ["ABG", "Urinalysis", "EEG", "Coagulation panel"],
}

# Test urgency levels
TEST_URGENCY_LEVELS = ["immediate", "urgent", "routine"]

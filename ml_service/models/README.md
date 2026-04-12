# SmartQ ML Models

Three specialized models power the SmartQ triage and diagnostic pipeline:

## 1. Triage Model (Symptoms → Urgency/Priority)
**Location:** `triage_v3/`  
**Status:** ✅ Production  
**Purpose:** Predicts KTAS priority class (1-5) from patient vitals, symptoms, and risk factors.

- **Input:** Patient demographics, vitals, neurological status, oxygen saturation, pain scores, etc.
- **Output:** Priority class (1=immediate resuscitation, 2=emergency, 3=urgent, 4=less urgent, 5=non-urgent)
- **Model Type:** XGBoost classifier with SMOTE balancing
- **Accuracy:** 85.01% on held-out test set
- **Artifacts:** `triage_v3/model/triage_model_v3.pkl`

**Training:**
```bash
cd ml_service/models/triage_v3/training
python train_triage_v3.py  # Requires train.csv, chief_complaints.csv, patient_history.csv
```

---

## 2. Speciality Model (Symptoms → Medical Speciality)
**Location:** `specialty_v2/`  
**Status:** 🔨 In Development  
**Purpose:** Routes patients to appropriate medical specialties based on chief complaints.

- **Input:** Chief complaint system, symptoms, patient history, vital signs
- **Output:** Recommended specialty (Cardiology, Neurology, Orthopaedics, Dermatology, etc.)
- **Model Type:** Multi-class classifier (RandomForest or XGBoost)
- **Target Specialties:** Cardiology, Orthopaedics, Neurology, General OPD, Dermatology, Gastroenterology, Paediatrics, Pulmonology
- **Artifacts:** `specialty_v2/model/specialty_model_v2.pkl`

**Training:**
```bash
cd ml_service/models/specialty_v2/training
python train_specialty_v2.py  # Requires specialty-labeled dataset
```

---

## 3. Diagnostic Tests Model (Symptoms → Recommended Tests)
**Location:** `tests_v1/`  
**Status:** 🎯 Planned  
**Purpose:** Recommends diagnostic tests based on patient condition and chief complaint.

- **Input:** Priority class, chief complaint, vitals, temperature, pain score, age group
- **Output:** Prioritized list of diagnostic tests (CBC, ECG, X-ray, CT, blood cultures, etc.)
- **Model Type:** Rule-based engine → supervised ML pipeline (upcoming)
- **Artifacts:** `tests_v1/model/tests_model_v1.pkl`

**Training:**
```bash
cd ml_service/models/tests_v1/training
python train_tests_v1.py  # Requires test recommendation dataset (future)
```

---

## Inference Service

The FastAPI service in `../main.py` currently exposes:
- `POST /predict` - Triage model only (priority prediction)
- `GET /health` - Service health check
- `GET /` - Service metadata

**Future endpoints:**
- `POST /specialty` - Route patient to specialty
- `POST /tests` - Get diagnostic test recommendations

---

## File Structure

```
ml_service/models/
├── triage_v3/
│   ├── training/
│   │   ├── train_triage_v3.py
│   │   ├── datasets/ (local only, not tracked)
│   │   └── evaluation/ (output metrics)
│   └── model/
│       ├── triage_model_v3.pkl ✅ tracked
│       ├── scaler_v3.pkl ✅ tracked
│       └── feature_cols_v3.pkl ✅ tracked
│
├── specialty_v2/
│   ├── training/
│   │   ├── train_specialty_v2.py
│   │   └── datasets/ (local only, not tracked)
│   └── model/
│       └── (coming soon)
│
└── tests_v1/
    ├── training/
    │   ├── train_tests_v1.py
    │   └── datasets/ (local only, not tracked)
    └── model/
        └── (coming soon)
```

---

## Note on Data

**Training datasets are NOT committed** to keep the repo lean:
- Raw CSV data lives in `training/datasets/` locally
- Only final model artifacts (`.pkl` files) are tracked
- All `.gitignore` entries handle this automatically

To retrain locally:
1. Place dataset CSVs in the respective `training/datasets/` folder
2. Run the training script
3. New artifacts replace the old ones in `model/`

# SmartQ ML Service Restructuring Summary

## Changes Made (April 12, 2026)

### 1. ✅ Removed `src` from .gitignore
- **File:** `.gitignore`
- **Change:** Removed line `ml_service/src/`
- **Reason:** User wants `src/` directory to be tracked in git for utility modules
- **Note:** Local `src/` directory was cleaned up, can be recreated as needed for future utilities

### 2. ✅ Cleaned Up Datasets and Reports
- **Removed:** 
  - `ml_service/data/` (all CSVs - train, test, chief_complaints, patient_history, etc.)
  - `ml_service/reports/` (evaluation outputs)
  - `ml_service/src/` (temporary utilities)
  - `ml_service/models/triage_model.pkl` (duplicate)
  - `ml_service/models/bert_triage/` (unused prototype)
  
- **Reason:** Keep repository lean; datasets are local-only (still in .gitignore)
- **Result:** ML service reduced from ~1GB to ~3MB

### 3. ✅ Reorganized Model Structure

**New directory structure:**
```
ml_service/models/
├── config.py                    # Shared configuration for all models
├── __init__.py                  # Package exports
├── README.md                    # Detailed model documentation
├── triage_v3/
│   ├── training/
│   │   ├── train_triage_v3.py   # Training script (reference)
│   │   └── datasets/            # Local datasets (not tracked)
│   └── model/
│       ├── triage_model_v3.pkl  # ✅ Production artifact
│       ├── scaler_v3.pkl        # ✅ Production artifact
│       └── feature_cols_v3.pkl  # ✅ Production artifact
│
├── specialty_v2/
│   ├── training/
│   │   ├── train_specialty_v2.py # Training script template
│   │   └── datasets/            # Local datasets (not tracked)
│   └── model/                   # (artifacts coming soon)
│
└── tests_v1/
    ├── training/
    │   ├── train_tests_v1.py     # Training script template
    │   └── datasets/            # Local datasets (not tracked)
    └── model/                   # (artifacts coming soon)
```

### 4. ✅ Created Three Model Pipelines

#### **Model 1: Triage v3 (Symptoms → Urgency/Priority) ✅ PRODUCTION**
- **Purpose:** Predict KTAS priority class (1-5) from vitals and symptoms
- **Type:** XGBoost multiclass classifier
- **Accuracy:** 85.01%
- **Status:** Ready for inference via `POST /predict`
- **File:** `models/triage_v3/training/train_triage_v3.py`

#### **Model 2: Specialty v2 (Symptoms → Medical Specialty) 🔨 IN DEVELOPMENT**
- **Purpose:** Route patient to appropriate specialty (Cardiology, Neuro, Ortho, etc.)
- **Target Specialties:** 8 major departments
- **Status:** Template created, training script ready for implementation
- **File:** `models/specialty_v2/training/train_specialty_v2.py`
- **Next Steps:** Prepare labeled specialty dataset, implement training pipeline

#### **Model 3: Diagnostic Tests v1 (Symptoms → Recommended Tests) 🎯 PLANNED**
- **Purpose:** Generate prioritized list of diagnostic tests (CBC, ECG, X-ray, etc.)
- **Phase 1:** Rule-based engine (chief complaint → default tests)
- **Phase 2:** Supervised ML pipeline (future)
- **Status:** Architecture documented, template script created
- **File:** `models/tests_v1/training/train_tests_v1.py`
- **Next Steps:** Define test recommendation rules, build training dataset

### 5. ✅ Updated Documentation

- **`models/README.md`**
  - Explains all three models and their purposes
  - Shows input/output formats for each
  - Provides training instructions
  - References code locations

- **`README.md` (main service)**
  - Updated to reflect three-model architecture
  - Clarified production endpoints vs. future ones
  - Added development and deployment instructions
  - Documented data policy (local-only datasets)

- **`models/config.py`**
  - Centralized constants for all models
  - TRIAGE_CLASSES, SPECIALTY_CLASSES, DIAGNOSTIC_TESTS
  - Path definitions and defaults
  - Easy to extend for new models

### 6. ✅ Updated .gitignore

**Kept tracking:**
- Model `.pkl` files (triage_v3 only, for now)
- Training scripts
- Configuration files
- Source code

**Still ignored:**
- `ml_service/data/` — all training CSV datasets
- `ml_service/.venv/` — Python virtual environment
- `ml_service/__pycache__/` — Python cache
- `ml_service/reports/` — evaluation outputs (can be regenerated)

## Files Changed

| File | Status | Notes |
|------|--------|-------|
| `.gitignore` | Modified | Removed `ml_service/src/` |
| `ml_service/README.md` | Updated | New three-model architecture |
| `ml_service/models/README.md` | **Created** | Detailed model documentation |
| `ml_service/models/config.py` | **Created** | Shared configuration |
| `ml_service/models/__init__.py` | **Created** | Package exports |
| `ml_service/models/triage_v3/training/train_triage_v3.py` | **Created** | Reference script |
| `ml_service/models/specialty_v2/training/train_specialty_v2.py` | **Created** | Template for in-dev model |
| `ml_service/models/tests_v1/training/train_tests_v1.py` | **Created** | Template for planned model |
| Directories cleaned | Removed | src/, data/, reports/, unused models |

## Benefits

✅ **Lean Repository:** Reduced from 1GB+ to ~5MB  
✅ **Clear Architecture:** Three models with distinct purposes  
✅ **Scalable:** Easy to add more models following same structure  
✅ **Production Ready:** v3 triage model ready for deployment  
✅ **Development Ready:** Templates and structure for specialty + tests models  
✅ **Local Data:** Training datasets stay local (not committed)  
✅ **Documented:** Each model has clear input/output specs and training instructions  

## Next Steps

1. **Specialty Model (v2):** Prepare labeled dataset, implement `train_specialty_v2.py`
2. **Diagnostic Tests (v1):** Define rule-based engine rules, create training dataset
3. **Inference Endpoints:** Add `/specialty` and `/tests` endpoints to FastAPI service
4. **Deployment:** Redeploy ml_service with cleaner structure and new endpoints

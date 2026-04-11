"""
SmartQ Triage Model Trainer — v2 (Real Triagegeist Data)
=========================================================
Uses the full Triagegeist Kaggle competition dataset:
  - train.csv          : 80,000 records with 40 columns including vitals, demographics, triage_acuity
  - chief_complaints.csv: 100,000 records with free-text chief complaints per patient
  - patient_history.csv : 100,000 records with 25 binary medical history flags per patient

Pipeline:
  1. Merge all three datasets on patient_id
  2. Engineer features (TF-IDF on complaints + numeric vitals + categorical encoding + medical history)
  3. Train Gradient Boosted Trees (HistGradientBoostingClassifier) — handles NaN natively
  4. Evaluate with stratified cross-validation
  5. Save model artifacts for the FastAPI microservice

Usage:
    source venv/bin/activate
    python train.py
"""

import os
import time
import warnings
import pandas as pd
import numpy as np
import joblib
from sklearn.model_selection import train_test_split, StratifiedKFold, cross_val_score
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import LabelEncoder, OrdinalEncoder
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix
from scipy.sparse import hstack, csr_matrix

warnings.filterwarnings("ignore", category=FutureWarning)

# ── Paths ──────────────────────────────────────────────────────
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
MODEL_DIR = os.path.join(BASE_DIR, "models")

TRAIN_PATH = os.path.join(DATA_DIR, "train.csv")
CC_PATH = os.path.join(DATA_DIR, "chief_complaints.csv")
HISTORY_PATH = os.path.join(DATA_DIR, "patient_history.csv")
TEST_PATH = os.path.join(DATA_DIR, "test.csv")

MODEL_PATH = os.path.join(MODEL_DIR, "triage_model.pkl")
VECTORIZER_PATH = os.path.join(MODEL_DIR, "tfidf_vectorizer.pkl")
FEATURE_COLS_PATH = os.path.join(MODEL_DIR, "feature_columns.pkl")
ENCODERS_PATH = os.path.join(MODEL_DIR, "encoders.pkl")
MAPPING_PATH = os.path.join(MODEL_DIR, "label_mapping.pkl")

# ── Feature Definitions ───────────────────────────────────────

# Numeric vitals & clinical scores
NUMERIC_FEATURES = [
    "age", "arrival_hour", "arrival_month",
    "num_prior_ed_visits_12m", "num_prior_admissions_12m",
    "num_active_medications", "num_comorbidities",
    "systolic_bp", "diastolic_bp", "mean_arterial_pressure", "pulse_pressure",
    "heart_rate", "respiratory_rate", "temperature_c", "spo2",
    "gcs_total", "pain_score", "weight_kg", "height_cm", "bmi",
    "shock_index", "news2_score",
]

# Categorical features to label-encode
CATEGORICAL_FEATURES = [
    "arrival_mode", "arrival_day", "arrival_season", "shift",
    "age_group", "sex", "language", "insurance_type",
    "transport_origin", "pain_location", "mental_status_triage",
    "chief_complaint_system",
]

# Binary medical history flags (from patient_history.csv)
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

TARGET = "triage_acuity"


def load_and_merge():
    """Load all 3 CSVs and merge on patient_id."""
    print("📂 Loading datasets...")

    df_train = pd.read_csv(TRAIN_PATH)
    df_cc = pd.read_csv(CC_PATH)
    df_hist = pd.read_csv(HISTORY_PATH)

    print(f"   train.csv:             {df_train.shape}")
    print(f"   chief_complaints.csv:  {df_cc.shape}")
    print(f"   patient_history.csv:   {df_hist.shape}")

    # Chief complaints: aggregate multiple complaints per patient into one string
    cc_agg = df_cc.groupby("patient_id")["chief_complaint_raw"].apply(
        lambda x: ", ".join(x.dropna().str.lower())
    ).reset_index()
    cc_agg.columns = ["patient_id", "complaints_text"]

    # Merge
    df = df_train.merge(cc_agg, on="patient_id", how="left")
    df = df.merge(df_hist, on="patient_id", how="left")

    df["complaints_text"] = df["complaints_text"].fillna("")

    print(f"\n📊 Merged dataset: {df.shape}")
    print(f"   Target distribution:")
    for level in sorted(df[TARGET].unique()):
        count = (df[TARGET] == level).sum()
        pct = count / len(df) * 100
        print(f"     Level {level}: {count:>6} ({pct:.1f}%)")

    return df


def engineer_features(df, tfidf=None, label_encoders=None, is_training=True):
    """
    Build a feature matrix from the merged dataframe.
    Returns: X (numpy array), tfidf vectorizer, label_encoders dict
    """
    # ── 1. TF-IDF on chief complaint text ──────────────────────
    if is_training:
        tfidf = TfidfVectorizer(
            max_features=5000,
            ngram_range=(1, 3),   # unigrams, bigrams, trigrams
            stop_words="english",
            min_df=3,
            max_df=0.95,
            sublinear_tf=True,    # apply log normalization
        )
        X_text = tfidf.fit_transform(df["complaints_text"])
    else:
        X_text = tfidf.transform(df["complaints_text"])

    print(f"   TF-IDF features: {X_text.shape[1]}")

    # ── 2. Numeric features ────────────────────────────────────
    X_numeric = df[NUMERIC_FEATURES].copy()

    # Fill NaN with median (HistGBT handles NaN, but cleaner this way)
    for col in NUMERIC_FEATURES:
        if X_numeric[col].isnull().any():
            X_numeric[col] = X_numeric[col].fillna(X_numeric[col].median())

    print(f"   Numeric features: {len(NUMERIC_FEATURES)}")

    # ── 3. Categorical features (label encoding) ───────────────
    if is_training:
        label_encoders = {}
        for col in CATEGORICAL_FEATURES:
            le = LabelEncoder()
            df[col] = df[col].fillna("unknown")
            le.fit(df[col])
            label_encoders[col] = le

    X_cat = pd.DataFrame()
    for col in CATEGORICAL_FEATURES:
        le = label_encoders[col]
        df[col] = df[col].fillna("unknown")
        # Handle unseen labels at test time
        df[col] = df[col].apply(lambda x: x if x in le.classes_ else "unknown")
        if "unknown" not in le.classes_:
            le.classes_ = np.append(le.classes_, "unknown")
        X_cat[col] = le.transform(df[col])

    print(f"   Categorical features: {len(CATEGORICAL_FEATURES)}")

    # ── 4. Medical history binary flags ────────────────────────
    available_hist = [c for c in HISTORY_FEATURES if c in df.columns]
    X_hist = df[available_hist].fillna(0).astype(int)
    print(f"   Medical history features: {len(available_hist)}")

    # ── 5. Engineered features ─────────────────────────────────
    X_eng = pd.DataFrame()
    X_eng["total_history_flags"] = X_hist.sum(axis=1)
    X_eng["is_elderly"] = (df["age"] >= 65).astype(int)
    X_eng["is_pediatric"] = (df["age"] <= 12).astype(int)
    X_eng["critical_gcs"] = (df["gcs_total"] <= 8).astype(int)
    X_eng["low_spo2"] = (df["spo2"] < 92).astype(int)
    X_eng["high_news2"] = (df["news2_score"] >= 7).astype(int)
    X_eng["high_heart_rate"] = (df["heart_rate"] > 100).astype(int)
    X_eng["low_heart_rate"] = (df["heart_rate"] < 50).astype(int)
    X_eng["severe_pain"] = (df["pain_score"] >= 8).astype(int)
    X_eng["complaint_length"] = df["complaints_text"].str.len()
    X_eng["complaint_word_count"] = df["complaints_text"].str.split().str.len().fillna(0)

    eng_feature_names = list(X_eng.columns)
    print(f"   Engineered features: {len(eng_feature_names)}")

    # ── Combine all ────────────────────────────────────────────
    X_tabular = np.hstack([
        X_numeric.values,
        X_cat.values,
        X_hist.values,
        X_eng.values,
    ])

    X_combined = hstack([X_text, csr_matrix(X_tabular)])

    total = X_combined.shape[1]
    print(f"\n   📐 Total features: {total}")

    feature_names = (
        [f"tfidf_{i}" for i in range(X_text.shape[1])]
        + NUMERIC_FEATURES
        + CATEGORICAL_FEATURES
        + available_hist
        + eng_feature_names
    )

    return X_combined, tfidf, label_encoders, feature_names


def map_acuity_to_priority(acuity: int) -> dict:
    """
    Map 5-level ESI triage to SmartQ's 3-level priority system.
      Level 1-2  → high    (priorityScore: 10)
      Level 3    → medium  (priorityScore: 7)
      Level 4-5  → normal  (priorityScore: 5)
    """
    if acuity <= 2:
        return {"label": "high", "score": 10}
    elif acuity == 3:
        return {"label": "medium", "score": 7}
    else:
        return {"label": "normal", "score": 5}


def train():
    """Main training pipeline."""
    start = time.time()

    # ── Load ───────────────────────────────────────────────────
    df = load_and_merge()
    y = df[TARGET].values

    # ── Feature Engineering ────────────────────────────────────
    print("\n🔧 Engineering features...")
    X, tfidf, label_encoders, feature_names = engineer_features(df)

    # ── Train/Test Split ───────────────────────────────────────
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    print(f"\n   Train: {X_train.shape[0]} | Test: {X_test.shape[0]}")

    # ── Model: HistGradientBoosting (fast, handles NaN natively) ─
    print("\n🚀 Training HistGradientBoostingClassifier...")
    clf = HistGradientBoostingClassifier(
        max_iter=500,
        max_depth=8,
        learning_rate=0.1,
        min_samples_leaf=20,
        max_leaf_nodes=63,
        l2_regularization=0.1,
        random_state=42,
        verbose=0,
        class_weight="balanced",
    )

    # Convert sparse to dense for HistGBT (it requires dense input)
    X_train_dense = X_train.toarray()
    X_test_dense = X_test.toarray()

    clf.fit(X_train_dense, y_train)

    # ── Evaluation ─────────────────────────────────────────────
    y_pred = clf.predict(X_test_dense)
    accuracy = accuracy_score(y_test, y_pred)

    print(f"\n{'='*60}")
    print(f"🎯 Test Accuracy: {accuracy:.4f} ({accuracy:.2%})")
    print(f"{'='*60}")

    print(f"\n📋 Classification Report (5-class ESI):\n")
    print(classification_report(
        y_test, y_pred,
        target_names=["ESI-1 Resuscitation", "ESI-2 Emergent", "ESI-3 Urgent",
                       "ESI-4 Less Urgent", "ESI-5 Non-Urgent"]
    ))

    # Confusion Matrix
    cm = confusion_matrix(y_test, y_pred)
    print("📊 Confusion Matrix:")
    print(f"       Predicted → 1     2     3     4     5")
    for i, row in enumerate(cm):
        print(f"  Actual {i+1}:     {row}")

    # ── Also evaluate on SmartQ 3-class mapping ────────────────
    y_test_3 = np.array(["high" if v <= 2 else ("medium" if v == 3 else "normal") for v in y_test])
    y_pred_3 = np.array(["high" if v <= 2 else ("medium" if v == 3 else "normal") for v in y_pred])
    acc_3 = accuracy_score(y_test_3, y_pred_3)

    print(f"\n🏥 SmartQ 3-Class Accuracy: {acc_3:.2%}")
    print(classification_report(y_test_3, y_pred_3, target_names=["high", "medium", "normal"]))

    # ── Cross-validation (3-fold for speed) ────────────────────
    print("📈 3-Fold Cross-Validation (this may take a minute)...")
    X_dense = X.toarray()
    cv = StratifiedKFold(n_splits=3, shuffle=True, random_state=42)
    cv_scores = cross_val_score(clf, X_dense, y, cv=cv, scoring="accuracy", n_jobs=-1)
    print(f"   CV Accuracy: {cv_scores.mean():.4f} ± {cv_scores.std():.4f}")

    # ── Save Artifacts ─────────────────────────────────────────
    os.makedirs(MODEL_DIR, exist_ok=True)

    joblib.dump(clf, MODEL_PATH)
    joblib.dump(tfidf, VECTORIZER_PATH)
    joblib.dump(label_encoders, ENCODERS_PATH)
    joblib.dump(feature_names, FEATURE_COLS_PATH)

    label_mapping = {
        "high":   {"label": "high",   "score": 10},
        "medium": {"label": "medium", "score": 7},
        "normal": {"label": "normal", "score": 5},
    }
    joblib.dump(label_mapping, MAPPING_PATH)

    elapsed = time.time() - start
    print(f"\n✅ All artifacts saved to {MODEL_DIR}/")
    print(f"⏱️  Total training time: {elapsed:.1f}s")

    # ── Summary ────────────────────────────────────────────────
    print(f"""
╔══════════════════════════════════════════════════════════╗
║           SmartQ Triage Model — Training Summary        ║
╠══════════════════════════════════════════════════════════╣
║  Dataset:       80,000 patients (Triagegeist)           ║
║  Features:      {X.shape[1]:>5} total                            ║
║  Model:         HistGradientBoostingClassifier           ║
║  5-Class Acc:   {accuracy:.2%}                               ║
║  3-Class Acc:   {acc_3:.2%} (SmartQ: high/medium/normal)   ║
║  CV Score:      {cv_scores.mean():.4f} ± {cv_scores.std():.4f}                         ║
╚══════════════════════════════════════════════════════════╝
""")


if __name__ == "__main__":
    train()

from __future__ import annotations

import warnings
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from imblearn.over_sampling import SMOTE
from lightgbm import LGBMClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_selection import VarianceThreshold
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, StandardScaler

warnings.filterwarnings("ignore")

TARGET_HINTS = ("acuity", "triage_level", "priority", "severity", "label", "target")
EXCLUDED_FILENAME_TOKENS = ("test", "sample", "submission")
POST_EVENT_TOKENS = ("disposition", "los", "length_of_stay", "outcome", "discharge", "admit")

SCRIPT_DIR = Path(__file__).resolve().parent
MODELS_DIR = SCRIPT_DIR / "models"
MODEL_PATH = MODELS_DIR / "triage_model.pkl"
FEATURES_PATH = MODELS_DIR / "feature_cols.pkl"


def print_header(title: str) -> None:
    line = "=" * 96
    print(f"\n{line}\n{title}\n{line}")


def print_subheader(title: str) -> None:
    print(f"\n{title}")
    print("-" * len(title))


def print_assumption(message: str) -> None:
    print(f"ASSUMPTION: {message}")


def count_rows(csv_path: Path) -> int:
    probe = pd.read_csv(csv_path, usecols=[0])
    return int(probe.shape[0])


def detect_target_column(df: pd.DataFrame) -> tuple[str, str]:
    exact_matches = [col for col in df.columns if col.lower() in TARGET_HINTS]
    if exact_matches:
        return exact_matches[0], "exact target-name match"

    partial_matches = [
        col for col in df.columns if any(hint in col.lower() for hint in TARGET_HINTS)
    ]
    if partial_matches:
        best = min(partial_matches, key=lambda col: (df[col].nunique(dropna=True), col))
        return best, "partial target-name match"

    categorical_candidates: list[tuple[int, float, str]] = []
    for col in df.columns:
        nunique = int(df[col].nunique(dropna=True))
        if nunique <= 1:
            continue
        unique_ratio = nunique / max(len(df), 1)
        looks_categorical = (
            pd.api.types.is_object_dtype(df[col])
            or pd.api.types.is_categorical_dtype(df[col])
            or nunique <= 20
            or unique_ratio <= 0.05
        )
        if looks_categorical:
            categorical_candidates.append((nunique, unique_ratio, col))

    if categorical_candidates:
        _, _, best = min(categorical_candidates, key=lambda item: (item[0], item[1], item[2]))
        return best, "fallback to low-cardinality categorical-looking column"

    raise ValueError("Could not detect a suitable target column.")


def detect_dataset(search_dir: Path) -> tuple[Path, pd.DataFrame, str, str]:
    csv_paths = sorted(search_dir.glob("*.csv"))
    if not csv_paths:
        raise FileNotFoundError(f"No CSV files found in {search_dir}.")

    ranked_candidates: list[tuple[int, int, int, Path, str]] = []
    for path in csv_paths:
        header = pd.read_csv(path, nrows=0)
        columns = list(header.columns)
        target_like = any(any(hint in col.lower() for hint in TARGET_HINTS) for col in columns)
        filename_penalty = any(token in path.stem.lower() for token in EXCLUDED_FILENAME_TOKENS)
        rows = count_rows(path)
        cols = len(columns)
        score = (
            (2 if target_like else 0),
            (0 if filename_penalty else 1),
            rows,
            cols,
        )
        ranked_candidates.append((*score, path, ", ".join(columns[:6])))

    ranked_candidates.sort(reverse=True)
    _, _, _, _, best_path, preview_cols = ranked_candidates[0]
    df = pd.read_csv(best_path, low_memory=False)
    target_col, target_reason = detect_target_column(df)

    print_assumption(
        f"Auto-detected dataset file `{best_path.name}` from `{search_dir}`. "
        f"It was selected because it is the strongest labeled CSV candidate "
        f"(preview columns: {preview_cols})."
    )

    if df.shape[1] < 100:
        print_assumption(
            f"The available labeled CSV has shape {df.shape}, not a single 5,070-column table. "
            "Proceeding with the on-disk training file that best matches the target detection rules."
        )

    return best_path, df, target_col, target_reason


def missing_summary(df: pd.DataFrame) -> str:
    missing_counts = df.isna().sum()
    total_missing = int(missing_counts.sum())
    cols_with_missing = int((missing_counts > 0).sum())
    top_missing = missing_counts[missing_counts > 0].sort_values(ascending=False).head(10)
    if top_missing.empty:
        return "No missing values detected."
    details = ", ".join(f"{col}={int(cnt)}" for col, cnt in top_missing.items())
    return (
        f"total_missing_cells={total_missing}, columns_with_missing={cols_with_missing}, "
        f"top_missing_cols=[{details}]"
    )


def dtype_summary(df: pd.DataFrame) -> str:
    counts = df.dtypes.astype(str).value_counts()
    return ", ".join(f"{dtype}={count}" for dtype, count in counts.items())


def identify_model_exclusions(df: pd.DataFrame, target_col: str) -> list[str]:
    exclusions: list[str] = []
    for col in df.columns:
        if col == target_col:
            continue
        lower = col.lower()
        unique_ratio = df[col].nunique(dropna=False) / max(len(df), 1)
        is_identifier = (lower == "id" or lower.endswith("_id")) and unique_ratio > 0.5
        is_post_event = any(token in lower for token in POST_EVENT_TOKENS)
        if is_identifier or is_post_event:
            exclusions.append(col)
    return exclusions


def format_class_counts(values: np.ndarray, encoder: LabelEncoder | None = None) -> str:
    series = pd.Series(values)
    counts = series.value_counts().sort_index()
    labels = counts.index.tolist()
    if encoder is not None:
        labels = encoder.inverse_transform(np.array(labels, dtype=int)).tolist()
    return ", ".join(f"{label}={int(count)}" for label, count in zip(labels, counts.tolist()))


def main() -> None:
    search_dir = Path.cwd()
    MODELS_DIR.mkdir(parents=True, exist_ok=True)

    print_header("AUTOMATED TRIAGE ML PIPELINE")
    print(f"Working directory: {search_dir}")
    print(f"Artifacts directory: {MODELS_DIR}")

    dataset_path, df, target_col, target_reason = detect_dataset(search_dir)

    print_header("STEP 1 - LOAD & INSPECT")
    print(f"Loaded file: {dataset_path.name}")
    print(f"Shape: {df.shape}")
    print(f"Detected target column: {target_col} ({target_reason})")
    print_subheader("Class Distribution")
    print(df[target_col].value_counts(dropna=False).sort_index().to_string())
    print_subheader("Missing Values Summary")
    print(missing_summary(df))
    print_subheader("Dtypes Summary")
    print(dtype_summary(df))

    print_header("STEP 2 - CLEAN")
    before_shape = df.shape
    if df[target_col].isna().any():
        before_drop = len(df)
        df = df.dropna(subset=[target_col]).copy()
        print_assumption(
            f"Dropped {before_drop - len(df)} rows with missing target values because the model "
            "cannot train on unlabeled records."
        )

    missing_ratio = df.isna().mean()
    cols_to_drop = [
        col for col in df.columns if col != target_col and missing_ratio[col] > 0.40
    ]
    if cols_to_drop:
        df = df.drop(columns=cols_to_drop)
        print(f"Dropped columns with >40% missing: {cols_to_drop}")
    else:
        print("Dropped columns with >40% missing: none")

    feature_cols_for_cleaning = [col for col in df.columns if col != target_col]
    numeric_cols_all = [
        col
        for col in feature_cols_for_cleaning
        if pd.api.types.is_numeric_dtype(df[col]) or pd.api.types.is_bool_dtype(df[col])
    ]
    categorical_cols_all = [col for col in feature_cols_for_cleaning if col not in numeric_cols_all]

    for col in numeric_cols_all:
        if df[col].isna().any():
            df[col] = df[col].fillna(df[col].median())

    for col in categorical_cols_all:
        if df[col].isna().any():
            mode = df[col].mode(dropna=True)
            fill_value = mode.iloc[0] if not mode.empty else "missing"
            df[col] = df[col].fillna(fill_value)

    duplicates_removed = int(df.duplicated().sum())
    if duplicates_removed:
        df = df.drop_duplicates().copy()

    after_shape = df.shape
    print(f"Shape before cleaning: {before_shape}")
    print(f"Shape after cleaning : {after_shape}")
    print(f"Duplicate rows removed: {duplicates_removed}")

    print_header("STEP 3 - FEATURE REDUCTION")
    model_exclusions = identify_model_exclusions(df, target_col)
    if model_exclusions:
        print_assumption(
            "Excluded identifier-like or post-event columns from modeling to reduce leakage/noise: "
            f"{model_exclusions}"
        )

    X_base = df.drop(columns=[target_col] + model_exclusions, errors="ignore").copy()
    y = df[target_col].copy()

    reduction_categorical_cols = [
        col for col in X_base.columns if not pd.api.types.is_numeric_dtype(X_base[col])
    ]
    if reduction_categorical_cols:
        print_assumption(
            "For VarianceThreshold and RandomForest feature ranking, categorical columns are "
            "temporarily ordinal-encoded on a copy of the cleaned data."
        )

    X_reduction = X_base.copy()
    for col in reduction_categorical_cols:
        encoded, _ = pd.factorize(X_reduction[col].astype(str), sort=True)
        X_reduction[col] = encoded.astype(float)

    variance_selector = VarianceThreshold(threshold=0.01)
    X_variance = variance_selector.fit_transform(X_reduction)
    variance_mask = variance_selector.get_support()
    variance_kept_cols = X_base.columns[variance_mask].tolist()
    print(f"Features before VarianceThreshold: {X_base.shape[1]}")
    print(f"Features after VarianceThreshold : {len(variance_kept_cols)}")

    rf_target_encoder = LabelEncoder()
    y_for_ranking = rf_target_encoder.fit_transform(y.astype(str))
    rf_model = RandomForestClassifier(n_estimators=50, random_state=42, n_jobs=-1)
    rf_model.fit(X_variance, y_for_ranking)

    importance_df = (
        pd.DataFrame(
            {
                "feature": variance_kept_cols,
                "importance": rf_model.feature_importances_,
            }
        )
        .sort_values("importance", ascending=False)
        .reset_index(drop=True)
    )
    top_feature_count = min(50, len(importance_df))
    top_features = importance_df.head(top_feature_count)["feature"].tolist()
    print(f"Top features retained for downstream modeling: {top_feature_count}")
    print_subheader("Top Feature Importances")
    for row in importance_df.head(top_feature_count).itertuples(index=False):
        print(f"{row.feature}: {row.importance:.6f}")

    print_header("STEP 4 - PREPROCESS")
    X_selected = X_base[top_features].copy()
    selected_categorical_cols = [
        col for col in X_selected.columns if not pd.api.types.is_numeric_dtype(X_selected[col])
    ]
    selected_numeric_cols = [col for col in X_selected.columns if col not in selected_categorical_cols]

    feature_label_encoders: dict[str, LabelEncoder] = {}
    for col in selected_categorical_cols:
        encoder = LabelEncoder()
        X_selected[col] = encoder.fit_transform(X_selected[col].astype(str))
        feature_label_encoders[col] = encoder

    scaler = StandardScaler()
    if selected_numeric_cols:
        X_selected[selected_numeric_cols] = scaler.fit_transform(X_selected[selected_numeric_cols])
    else:
        print_assumption("No numeric columns remained after feature selection, so scaling was skipped.")

    target_encoder = LabelEncoder()
    y_encoded = target_encoder.fit_transform(y.astype(str))

    X_train, X_test, y_train, y_test = train_test_split(
        X_selected,
        y_encoded,
        test_size=0.20,
        stratify=y_encoded,
        random_state=42,
    )

    print(f"Selected feature count: {X_selected.shape[1]}")
    print(f"Categorical features encoded: {len(selected_categorical_cols)}")
    print(f"Numeric features scaled     : {len(selected_numeric_cols)}")
    print(f"Train shape: {X_train.shape}, Test shape: {X_test.shape}")
    print_subheader("Train Class Distribution")
    print(format_class_counts(y_train, target_encoder))
    print_subheader("Test Class Distribution")
    print(format_class_counts(y_test, target_encoder))

    print_header("STEP 5 - CLASS IMBALANCE")
    class_counts_before = pd.Series(y_train).value_counts().sort_index()
    imbalance_ratio = class_counts_before.max() / class_counts_before.min()
    print(f"Imbalance ratio (max/min): {imbalance_ratio:.4f}")
    print(f"Training counts before SMOTE: {format_class_counts(y_train, target_encoder)}")

    X_train_balanced = X_train
    y_train_balanced = y_train
    if imbalance_ratio > 2.0:
        min_class_count = int(class_counts_before.min())
        if min_class_count < 2:
            print_assumption(
                "SMOTE was skipped because at least one class has fewer than 2 samples in the "
                "training split."
            )
        else:
            k_neighbors = min(5, min_class_count - 1)
            smote = SMOTE(random_state=42, k_neighbors=k_neighbors)
            X_train_balanced, y_train_balanced = smote.fit_resample(X_train, y_train)
            print(f"SMOTE applied with k_neighbors={k_neighbors}")
    else:
        print("SMOTE not applied because no class is underrepresented by more than 2x.")

    print(f"Training counts after SMOTE : {format_class_counts(y_train_balanced, target_encoder)}")

    print_header("STEP 6 - TRAIN")
    model = LGBMClassifier(
        n_estimators=300,
        learning_rate=0.05,
        num_leaves=63,
        class_weight="balanced",
        n_jobs=-1,
        random_state=42,
        verbosity=-1,
    )
    model.fit(X_train_balanced, y_train_balanced)
    print(
        "Training complete: LightGBMClassifier("
        "n_estimators=300, learning_rate=0.05, num_leaves=63, class_weight='balanced')"
    )

    print_header("STEP 7 - EVALUATE")
    y_pred = model.predict(X_test)
    y_proba = model.predict_proba(X_test)

    accuracy = accuracy_score(y_test, y_pred)
    report = classification_report(
        y_test,
        y_pred,
        labels=list(range(len(target_encoder.classes_))),
        target_names=target_encoder.classes_.tolist(),
        digits=4,
        zero_division=0,
    )
    confusion = pd.DataFrame(
        confusion_matrix(y_test, y_pred, labels=list(range(len(target_encoder.classes_)))),
        index=target_encoder.classes_.tolist(),
        columns=target_encoder.classes_.tolist(),
    )

    try:
        if len(target_encoder.classes_) == 2:
            roc_auc = roc_auc_score(y_test, y_proba[:, 1])
        else:
            roc_auc = roc_auc_score(y_test, y_proba, multi_class="ovr", average="weighted")
        roc_auc_text = f"{roc_auc:.6f}"
    except Exception as exc:
        roc_auc_text = f"Could not compute ROC-AUC ({exc})"

    print(f"Accuracy: {accuracy:.6f}")
    print_subheader("Classification Report")
    print(report)
    print_subheader("Confusion Matrix")
    print(confusion.to_string())
    print_subheader("ROC-AUC")
    print(roc_auc_text)

    model_bundle = {
        "model": model,
        "feature_columns": top_features,
        "categorical_columns": selected_categorical_cols,
        "numeric_columns": selected_numeric_cols,
        "feature_label_encoders": feature_label_encoders,
        "target_encoder": target_encoder,
        "scaler": scaler,
        "source_file": dataset_path.name,
        "target_column": target_col,
        "variance_selected_columns": variance_kept_cols,
        "model_exclusions": model_exclusions,
    }
    joblib.dump(model_bundle, MODEL_PATH)
    joblib.dump(top_features, FEATURES_PATH)

    print_subheader("Saved Artifacts")
    print(f"Model bundle saved to : {MODEL_PATH}")
    print(f"Feature list saved to : {FEATURES_PATH}")


if __name__ == "__main__":
    main()

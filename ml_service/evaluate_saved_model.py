from __future__ import annotations

from pathlib import Path

import joblib
import matplotlib
import numpy as np
import pandas as pd
from imblearn.over_sampling import SMOTE
from lightgbm import LGBMClassifier
from sklearn.metrics import accuracy_score, confusion_matrix
from sklearn.model_selection import train_test_split

matplotlib.use("Agg")
import matplotlib.pyplot as plt


BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
MODELS_DIR = BASE_DIR / "models"
MODEL_PATH = MODELS_DIR / "triage_model.pkl"
FEATURES_PATH = MODELS_DIR / "feature_cols.pkl"
CONFUSION_PLOT_PATH = MODELS_DIR / "confusion_matrix.png"
IMPORTANCE_PLOT_PATH = MODELS_DIR / "feature_importance.png"


def print_header(title: str) -> None:
    line = "=" * 96
    print(f"\n{line}\n{title}\n{line}")


def load_artifacts() -> tuple[dict, list[str]]:
    bundle = joblib.load(MODEL_PATH)
    features = joblib.load(FEATURES_PATH)
    if bundle["feature_columns"] != features:
        raise ValueError("feature_cols.pkl does not match the feature list stored in triage_model.pkl")
    return bundle, features


def clean_dataframe(df: pd.DataFrame, target_col: str) -> pd.DataFrame:
    cleaned = df.copy()
    if cleaned[target_col].isna().any():
        cleaned = cleaned.dropna(subset=[target_col]).copy()

    missing_ratio = cleaned.isna().mean()
    cols_to_drop = [
        col for col in cleaned.columns if col != target_col and missing_ratio[col] > 0.40
    ]
    cleaned = cleaned.drop(columns=cols_to_drop)

    feature_cols = [col for col in cleaned.columns if col != target_col]
    numeric_cols = [
        col
        for col in feature_cols
        if pd.api.types.is_numeric_dtype(cleaned[col]) or pd.api.types.is_bool_dtype(cleaned[col])
    ]
    categorical_cols = [col for col in feature_cols if col not in numeric_cols]

    for col in numeric_cols:
        if cleaned[col].isna().any():
            cleaned[col] = cleaned[col].fillna(cleaned[col].median())

    for col in categorical_cols:
        if cleaned[col].isna().any():
            mode = cleaned[col].mode(dropna=True)
            fill_value = mode.iloc[0] if not mode.empty else "missing"
            cleaned[col] = cleaned[col].fillna(fill_value)

    cleaned = cleaned.drop_duplicates().copy()
    return cleaned


def prepare_model_frame(df: pd.DataFrame, bundle: dict, feature_columns: list[str]) -> tuple[pd.DataFrame, np.ndarray]:
    target_col = bundle["target_column"]
    working = clean_dataframe(df, target_col)
    working = working.drop(columns=bundle.get("model_exclusions", []), errors="ignore")

    X = working[feature_columns].copy()
    for col, encoder in bundle["feature_label_encoders"].items():
        raw_values = X[col].astype(str)
        unseen = sorted(set(raw_values.unique()) - set(encoder.classes_))
        if unseen:
            raise ValueError(f"Unseen categories in column {col}: {unseen[:5]}")
        X[col] = encoder.transform(raw_values)

    numeric_cols = bundle["numeric_columns"]
    if numeric_cols:
        X[numeric_cols] = bundle["scaler"].transform(X[numeric_cols])

    y = bundle["target_encoder"].transform(working[target_col].astype(str))
    return X, y


def split_data(X: pd.DataFrame, y: np.ndarray, random_state: int = 42):
    indices = np.arange(len(X))
    return train_test_split(
        X,
        y,
        indices,
        test_size=0.20,
        stratify=y,
        random_state=random_state,
    )


def maybe_apply_smote(X_train: pd.DataFrame, y_train: np.ndarray):
    class_counts = pd.Series(y_train).value_counts().sort_index()
    imbalance_ratio = class_counts.max() / class_counts.min()
    if imbalance_ratio <= 2.0:
        return X_train, y_train, False, imbalance_ratio

    min_class_count = int(class_counts.min())
    if min_class_count < 2:
        return X_train, y_train, False, imbalance_ratio

    k_neighbors = min(5, min_class_count - 1)
    smote = SMOTE(random_state=42, k_neighbors=k_neighbors)
    X_resampled, y_resampled = smote.fit_resample(X_train, y_train)
    return X_resampled, y_resampled, True, imbalance_ratio


def save_confusion_matrix_plot(cm: np.ndarray, labels: list[str]) -> None:
    plt.figure(figsize=(7.5, 6))
    plt.imshow(cm, interpolation="nearest", cmap="Blues")
    plt.title("Confusion Matrix")
    plt.colorbar()
    ticks = np.arange(len(labels))
    plt.xticks(ticks, labels)
    plt.yticks(ticks, labels)
    plt.xlabel("Predicted label")
    plt.ylabel("Actual label")

    threshold = cm.max() / 2 if cm.size else 0
    for row in range(cm.shape[0]):
        for col in range(cm.shape[1]):
            color = "white" if cm[row, col] > threshold else "black"
            plt.text(col, row, f"{cm[row, col]}", ha="center", va="center", color=color)

    plt.tight_layout()
    plt.savefig(CONFUSION_PLOT_PATH, dpi=200, bbox_inches="tight")
    plt.close()


def save_feature_importance_plot(model, feature_columns: list[str]) -> None:
    importance = pd.DataFrame(
        {"feature": feature_columns, "importance": model.feature_importances_}
    ).sort_values("importance", ascending=False)
    top20 = importance.head(20).sort_values("importance", ascending=True)

    plt.figure(figsize=(10, 8))
    plt.barh(top20["feature"], top20["importance"], color="#2c7fb8")
    plt.title("Top 20 Feature Importances")
    plt.xlabel("Importance")
    plt.ylabel("Feature")
    plt.tight_layout()
    plt.savefig(IMPORTANCE_PLOT_PATH, dpi=200, bbox_inches="tight")
    plt.close()


def print_sample_predictions(
    model,
    X_test: pd.DataFrame,
    y_test: np.ndarray,
    test_indices: np.ndarray,
    target_encoder,
) -> None:
    sample_positions = (
        pd.Series(np.arange(len(X_test)))
        .sample(n=min(3, len(X_test)), random_state=42, replace=False)
        .sort_values()
        .tolist()
    )
    X_sample = X_test.iloc[sample_positions]
    y_sample = y_test[sample_positions]
    sample_indices = test_indices[sample_positions]
    pred_encoded = model.predict(X_sample)
    pred_proba = model.predict_proba(X_sample)
    class_labels = target_encoder.classes_.tolist()

    for idx_in_batch, original_index in enumerate(sample_indices):
        actual = target_encoder.inverse_transform([y_sample[idx_in_batch]])[0]
        predicted = target_encoder.inverse_transform([pred_encoded[idx_in_batch]])[0]
        probability_map = ", ".join(
            f"{label}={pred_proba[idx_in_batch][label_idx]:.4f}"
            for label_idx, label in enumerate(class_labels)
        )
        print(f"Test row index: {int(original_index)}")
        print(f"Actual label   : {actual}")
        print(f"Predicted label: {predicted}")
        print(f"Class confidence scores: {probability_map}")
        print("")


def main() -> None:
    bundle, feature_columns = load_artifacts()
    data_path = DATA_DIR / bundle["source_file"]

    print_header("LOAD SAVED MODEL")
    print(f"Model path   : {MODEL_PATH}")
    print(f"Feature path : {FEATURES_PATH}")
    print(f"Dataset path : {data_path}")
    print(f"Target column: {bundle['target_column']}")
    print(f"Feature count: {len(feature_columns)}")

    df = pd.read_csv(data_path, low_memory=False)
    X, y = prepare_model_frame(df, bundle, feature_columns)
    X_train, X_test, y_train, y_test, _, test_indices = split_data(X, y)

    base_model = bundle["model"]
    base_predictions = base_model.predict(X_test)
    base_probabilities = base_model.predict_proba(X_test)
    base_accuracy = accuracy_score(y_test, base_predictions)
    class_labels = bundle["target_encoder"].classes_.tolist()
    cm = confusion_matrix(y_test, base_predictions, labels=np.arange(len(class_labels)))

    print_header("1. CONFUSION MATRIX")
    save_confusion_matrix_plot(cm, class_labels)
    print(f"Saved confusion matrix plot to {CONFUSION_PLOT_PATH}")

    print_header("2. FEATURE IMPORTANCE")
    save_feature_importance_plot(base_model, feature_columns)
    print(f"Saved top-20 feature importance plot to {IMPORTANCE_PLOT_PATH}")

    print_header("3. LIGHTGBM TUNING")
    X_train_balanced, y_train_balanced, smote_applied, imbalance_ratio = maybe_apply_smote(
        X_train, y_train
    )
    print(f"Baseline accuracy: {base_accuracy:.6f}")
    print(f"Training imbalance ratio before tuning: {imbalance_ratio:.4f}")
    print(f"SMOTE applied for tuned run: {'yes' if smote_applied else 'no'}")

    tuned_model = LGBMClassifier(
        n_estimators=500,
        learning_rate=0.03,
        num_leaves=127,
        min_child_samples=20,
        class_weight="balanced",
        n_jobs=-1,
        random_state=42,
        verbosity=-1,
    )
    tuned_model.fit(X_train_balanced, y_train_balanced)
    tuned_predictions = tuned_model.predict(X_test)
    tuned_accuracy = accuracy_score(y_test, tuned_predictions)
    delta = tuned_accuracy - base_accuracy
    print(
        "Tuned params: n_estimators=500, learning_rate=0.03, "
        "num_leaves=127, min_child_samples=20"
    )
    print(f"Tuned accuracy   : {tuned_accuracy:.6f}")
    print(f"Accuracy delta   : {delta:+.6f}")
    print(f"Accuracy improved: {'yes' if delta > 0 else 'no'}")

    print_header("4. SAMPLE PREDICTIONS")
    print_sample_predictions(base_model, X_test, y_test, test_indices, bundle["target_encoder"])


if __name__ == "__main__":
    main()

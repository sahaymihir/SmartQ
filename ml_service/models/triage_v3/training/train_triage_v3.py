"""
Triage Model v3 Training Pipeline

Trains XGBoost multiclass classifier to predict KTAS priority class
from patient vitals, symptoms, and risk factors.

Usage:
    python train_triage_v3.py --data-dir datasets/ --output-dir model/
"""

import argparse
import logging
from pathlib import Path

logger = logging.getLogger("smartq-ml.triage")

def train_triage_model(data_dir: Path, output_dir: Path):
    """
    Train the triage model.
    
    Args:
        data_dir: Directory containing train.csv, chief_complaints.csv, patient_history.csv
        output_dir: Directory to save model artifacts (pkl files)
    """
    logger.info("Triage model training pipeline")
    logger.info(f"  Data directory: {data_dir}")
    logger.info(f"  Output directory: {output_dir}")
    
    # This script is a placeholder for the full training pipeline.
    # The current production model (triage_model_v3.pkl) was generated
    # by auto_ml_pipeline_v3.py in the parent ml_service/ directory.
    #
    # To retrain:
    # 1. Place train.csv, chief_complaints.csv, patient_history.csv in {data_dir}
    # 2. Run: python train_triage_v3.py --data-dir {data_dir} --output-dir {output_dir}
    #
    # Steps:
    # - Load and preprocess datasets
    # - Engineer features (shock_index, multi_risk_flag, etc.)
    # - Apply SMOTE for class balancing
    # - Hyperparameter tuning
    # - Train XGBoost classifier
    # - Save model artifacts
    # - Generate evaluation report

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train triage model v3")
    parser.add_argument("--data-dir", type=Path, default="datasets/", help="Directory with training data")
    parser.add_argument("--output-dir", type=Path, default="model/", help="Directory to save artifacts")
    args = parser.parse_args()
    
    logging.basicConfig(level=logging.INFO)
    train_triage_model(args.data_dir, args.output_dir)

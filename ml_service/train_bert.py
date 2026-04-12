import os
import random
import torch
import numpy as np
import pandas as pd
from datasets import Dataset
from transformers import (
    DistilBertTokenizerFast,
    DistilBertForSequenceClassification,
    Trainer,
    TrainingArguments
)
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score

def set_seed(seed=42):
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)

def main():
    print("🚀 Initializing Semantic AI Triage Training (Deep Learning)")
    set_seed()
    
    data_path = './data/chief_complaints.csv'
    model_dir = './models/bert_triage'
    os.makedirs(model_dir, exist_ok=True)
    
    df = pd.read_csv(data_path)
    df = df.dropna(subset=['chief_complaint_raw', 'chief_complaint_system'])
    
    unique_classes = sorted(df['chief_complaint_system'].unique().tolist())
    num_labels = len(unique_classes)
    
    label2id = {label: i for i, label in enumerate(unique_classes)}
    id2label = {i: label for i, label in enumerate(unique_classes)}
    
    df['label'] = df['chief_complaint_system'].map(label2id)
    
    # 5K subset for CPU feasibility
    try:
        sample_df, _ = train_test_split(df, train_size=5000, stratify=df['label'], random_state=42)
    except ValueError:
        sample_df = df.sample(5000, random_state=42)
        
    print(f"📊 Extracted {len(sample_df)} records for rapid fine-tuning.")
    
    train_df, val_df = train_test_split(sample_df, test_size=0.2, random_state=42, stratify=sample_df['label'])
    
    model_name = "distilbert-base-uncased"
    tokenizer = DistilBertTokenizerFast.from_pretrained(model_name)
    model = DistilBertForSequenceClassification.from_pretrained(
        model_name, 
        num_labels=num_labels,
        id2label=id2label,
        label2id=label2id
    )
    
    def tokenize_data(texts, labels):
        encodings = tokenizer(texts.tolist(), truncation=True, padding=True, max_length=128)
        return Dataset.from_dict({
            'input_ids': encodings['input_ids'],
            'attention_mask': encodings['attention_mask'],
            'labels': labels.tolist()
        })
        
    train_dataset = tokenize_data(train_df['chief_complaint_raw'], train_df['label'])
    val_dataset = tokenize_data(val_df['chief_complaint_raw'], val_df['label'])
    
    def compute_metrics(pred):
        labels = pred.label_ids
        preds = pred.predictions.argmax(-1)
        return {'accuracy': accuracy_score(labels, preds)}

    # Fixed deprecated HF parameter 'evaluation_strategy' -> 'eval_strategy'
    training_args = TrainingArguments(
        output_dir='./results',
        num_train_epochs=2,
        per_device_train_batch_size=16,
        per_device_eval_batch_size=32,
        eval_strategy="epoch",  
        logging_dir='./logs',
        logging_steps=50,
        save_strategy="epoch",
        load_best_model_at_end=True,
    )
    
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=train_dataset,
        eval_dataset=val_dataset,
        compute_metrics=compute_metrics,
    )
    
    print("🧠 Starting Fine-Tuning... (This might take a few minutes)")
    trainer.train()
    
    print("✅ Training complete. Saving Deep Learning Model...")
    model.save_pretrained(model_dir)
    tokenizer.save_pretrained(model_dir)
    print(f"💾 Model securely saved to {model_dir}")

if __name__ == '__main__':
    main()

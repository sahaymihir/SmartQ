import os
import sys
import torch
from transformers import pipeline

def main():
    model_dir = './models/bert_triage'
    
    if not os.path.exists(model_dir):
        print("❌ Error: Deep Learning Model not found. Please run train_bert.py first!")
        sys.exit(1)
        
    print("🧠 Loading Semantic AI Triage Model (HuggingFace Transformers)...")
    
    triage_pipe = pipeline(
        "text-classification", 
        model=model_dir, 
        tokenizer=model_dir,
        device=-1 # CPU
    )
    
    print("✅ Model loaded successfully.\n")
    print("=" * 60)
    print("🩺 SMART-Q DEEP LEARNING DIAGNOSTIC TRIAGE")
    print("Type exact symptoms, synonyms, or full conversational descriptions.")
    print("Type 'exit' or 'quit' to close.")
    print("=" * 60)
    
    while True:
        symptoms = input("\n📝 Patient Symptoms: ")
        
        if symptoms.lower() in ['exit', 'quit']:
            print("Goodbye! 👋")
            break
            
        if not symptoms.strip():
            continue
            
        prediction = triage_pipe(symptoms)[0]
        department = prediction['label']
        confidence = prediction['score'] * 100
        
        print("\n" + "-" * 40)
        print(f"🏥 Recommended Department : {department.upper()}")
        print(f"📊 Semantic Confidence    : {confidence:.2f}%")
        print("-" * 40)

if __name__ == '__main__':
    main()

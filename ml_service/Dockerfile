FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app/ml_service

COPY ml_service/requirements.txt ./requirements.txt

RUN pip install --upgrade pip && \
    pip install -r requirements.txt

RUN mkdir -p models

COPY ml_service/main.py ./main.py
COPY ml_service/README.md ./README.md
COPY ml_service/models/triage_model_v3.pkl ./models/triage_model_v3.pkl
COPY ml_service/models/feature_cols_v3.pkl ./models/feature_cols_v3.pkl
COPY ml_service/models/scaler_v3.pkl ./models/scaler_v3.pkl

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

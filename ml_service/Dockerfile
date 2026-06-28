FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app/ml_service

COPY ml_service/requirements.txt ./requirements.txt

RUN pip install --upgrade pip && \
    pip install -r requirements.txt

RUN mkdir -p models/triage_v3/model

COPY ml_service/main.py ./main.py
COPY ml_service/specialty_hybrid.py ./specialty_hybrid.py
COPY ml_service/README.md ./README.md
COPY ml_service/models/triage_v3/model/ ./models/triage_v3/model/
COPY ml_service/static/ ./static/

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]

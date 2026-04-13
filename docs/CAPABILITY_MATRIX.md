# SmartQ Capability Matrix

## Live

- Java/XML Android client for patient, nurse, doctor, admin, and superuser flows
- JWT auth with role-based routing and biometric sign-in support on Android
- Patient intake, doctor suggestion flow, queue join, leave queue, snooze, and manual check-in
- Nurse vitals submission with re-triage, safety-rule escalation, and doctor-queue handoff
- Doctor/admin queue management, call-next, no-show, pause/resume, and prescription workflow
- FastAPI `POST /predict` triage predictor backed by the saved XGBoost bundle in `ml_service/models/triage_v3/model/`
- Rule-assisted specialty routing and rule-based test recommendation generation
- Queue ETA based on moving averages from completed consultations
- Demo seed data and consultation history views

## Demo-Only / Session-Scoped

- ML diagnostics and model-eval event history that are stored for the current seeded/demo state rather than treated as long-term clinical records
- Seed/reset flows intended for presentation resets rather than production operations

## Scaffolded / In Progress

- Firebase push registration and queue-event hooks
- OCR prescription extraction when an external `OCR_SERVICE_URL` is available; otherwise placeholder extraction is used
- Geofence-based automatic check-in
- Compose/Kotlin prototype screens that are not part of the supported live Android path

## Future

- ETA prediction driven by a dedicated wait-time ML model instead of moving-average timing
- Fully verified end-to-end FCM notification rollout for all queue events
- WebSocket or Socket.IO realtime updates instead of polling
- Hardened Android token storage and refresh-token auth flow

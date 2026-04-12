# Clinical Flow — Trauma Example

> **Scenario**: Child, age 5, brought in with broken right/left leg, hand, and skull. Complaint: trauma.

---

### 🟦 Patient

**What the patient (or guardian) provides at intake:**

```
Symptoms typed:  "broken right and left leg and hand and skull"
Age:             5
Chief complaint: Injury / bones  →  trauma
```

**What the Patient panel shows after the AI runs:**

| Field | Value |
|---|---|
| What the system understood | broken right and left leg and hand and skull |
| Estimated wait time | **Seen immediately at Orthopaedics** (wait = 0 m) |

---

### 🟩 Nurse / Triage

| Field | Value |
|---|---|
| Priority Assessment | **Urgent priority  •  raw KTAS 3  •  final KTAS 3  •  score 7  •  100% ML confidence** |
| Priority breakdown | complaint trauma  •  age 5  •  triage 7  •  ml_v3  •  Urgent |
| Safety Rules | No hard safety override fired |
| Queue Assignment | Orthopaedics  •  primary  •  queue 0  •  doctors 1  •  wait 0m |
| Rationale | Selected Orthopaedics using route hint; queue=0, doctors=1, estimated wait=0m |

---

### 🟥 Doctor Clinical

| Field | Value |
|---|---|
| Likely department | **Orthopaedics  •  33% route confidence  •  Low confidence** |
| Assigned queue | Orthopaedics  •  manual review recommended |
| Key signals detected | trauma complaint, severe pain score |
| Top department matches | Orthopaedics 56% ← trauma complaint · Emergency Medicine 35% ← trauma complaint, severe pain score · General Practice 9% |
| Recommended Doctor | **Dr. Rajesh Patel (Orthopaedics)** |
| Suggested Tests | `immediate` Full trauma series X-rays — identify fractures and internal injury |
| | `immediate` FAST ultrasound — detect haemoperitoneum |
| | `urgent` CBC + coagulation screen — baseline haematology post-trauma |
| | `urgent` Serum lactate — assess tissue perfusion in severe pain states |
| Reasoning | Primary clinical fit is Orthopaedics based on signals like trauma complaint, severe pain score. SmartQ can route directly to Orthopaedics. Symptom overlap is high, so manual review or doctor override is recommended. |
| Sources | patient_flow_v1 · specialty_hybrid_v1 · ml_v3 · rule_based_v1 |

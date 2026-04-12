package com.example.smartqueue.models.request;

/**
 * Request body for joining a queue with ML triage.
 *
 * Extended for multimodal symptom intake:
 *   - symptomsText:             free-text typed by the patient
 *   - symptomsVoiceTranscript:  STT transcript from the voice input path
 *   - intakeLanguage:           BCP-47 language tag (e.g. "en", "hi")
 *
 * The legacy `symptoms` field is retained for backwards compatibility with
 * older server versions. New backend code normalises all three fields into
 * a single canonical symptom string.
 *
 * Optional clinical vitals fields (pain_score, spo2, etc.) follow the same
 * names used by the ML triage service so they are forwarded transparently.
 */
public class JoinQueueRequest {
    // Legacy field — kept for backward compatibility
    private String symptoms;

    // Multimodal intake (v3+)
    private String symptomsText;
    private String symptomsVoiceTranscript;
    private String intakeLanguage;

    // Optional structured vitals forwarded to the triage model
    private Float pain_score;
    private Float spo2;
    private Float temperature_c;
    private Float heart_rate;
    private Float systolic_bp;
    private Float diastolic_bp;
    private Float respiratory_rate;
    private Integer gcs_total;
    private String mental_status_triage;
    private String chief_complaint_system;
    private String arrival_mode;
    private String sex;

    public JoinQueueRequest() {}

    /** Convenience constructor for the simple text path (backwards-compatible). */
    public JoinQueueRequest(String symptoms) {
        this.symptoms = symptoms;
        this.symptomsText = symptoms;
        this.intakeLanguage = "en";
    }

    /** Full multimodal constructor. */
    public JoinQueueRequest(String symptomsText, String symptomsVoiceTranscript, String intakeLanguage) {
        // Use the richest available text as the legacy field.
        this.symptoms = symptomsText != null && !symptomsText.isEmpty()
                ? symptomsText : symptomsVoiceTranscript;
        this.symptomsText = symptomsText;
        this.symptomsVoiceTranscript = symptomsVoiceTranscript;
        this.intakeLanguage = intakeLanguage != null ? intakeLanguage : "en";
    }

    // ── Getters ──────────────────────────────────────────────────

    public String getSymptoms() { return symptoms; }
    public String getSymptomsText() { return symptomsText; }
    public String getSymptomsVoiceTranscript() { return symptomsVoiceTranscript; }
    public String getIntakeLanguage() { return intakeLanguage; }
    public Float getPainScore() { return pain_score; }
    public Float getSpo2() { return spo2; }
    public Float getTemperatureC() { return temperature_c; }
    public Float getHeartRate() { return heart_rate; }
    public Float getSystolicBp() { return systolic_bp; }
    public Float getDiastolicBp() { return diastolic_bp; }
    public Float getRespiratoryRate() { return respiratory_rate; }
    public Integer getGcsTotal() { return gcs_total; }
    public String getMentalStatusTriage() { return mental_status_triage; }
    public String getChiefComplaintSystem() { return chief_complaint_system; }
    public String getArrivalMode() { return arrival_mode; }
    public String getSex() { return sex; }

    // ── Setters ──────────────────────────────────────────────────

    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public void setSymptomsText(String symptomsText) { this.symptomsText = symptomsText; }
    public void setSymptomsVoiceTranscript(String t) { this.symptomsVoiceTranscript = t; }
    public void setIntakeLanguage(String intakeLanguage) { this.intakeLanguage = intakeLanguage; }
    public void setPainScore(Float pain_score) { this.pain_score = pain_score; }
    public void setSpo2(Float spo2) { this.spo2 = spo2; }
    public void setTemperatureC(Float temperature_c) { this.temperature_c = temperature_c; }
    public void setHeartRate(Float heart_rate) { this.heart_rate = heart_rate; }
    public void setSystolicBp(Float systolic_bp) { this.systolic_bp = systolic_bp; }
    public void setDiastolicBp(Float diastolic_bp) { this.diastolic_bp = diastolic_bp; }
    public void setRespiratoryRate(Float respiratory_rate) { this.respiratory_rate = respiratory_rate; }
    public void setGcsTotal(Integer gcs_total) { this.gcs_total = gcs_total; }
    public void setMentalStatusTriage(String mental_status_triage) { this.mental_status_triage = mental_status_triage; }
    public void setChiefComplaintSystem(String ccs) { this.chief_complaint_system = ccs; }
    public void setArrivalMode(String arrival_mode) { this.arrival_mode = arrival_mode; }
    public void setSex(String sex) { this.sex = sex; }
}

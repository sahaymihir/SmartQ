package com.example.smartqueue.models.request;

/**
 * Request body for PATCH /api/queue/nurse-triage/:tokenId
 *
 * Carries the actual vitals measured by nursing staff during the
 * physical triage step. All vitals fields are optional — only
 * provide the ones that were measured. The backend merges these
 * on top of the patient's self-reported intake and re-runs the
 * triage model to produce the definitive (nurse-confirmed) score.
 *
 * clinicianPriorityOverride — optional integer bump (0–3) applied
 *   on top of the ML score when the nurse/doctor believes the
 *   patient is more urgent than the model suggests.
 */
public class NurseTriageRequest {
    private Float heart_rate;
    private Float respiratory_rate;
    private Float spo2;
    private Float temperature_c;
    private Float systolic_bp;
    private Float diastolic_bp;
    private Integer gcs_total;
    private Float pain_score;
    private Float news2_score;
    private String mental_status_triage;

    private int clinicianPriorityOverride;
    private String nurseTriageNote;

    public NurseTriageRequest() {}

    // ── Getters ──────────────────────────────────────────────────

    public Float getHeartRate() { return heart_rate; }
    public Float getRespiratoryRate() { return respiratory_rate; }
    public Float getSpo2() { return spo2; }
    public Float getTemperatureC() { return temperature_c; }
    public Float getSystolicBp() { return systolic_bp; }
    public Float getDiastolicBp() { return diastolic_bp; }
    public Integer getGcsTotal() { return gcs_total; }
    public Float getPainScore() { return pain_score; }
    public Float getNews2Score() { return news2_score; }
    public String getMentalStatusTriage() { return mental_status_triage; }
    public int getClinicianPriorityOverride() { return clinicianPriorityOverride; }
    public String getNurseTriageNote() { return nurseTriageNote; }

    // ── Setters ──────────────────────────────────────────────────

    public void setHeartRate(Float v) { this.heart_rate = v; }
    public void setRespiratoryRate(Float v) { this.respiratory_rate = v; }
    public void setSpo2(Float v) { this.spo2 = v; }
    public void setTemperatureC(Float v) { this.temperature_c = v; }
    public void setSystolicBp(Float v) { this.systolic_bp = v; }
    public void setDiastolicBp(Float v) { this.diastolic_bp = v; }
    public void setGcsTotal(Integer v) { this.gcs_total = v; }
    public void setPainScore(Float v) { this.pain_score = v; }
    public void setNews2Score(Float v) { this.news2_score = v; }
    public void setMentalStatusTriage(String v) { this.mental_status_triage = v; }
    public void setClinicianPriorityOverride(int v) { this.clinicianPriorityOverride = v; }
    public void setNurseTriageNote(String v) { this.nurseTriageNote = v; }
}

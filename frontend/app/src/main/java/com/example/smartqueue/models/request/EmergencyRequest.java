package com.example.smartqueue.models.request;

/**
 * Request body for POST /api/queue/emergency
 *
 * Used by nursing/admin staff to create an emergency token for a
 * patient who is unconscious or unable to self-register. The
 * backend auto-routes to immediate_review lane (KTAS 1) and
 * bypasses the normal self-intake step.
 *
 * patientId   — MongoDB _id of the patient's existing account.
 *               If the patient is new, register them first via
 *               POST /api/auth/register, then use that ID here.
 * doctorId    — Optional. If omitted, the backend auto-selects
 *               the first available doctor.
 * reportedSymptoms — Optional brief description from bystanders
 *               or the accompanying person.
 * estimatedAge — Optional age estimate (for priority calculation
 *               when the patient's profile cannot be loaded).
 */
public class EmergencyRequest {
    private String patientId;
    private String doctorId;
    private String reportedSymptoms;
    private Integer estimatedAge;

    public EmergencyRequest() {}

    public EmergencyRequest(String patientId, String doctorId, String reportedSymptoms) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.reportedSymptoms = reportedSymptoms;
    }

    // ── Getters ──────────────────────────────────────────────────

    public String getPatientId() { return patientId; }
    public String getDoctorId() { return doctorId; }
    public String getReportedSymptoms() { return reportedSymptoms; }
    public Integer getEstimatedAge() { return estimatedAge; }

    // ── Setters ──────────────────────────────────────────────────

    public void setPatientId(String patientId) { this.patientId = patientId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }
    public void setReportedSymptoms(String reportedSymptoms) { this.reportedSymptoms = reportedSymptoms; }
    public void setEstimatedAge(Integer estimatedAge) { this.estimatedAge = estimatedAge; }
}

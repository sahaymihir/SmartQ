package com.example.smartqueue.models.response;

public class PrescriptionResponse {
    private boolean success;
    private String message;
    private String prescriptionId;
    private String tokenId;
    private String patientId;
    private String doctorId;
    private String patientName;
    private String doctorName;
    private String doctorSpecialty;
    private String reportedSymptoms;
    private String visitType;
    private String status;
    private String symptomsSummary;
    private String testsDone;
    private String medications;
    private String conclusion;
    private String adviceNotes;
    private String finalizedAt;
    private String completedAt;
    private String createdAt;
    private boolean hasPrescription;
    private boolean canEdit;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getPrescriptionId() { return prescriptionId; }
    public String getTokenId() { return tokenId; }
    public String getPatientId() { return patientId; }
    public String getDoctorId() { return doctorId; }
    public String getPatientName() { return patientName; }
    public String getDoctorName() { return doctorName; }
    public String getDoctorSpecialty() { return doctorSpecialty; }
    public String getReportedSymptoms() { return reportedSymptoms; }
    public String getVisitType() { return visitType; }
    public String getStatus() { return status; }
    public String getSymptomsSummary() { return symptomsSummary; }
    public String getTestsDone() { return testsDone; }
    public String getMedications() { return medications; }
    public String getConclusion() { return conclusion; }
    public String getAdviceNotes() { return adviceNotes; }
    public String getFinalizedAt() { return finalizedAt; }
    public String getCompletedAt() { return completedAt; }
    public String getCreatedAt() { return createdAt; }
    public boolean hasPrescription() { return hasPrescription; }
    public boolean canEdit() { return canEdit; }
}

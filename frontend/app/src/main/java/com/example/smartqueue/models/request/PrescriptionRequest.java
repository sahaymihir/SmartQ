package com.example.smartqueue.models.request;

public class PrescriptionRequest {
    private String symptomsSummary;
    private String testsDone;
    private String medications;
    private String conclusion;
    private String adviceNotes;
    private String status;

    public PrescriptionRequest(String symptomsSummary, String testsDone, String medications,
                               String conclusion, String adviceNotes, String status) {
        this.symptomsSummary = symptomsSummary;
        this.testsDone = testsDone;
        this.medications = medications;
        this.conclusion = conclusion;
        this.adviceNotes = adviceNotes;
        this.status = status;
    }

    public String getSymptomsSummary() { return symptomsSummary; }
    public String getTestsDone() { return testsDone; }
    public String getMedications() { return medications; }
    public String getConclusion() { return conclusion; }
    public String getAdviceNotes() { return adviceNotes; }
    public String getStatus() { return status; }
}

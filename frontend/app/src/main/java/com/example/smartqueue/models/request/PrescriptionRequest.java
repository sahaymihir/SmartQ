package com.example.smartqueue.models.request;

public class PrescriptionRequest {
    private String tokenId;
    private String patientId;
    private String diagnosis;
    private String medicines;
    private String notes;

    public PrescriptionRequest(String tokenId, String patientId, String diagnosis, String medicines, String notes) {
        this.tokenId = tokenId;
        this.patientId = patientId;
        this.diagnosis = diagnosis;
        this.medicines = medicines;
        this.notes = notes;
    }

    public String getTokenId() { return tokenId; }
    public String getPatientId() { return patientId; }
    public String getDiagnosis() { return diagnosis; }
    public String getMedicines() { return medicines; }
    public String getNotes() { return notes; }
}

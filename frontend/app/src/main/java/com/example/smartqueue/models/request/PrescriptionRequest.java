package com.example.smartqueue.models.request;

public class PrescriptionRequest {
    private String tokenId;
    private String diagnosis;
    private String medicines;
    private String notes;

    public PrescriptionRequest(String tokenId, String diagnosis, String medicines, String notes) {
        this.tokenId = tokenId;
        this.diagnosis = diagnosis;
        this.medicines = medicines;
        this.notes = notes;
    }

    public String getTokenId() { return tokenId; }
    public String getDiagnosis() { return diagnosis; }
    public String getMedicines() { return medicines; }
    public String getNotes() { return notes; }
}

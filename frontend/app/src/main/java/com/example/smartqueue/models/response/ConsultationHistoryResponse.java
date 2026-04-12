package com.example.smartqueue.models.response;

import java.util.List;

public class ConsultationHistoryResponse {
    private boolean success;
    private int total;
    private List<Consultation> history;

    public boolean isSuccess() { return success; }
    public int getTotal() { return total; }
    public List<Consultation> getHistory() { return history; }

    public static class Consultation {
        private String tokenId;
        private String doctorId;
        private String date;
        private String doctorSpecialty;
        private String symptoms;
        private String symptomsSummary;
        private String diagnosis;
        private String medicines;
        private String notes;
        private String conclusionPreview;
        private String doctorName;
        private String visitType;
        private boolean hasPrescription;
        private String status;
        private String finalizedAt;

        public String getTokenId() { return tokenId; }
        public String getDoctorId() { return doctorId; }
        public String getDate() { return date; }
        public String getDoctorSpecialty() { return doctorSpecialty; }
        public String getSymptoms() { return symptoms; }
        public String getSymptomsSummary() { return symptomsSummary; }
        public String getDiagnosis() { return diagnosis; }
        public String getMedicines() { return medicines; }
        public String getNotes() { return notes; }
        public String getConclusionPreview() { return conclusionPreview; }
        public String getDoctorName() { return doctorName; }
        public String getVisitType() { return visitType; }
        public boolean hasPrescription() { return hasPrescription; }
        public String getStatus() { return status; }
        public String getFinalizedAt() { return finalizedAt; }
    }
}

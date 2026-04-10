package com.example.smartqueue.models.response;

import java.util.List;

public class ConsultationHistoryResponse {
    private boolean success;
    private List<Consultation> history;

    public boolean isSuccess() { return success; }
    public List<Consultation> getHistory() { return history; }

    public static class Consultation {
        private String date;
        private String diagnosis;
        private String medicines;
        private String notes;
        private String doctorName;

        public String getDate() { return date; }
        public String getDiagnosis() { return diagnosis; }
        public String getMedicines() { return medicines; }
        public String getNotes() { return notes; }
        public String getDoctorName() { return doctorName; }
    }
}

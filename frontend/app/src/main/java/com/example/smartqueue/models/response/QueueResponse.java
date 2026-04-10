package com.example.smartqueue.models.response;

import java.util.List;

public class QueueResponse {
    private boolean success;
    private int position;
    private int etaMinutes;
    private int tokenNumber;
    private String status;
    private String doctorName;
    private boolean checkedIn;
    private int avgConsultationMinutes;
    private boolean isPaused;
    private List<QueueEntry> queue;

    // Getters
    public boolean isSuccess()                  { return success; }
    public int getPosition()                    { return position; }
    public int getEtaMinutes()                  { return etaMinutes; }
    public int getTokenNumber()                 { return tokenNumber; }
    public String getStatus()                   { return status; }
    public String getDoctorName()               { return doctorName; }
    public boolean isCheckedIn()                { return checkedIn; }
    public int getAvgConsultationMinutes()      { return avgConsultationMinutes == 0 ? 8 : avgConsultationMinutes; }
    public boolean isPaused()                   { return isPaused; }
    public List<QueueEntry> getQueue()          { return queue; }

    // Setters
    public void setSuccess(boolean v)                   { this.success = v; }
    public void setPosition(int v)                      { this.position = v; }
    public void setEtaMinutes(int v)                    { this.etaMinutes = v; }
    public void setTokenNumber(int v)                   { this.tokenNumber = v; }
    public void setStatus(String v)                     { this.status = v; }
    public void setDoctorName(String v)                 { this.doctorName = v; }
    public void setCheckedIn(boolean v)                 { this.checkedIn = v; }
    public void setAvgConsultationMinutes(int v)        { this.avgConsultationMinutes = v; }
    public void setQueue(List<QueueEntry> v)            { this.queue = v; }

    public static class QueueEntry {
        private String tokenId;
        private String patientId; // Added
        private String patientName;
        private int tokenNumber; // Added
        private int position;
        private int etaMinutes;
        private String priority;
        private String status;

        public String getTokenId()      { return tokenId; }
        public String getPatientId()    { return patientId; }
        public String getPatientName()  { return patientName; }
        public int getTokenNumber()     { return tokenNumber; }
        public int getPosition()        { return position; }
        public int getEtaMinutes()      { return etaMinutes; }
        public String getPriority()     { return priority; }
        public String getStatus()       { return status; }

        public void setTokenId(String v)     { this.tokenId = v; }
        public void setPatientId(String v)   { this.patientId = v; }
        public void setPatientName(String v) { this.patientName = v; }
        public void setTokenNumber(int v)    { this.tokenNumber = v; }
        public void setPosition(int v)       { this.position = v; }
        public void setEtaMinutes(int v)     { this.etaMinutes = v; }
        public void setPriority(String v)    { this.priority = v; }
        public void setStatus(String v)      { this.status = v; }
    }
}

package com.example.smartqueue.models.response;

import com.example.smartqueue.models.response.TestRecommendationResponse.Recommendation;
import java.util.List;

public class QueueResponse {
    private boolean success;
    private int position;
    private int etaMinutes;
    private int tokenNumber;
    private String status;
    private String doctorName;
    private boolean checkedIn;
    private int snoozeCount;
    private String routingLane;
    private boolean requiresImmediateReview;
    private boolean nurseTriaged;
    private List<Recommendation> testRecommendations;
    private String escalationReason;
    private Integer triagePriorityClass;
    private int avgConsultationMinutes;
    private boolean isPaused;
    private int currentToken;
    private List<QueueEntry> queue;

    // Getters
    public boolean isSuccess()                  { return success; }
    public int getPosition()                    { return position; }
    public int getEtaMinutes()                  { return etaMinutes; }
    public int getTokenNumber()                 { return tokenNumber; }
    public String getStatus()                   { return status; }
    public String getDoctorName()               { return doctorName; }
    public boolean isCheckedIn()                { return checkedIn; }
    public int getSnoozeCount()                 { return snoozeCount; }
    public String getRoutingLane()              { return routingLane; }
    public boolean isImmediateReviewRequired()  { return requiresImmediateReview; }
    public boolean isNurseTriaged()             { return nurseTriaged; }
    public List<Recommendation> getTestRecommendations() { return testRecommendations; }
    public boolean hasTestRecommendations() {
        return testRecommendations != null && !testRecommendations.isEmpty();
    }
    public String getEscalationReason()         { return escalationReason; }
    public Integer getTriagePriorityClass()     { return triagePriorityClass; }
    public int getAvgConsultationMinutes()      { return avgConsultationMinutes == 0 ? 8 : avgConsultationMinutes; }
    public boolean isPaused()                   { return isPaused; }
    public int getCurrentToken()                { return currentToken; }
    public List<QueueEntry> getQueue()          { return queue; }

    // Setters
    public void setSuccess(boolean v)                   { this.success = v; }
    public void setPosition(int v)                      { this.position = v; }
    public void setEtaMinutes(int v)                    { this.etaMinutes = v; }
    public void setTokenNumber(int v)                   { this.tokenNumber = v; }
    public void setStatus(String v)                     { this.status = v; }
    public void setDoctorName(String v)                 { this.doctorName = v; }
    public void setCheckedIn(boolean v)                 { this.checkedIn = v; }
    public void setSnoozeCount(int v)                   { this.snoozeCount = v; }
    public void setRoutingLane(String v)                { this.routingLane = v; }
    public void setRequiresImmediateReview(boolean v)   { this.requiresImmediateReview = v; }
    public void setNurseTriaged(boolean v)              { this.nurseTriaged = v; }
    public void setTestRecommendations(List<Recommendation> v) { this.testRecommendations = v; }
    public void setEscalationReason(String v)           { this.escalationReason = v; }
    public void setTriagePriorityClass(Integer v)       { this.triagePriorityClass = v; }
    public void setAvgConsultationMinutes(int v)        { this.avgConsultationMinutes = v; }
    public void setCurrentToken(int v)                   { this.currentToken = v; }
    public void setQueue(List<QueueEntry> v)            { this.queue = v; }

    public static class QueueEntry {
        private String tokenId;
        private String patientId; // Added
        private String patientName;
        private int patientAge;
        private String patientPhone;
        private String doctorName;
        private String doctorSpecialty;
        private String symptoms;
        private int tokenNumber; // Added
        private int position;
        private int etaMinutes;
        private String priority;
        private String status;
        private Integer triagePriorityClass;
        private Integer modelPriorityClass;
        private Double priorityFinalScore;
        private boolean manualReviewRequired;
        private String routingLane;
        private boolean requiresImmediateReview;
        private String escalationReason;
        private String overrideReason;
        private boolean checkedIn;
        private boolean nurseTriaged;

        public String getTokenId()      { return tokenId; }
        public String getPatientId()    { return patientId; }
        public String getPatientName()  { return patientName; }
        public int getPatientAge()      { return patientAge; }
        public String getPatientPhone() { return patientPhone; }
        public String getDoctorName()   { return doctorName; }
        public String getDoctorSpecialty() { return doctorSpecialty; }
        public String getSymptoms()     { return symptoms; }
        public int getTokenNumber()     { return tokenNumber; }
        public int getPosition()        { return position; }
        public int getEtaMinutes()      { return etaMinutes; }
        public String getPriority()     { return priority; }
        public String getStatus()       { return status; }
        public Integer getTriagePriorityClass() { return triagePriorityClass; }
        public Integer getModelPriorityClass() { return modelPriorityClass; }
        public Double getPriorityFinalScore() { return priorityFinalScore; }
        public boolean isManualReviewRequired() { return manualReviewRequired; }
        public String getRoutingLane() { return routingLane; }
        public boolean isImmediateReviewRequired() { return requiresImmediateReview; }
        public String getEscalationReason() { return escalationReason; }
        public String getOverrideReason() { return overrideReason; }
        public boolean isCheckedIn() { return checkedIn; }
        public boolean isNurseTriaged() { return nurseTriaged; }

        public void setTokenId(String v)     { this.tokenId = v; }
        public void setPatientId(String v)   { this.patientId = v; }
        public void setPatientName(String v) { this.patientName = v; }
        public void setPatientAge(int v)     { this.patientAge = v; }
        public void setPatientPhone(String v){ this.patientPhone = v; }
        public void setDoctorName(String v)  { this.doctorName = v; }
        public void setDoctorSpecialty(String v) { this.doctorSpecialty = v; }
        public void setSymptoms(String v)    { this.symptoms = v; }
        public void setTokenNumber(int v)    { this.tokenNumber = v; }
        public void setPosition(int v)       { this.position = v; }
        public void setEtaMinutes(int v)     { this.etaMinutes = v; }
        public void setPriority(String v)    { this.priority = v; }
        public void setStatus(String v)      { this.status = v; }
        public void setTriagePriorityClass(Integer v) { this.triagePriorityClass = v; }
        public void setModelPriorityClass(Integer v) { this.modelPriorityClass = v; }
        public void setPriorityFinalScore(Double v) { this.priorityFinalScore = v; }
        public void setManualReviewRequired(boolean v) { this.manualReviewRequired = v; }
        public void setRoutingLane(String v) { this.routingLane = v; }
        public void setRequiresImmediateReview(boolean v) { this.requiresImmediateReview = v; }
        public void setEscalationReason(String v) { this.escalationReason = v; }
        public void setOverrideReason(String v) { this.overrideReason = v; }
        public void setCheckedIn(boolean v) { this.checkedIn = v; }
        public void setNurseTriaged(boolean v) { this.nurseTriaged = v; }
    }
}

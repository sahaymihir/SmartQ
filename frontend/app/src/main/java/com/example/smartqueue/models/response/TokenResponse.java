package com.example.smartqueue.models.response;

import com.example.smartqueue.models.response.TestRecommendationResponse.Recommendation;
import java.util.List;

public class TokenResponse {
    private boolean success;
    private String mentalStatusTriage;
    private Integer gcsTotal;
    private Double news2Score;
    private String tokenId;
    private int tokenNumber;
    private int position;
    private int etaMinutes;
    private int predictedWaitMinutes;
    private int snoozeCount;
    private String routingLane;
    private boolean requiresImmediateReview;
    private String escalationReason;
    private Integer triagePriorityClass;
    private Integer modelPriorityClass;
    private String priority;
    private Double priorityScore;
    private Double priorityFinalScore;
    private double aiConfidence;
    private double triageConfidence;
    private boolean triageLowConfidence;
    private String triageRecommendation;
    private String triageSource;
    private boolean manualReviewRequired;
    private String derivedChiefComplaintSystem;
    private String queueSelectedRoute;
    private String queueRouteType;
    private String queueRationale;
    private Integer queueCurrentLength;
    private Integer queueAvailableDoctors;
    private Double queueAvgWaitMinutes;
    private String message;
    private List<SafetyMatch> safetyMatches;

    // Visit intent fields
    private String visitType;
    private String followUpTokenId;

    // Nurse triage status
    private boolean nurseTriaged;

    // Test recommendations (populated when predicted wait >= threshold)
    private List<Recommendation> testRecommendations;

    public boolean isSuccess() { return success; }
    public String getMentalStatusTriage() { return mentalStatusTriage; }
    public Integer getGcsTotal() { return gcsTotal; }
    public Double getNews2Score() { return news2Score; }
    public String getTokenId() { return tokenId; }
    public int getTokenNumber() { return tokenNumber; }
    public int getPosition() { return position; }
    public int getEtaMinutes() { return etaMinutes; }
    public int getPredictedWaitMinutes() { return predictedWaitMinutes; }
    public int getSnoozeCount() { return snoozeCount; }
    public String getRoutingLane() { return routingLane; }
    public boolean isImmediateReviewRequired() { return requiresImmediateReview; }
    public String getEscalationReason() { return escalationReason; }
    public Integer getTriagePriorityClass() { return triagePriorityClass; }
    public Integer getModelPriorityClass() { return modelPriorityClass; }
    public String getPriority() { return priority; }
    public Double getPriorityScore() { return priorityScore; }
    public Double getPriorityFinalScore() {
        return priorityFinalScore != null ? priorityFinalScore : priorityScore;
    }
    public double getAiConfidence() { return aiConfidence; }
    public double getTriageConfidence() { return triageConfidence; }
    public boolean isTriageLowConfidence() { return triageLowConfidence; }
    public String getTriageRecommendation() { return triageRecommendation; }
    public String getTriageSource() { return triageSource; }
    public boolean isManualReviewRequired() { return manualReviewRequired; }
    public String getDerivedChiefComplaintSystem() { return derivedChiefComplaintSystem; }
    public String getQueueSelectedRoute() { return queueSelectedRoute; }
    public String getQueueRouteType() { return queueRouteType; }
    public String getQueueRationale() { return queueRationale; }
    public Integer getQueueCurrentLength() { return queueCurrentLength; }
    public Integer getQueueAvailableDoctors() { return queueAvailableDoctors; }
    public Double getQueueAvgWaitMinutes() { return queueAvgWaitMinutes; }
    public String getMessage() { return message; }
    public String getVisitType() { return visitType != null ? visitType : "new"; }
    public String getFollowUpTokenId() { return followUpTokenId; }
    public boolean isNurseTriaged() { return nurseTriaged; }
    public List<Recommendation> getTestRecommendations() { return testRecommendations; }
    public List<SafetyMatch> getSafetyMatches() { return safetyMatches; }
    public boolean hasTestRecommendations() {
        return testRecommendations != null && !testRecommendations.isEmpty();
    }

    public static class SafetyMatch {
        private String ruleId;
        private String severity;
        private Integer forcedPriorityClass;
        private String preferredRoute;
        private String rationale;

        public String getRuleId() { return ruleId; }
        public String getSeverity() { return severity; }
        public Integer getForcedPriorityClass() { return forcedPriorityClass; }
        public String getPreferredRoute() { return preferredRoute; }
        public String getRationale() { return rationale; }
    }
}

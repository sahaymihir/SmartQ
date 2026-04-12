package com.example.smartqueue.models.response;

import com.example.smartqueue.models.response.TestRecommendationResponse.Recommendation;
import java.util.List;

public class TokenResponse {
    private boolean success;
    private String tokenId;
    private int tokenNumber;
    private int position;
    private int etaMinutes;
    private int predictedWaitMinutes;
    private String routingLane;
    private boolean requiresImmediateReview;
    private String escalationReason;
    private Integer triagePriorityClass;
    private String message;

    // Visit intent fields
    private String visitType;
    private String followUpTokenId;

    // Nurse triage status
    private boolean nurseTriaged;

    // Test recommendations (populated when predicted wait >= threshold)
    private List<Recommendation> testRecommendations;

    public boolean isSuccess() { return success; }
    public String getTokenId() { return tokenId; }
    public int getTokenNumber() { return tokenNumber; }
    public int getPosition() { return position; }
    public int getEtaMinutes() { return etaMinutes; }
    public int getPredictedWaitMinutes() { return predictedWaitMinutes; }
    public String getRoutingLane() { return routingLane; }
    public boolean isImmediateReviewRequired() { return requiresImmediateReview; }
    public String getEscalationReason() { return escalationReason; }
    public Integer getTriagePriorityClass() { return triagePriorityClass; }
    public String getMessage() { return message; }
    public String getVisitType() { return visitType != null ? visitType : "new"; }
    public String getFollowUpTokenId() { return followUpTokenId; }
    public boolean isNurseTriaged() { return nurseTriaged; }
    public List<Recommendation> getTestRecommendations() { return testRecommendations; }
    public boolean hasTestRecommendations() {
        return testRecommendations != null && !testRecommendations.isEmpty();
    }
}

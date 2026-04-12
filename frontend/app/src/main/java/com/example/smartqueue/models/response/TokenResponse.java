package com.example.smartqueue.models.response;

public class TokenResponse {
    private boolean success;
    private String tokenId;
    private int tokenNumber;
    private int position;
    private int etaMinutes;
    private String routingLane;
    private boolean requiresImmediateReview;
    private String escalationReason;
    private Integer triagePriorityClass;
    private String message;

    public boolean isSuccess() { return success; }
    public String getTokenId() { return tokenId; }
    public int getTokenNumber() { return tokenNumber; }
    public int getPosition() { return position; }
    public int getEtaMinutes() { return etaMinutes; }
    public String getRoutingLane() { return routingLane; }
    public boolean isImmediateReviewRequired() { return requiresImmediateReview; }
    public String getEscalationReason() { return escalationReason; }
    public Integer getTriagePriorityClass() { return triagePriorityClass; }
    public String getMessage() { return message; }
}

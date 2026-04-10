package com.example.smartqueue.models.response;

public class TokenResponse {
    private boolean success;
    private String tokenId;
    private int tokenNumber;
    private int position;
    private int etaMinutes;
    private String message;

    public boolean isSuccess() { return success; }
    public String getTokenId() { return tokenId; }
    public int getTokenNumber() { return tokenNumber; }
    public int getPosition() { return position; }
    public int getEtaMinutes() { return etaMinutes; }
    public String getMessage() { return message; }
}
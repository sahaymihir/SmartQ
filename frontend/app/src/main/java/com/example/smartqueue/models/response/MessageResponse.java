package com.example.smartqueue.models.response;

public class MessageResponse {
    private boolean success;
    private String message;
    private boolean requiresPrescription;
    private String tokenId;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public boolean isRequiresPrescription() { return requiresPrescription; }
    public String getTokenId() { return tokenId; }
}

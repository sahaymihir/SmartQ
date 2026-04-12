package com.example.smartqueue.models.response;

public class CheckInStatusResponse {
    private boolean success;
    private boolean checkedIn;
    private String checkedInAt;
    private String message;

    public boolean isSuccess() { return success; }
    public boolean isCheckedIn() { return checkedIn; }
    public String getCheckedInAt() { return checkedInAt; }
    public String getMessage() { return message; }
}

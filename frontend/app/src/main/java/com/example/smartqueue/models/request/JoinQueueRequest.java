package com.example.smartqueue.models.request;

/**
 * Request body for joining a queue with AI triage.
 * Sends the patient's symptoms text to the backend,
 * which forwards it to the Python ML microservice.
 */
public class JoinQueueRequest {
    private String symptoms;

    public JoinQueueRequest() {}

    public JoinQueueRequest(String symptoms) {
        this.symptoms = symptoms;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public void setSymptoms(String symptoms) {
        this.symptoms = symptoms;
    }
}

package com.example.smartqueue.models.request;

public class SymptomRequest {
    private String symptoms;
    private Integer age;

    public SymptomRequest(String symptoms) {
        this(symptoms, null);
    }

    public SymptomRequest(String symptoms, Integer age) {
        this.symptoms = symptoms;
        this.age = age;
    }

    public String getSymptoms() {
        return symptoms;
    }

    public Integer getAge() {
        return age;
    }
}

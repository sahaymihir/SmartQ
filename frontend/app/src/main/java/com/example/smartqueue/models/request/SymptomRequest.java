package com.example.smartqueue.models.request;

import com.google.gson.annotations.SerializedName;

public class SymptomRequest {
    private String symptoms;
    private Integer age;
    private String sex;
    @SerializedName("mental_status_triage")
    private String mentalStatusTriage;
    @SerializedName("chief_complaint_system")
    private String chiefComplaintSystem;
    private String language;
    @SerializedName("temperature_c")
    private Double temperatureC;
    @SerializedName("pain_score")
    private Double painScore;
    private Double spo2;
    @SerializedName("respiratory_rate")
    private Double respiratoryRate;
    @SerializedName("heart_rate")
    private Double heartRate;
    @SerializedName("systolic_bp")
    private Double systolicBp;
    @SerializedName("diastolic_bp")
    private Double diastolicBp;
    @SerializedName("gcs_total")
    private Integer gcsTotal;
    @SerializedName("news2_score")
    private Double news2Score;

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

    public SymptomRequest setAge(Integer age) {
        this.age = age;
        return this;
    }

    public SymptomRequest setSex(String sex) {
        this.sex = sex;
        return this;
    }

    public SymptomRequest setMentalStatusTriage(String mentalStatusTriage) {
        this.mentalStatusTriage = mentalStatusTriage;
        return this;
    }

    public SymptomRequest setChiefComplaintSystem(String chiefComplaintSystem) {
        this.chiefComplaintSystem = chiefComplaintSystem;
        return this;
    }

    public SymptomRequest setLanguage(String language) {
        this.language = language;
        return this;
    }

    public SymptomRequest setTemperatureC(Double temperatureC) {
        this.temperatureC = temperatureC;
        return this;
    }

    public SymptomRequest setPainScore(Double painScore) {
        this.painScore = painScore;
        return this;
    }

    public SymptomRequest setSpo2(Double spo2) {
        this.spo2 = spo2;
        return this;
    }

    public SymptomRequest setRespiratoryRate(Double respiratoryRate) {
        this.respiratoryRate = respiratoryRate;
        return this;
    }

    public SymptomRequest setHeartRate(Double heartRate) {
        this.heartRate = heartRate;
        return this;
    }

    public SymptomRequest setSystolicBp(Double systolicBp) {
        this.systolicBp = systolicBp;
        return this;
    }

    public SymptomRequest setDiastolicBp(Double diastolicBp) {
        this.diastolicBp = diastolicBp;
        return this;
    }

    public SymptomRequest setGcsTotal(Integer gcsTotal) {
        this.gcsTotal = gcsTotal;
        return this;
    }

    public SymptomRequest setNews2Score(Double news2Score) {
        this.news2Score = news2Score;
        return this;
    }
}

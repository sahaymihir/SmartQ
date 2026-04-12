package com.example.smartqueue.models.response;

import java.util.List;

public class SymptomPredictResponse {
    private boolean success;
    private Integer age;
    private String sex;
    private String mentalStatusTriage;
    private String chiefComplaintSystem;
    private Double temperatureC;
    private Double painScore;
    private Double spo2;
    private Double respiratoryRate;
    private Double heartRate;
    private Double systolicBp;
    private Double diastolicBp;
    private Integer gcsTotal;
    private Double news2Score;
    private String derivedChiefComplaintSystem;
    private List<String> extractedFactors;
    private List<String> extractedSignals;
    private List<SpecialtyScore> specialtyScores;
    private List<AlternativeSpecialist> alternativeSpecialists;
    private Doctor recommendedDoctor;
    private double confidence;
    private String reasoning;
    private String primarySpecialist;
    private String routedSpecialty;
    private String normalizedSymptoms;
    private boolean lowConfidence;
    private String modelSource;
    private String priorityLabel;
    private Double priorityScore;
    private Double priorityFinalScore;
    private Integer triagePriorityClass;
    private double triageConfidence;
    private boolean triageLowConfidence;
    private String triageRecommendation;
    private String triageSource;
    private String priorityDecisionTrace;
    private PriorityComponents priorityComponents;
    private Integer modelPriorityClass;
    private Integer guardrailedPriorityClass;
    private String guardrailedRecommendation;
    private List<SafetyMatch> safetyMatches;
    private String queueSelectedRoute;
    private String queueRouteType;
    private String queueRationale;
    private Integer queueCurrentLength;
    private Integer queueAvailableDoctors;
    private Double queueAvgWaitMinutes;
    private List<TestRecommendation> testRecommendations;
    private String testSource;
    private boolean testLowConfidence;
    private String flowSource;

    public boolean isSuccess() { return success; }
    public Integer getAge() { return age; }
    public String getSex() { return sex; }
    public String getMentalStatusTriage() { return mentalStatusTriage; }
    public String getChiefComplaintSystem() { return chiefComplaintSystem; }
    public Double getTemperatureC() { return temperatureC; }
    public Double getPainScore() { return painScore; }
    public Double getSpo2() { return spo2; }
    public Double getRespiratoryRate() { return respiratoryRate; }
    public Double getHeartRate() { return heartRate; }
    public Double getSystolicBp() { return systolicBp; }
    public Double getDiastolicBp() { return diastolicBp; }
    public Integer getGcsTotal() { return gcsTotal; }
    public Double getNews2Score() { return news2Score; }
    public String getDerivedChiefComplaintSystem() { return derivedChiefComplaintSystem; }

    public List<String> getExtractedFactors() {
        return extractedFactors != null && !extractedFactors.isEmpty()
                ? extractedFactors : extractedSignals;
    }

    public List<String> getExtractedSignals() {
        return extractedSignals != null && !extractedSignals.isEmpty()
                ? extractedSignals : extractedFactors;
    }

    public List<SpecialtyScore> getSpecialtyScores() { return specialtyScores; }
    public List<AlternativeSpecialist> getAlternativeSpecialists() { return alternativeSpecialists; }
    public Doctor getRecommendedDoctor() { return recommendedDoctor; }
    public double getConfidence() { return confidence; }
    public String getReasoning() { return reasoning; }
    public String getPrimarySpecialist() { return primarySpecialist; }
    public String getRoutedSpecialty() { return routedSpecialty; }
    public String getNormalizedSymptoms() { return normalizedSymptoms; }
    public boolean isLowConfidence() { return lowConfidence; }
    public String getModelSource() { return modelSource; }
    public String getPriorityLabel() { return priorityLabel; }
    public Double getPriorityScore() { return priorityScore; }
    public Double getPriorityFinalScore() {
        return priorityFinalScore != null ? priorityFinalScore : priorityScore;
    }
    public Integer getTriagePriorityClass() { return triagePriorityClass; }
    public double getTriageConfidence() { return triageConfidence; }
    public boolean isTriageLowConfidence() { return triageLowConfidence; }
    public String getTriageRecommendation() { return triageRecommendation; }
    public String getTriageSource() { return triageSource; }
    public String getPriorityDecisionTrace() { return priorityDecisionTrace; }
    public PriorityComponents getPriorityComponents() { return priorityComponents; }
    public Integer getModelPriorityClass() { return modelPriorityClass; }
    public Integer getGuardrailedPriorityClass() {
        return guardrailedPriorityClass != null ? guardrailedPriorityClass : triagePriorityClass;
    }
    public String getGuardrailedRecommendation() { return guardrailedRecommendation; }
    public List<SafetyMatch> getSafetyMatches() { return safetyMatches; }
    public String getQueueSelectedRoute() { return queueSelectedRoute; }
    public String getQueueRouteType() { return queueRouteType; }
    public String getQueueRationale() { return queueRationale; }
    public Integer getQueueCurrentLength() { return queueCurrentLength; }
    public Integer getQueueAvailableDoctors() { return queueAvailableDoctors; }
    public Double getQueueAvgWaitMinutes() { return queueAvgWaitMinutes; }
    public List<TestRecommendation> getTestRecommendations() { return testRecommendations; }
    public String getTestSource() { return testSource; }
    public boolean isTestLowConfidence() { return testLowConfidence; }
    public String getFlowSource() { return flowSource; }

    public static class SpecialtyScore {
        private String specialty;
        private String routedSpecialty;
        private double score;
        private List<String> matchedKeywords;
        private List<String> matchedSignals;

        public String getSpecialty() { return specialty; }
        public String getRoutedSpecialty() { return routedSpecialty; }
        public double getScore() { return score; }

        public List<String> getMatchedKeywords() {
            return matchedKeywords != null && !matchedKeywords.isEmpty()
                    ? matchedKeywords : matchedSignals;
        }
    }

    public static class AlternativeSpecialist {
        private String specialist;
        private String routedSpecialty;
        private double confidence;
        private List<String> matchedSignals;

        public String getSpecialist() { return specialist; }
        public String getRoutedSpecialty() { return routedSpecialty; }
        public double getConfidence() { return confidence; }
        public List<String> getMatchedSignals() { return matchedSignals; }
    }

    public static class Doctor {
        private String id;
        private String name;
        private String specialty;
        private boolean isAvailable;

        public String getId() { return id; }
        public String getName() { return name; }
        public String getSpecialty() { return specialty; }
        public boolean isAvailable() { return isAvailable; }
    }

    public static class PriorityComponents {
        private double age;
        private double triage;
        private double symptomNlp;
        private double ocrFlags;
        private double clinicianOverride;

        public double getAge() { return age; }
        public double getTriage() { return triage; }
        public double getSymptomNlp() { return symptomNlp; }
        public double getOcrFlags() { return ocrFlags; }
        public double getClinicianOverride() { return clinicianOverride; }
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

    public static class TestRecommendation {
        private String test;
        private String rationale;
        private String urgency;

        public String getTest() { return test; }
        public String getRationale() { return rationale; }
        public String getUrgency() { return urgency; }
    }
}

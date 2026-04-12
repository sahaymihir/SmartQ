package com.example.smartqueue.models.response;

import java.util.List;

public class ModelEvalHistoryResponse {
    private boolean success;
    private List<EvalEntry> history;

    public boolean isSuccess()           { return success; }
    public List<EvalEntry> getHistory()  { return history; }

    public static class EvalEntry {
        private String symptoms;
        private List<String> extractedFactors;
        private List<SpecialtyScore> specialtyScores;
        private RecommendedDoctor recommendedDoctor;
        private double confidence;
        private String reasoning;
        private String timestamp;
        private String patientName;

        public String getSymptoms()                      { return symptoms; }
        public List<String> getExtractedFactors()        { return extractedFactors; }
        public List<SpecialtyScore> getSpecialtyScores() { return specialtyScores; }
        public RecommendedDoctor getRecommendedDoctor()  { return recommendedDoctor; }
        public double getConfidence()                    { return confidence; }
        public String getReasoning()                     { return reasoning; }
        public String getTimestamp()                     { return timestamp; }
        public String getPatientName()                   { return patientName; }
    }

    public static class SpecialtyScore {
        private String specialty;
        private double score;
        private List<String> matchedKeywords;

        public String getSpecialty()             { return specialty; }
        public double getScore()                 { return score; }
        public List<String> getMatchedKeywords() { return matchedKeywords; }
    }

    public static class RecommendedDoctor {
        private String id;
        private String name;
        private String specialty;

        public String getId()        { return id; }
        public String getName()      { return name; }
        public String getSpecialty() { return specialty; }
    }
}

package com.example.smartqueue.models.response;

import java.util.List;

public class SymptomPredictResponse {
    private boolean success;
    private List<String> extractedFactors;
    private List<SpecialtyScore> specialtyScores;
    private Doctor recommendedDoctor;
    private double confidence;
    private String reasoning;

    public boolean isSuccess()                      { return success; }
    public List<String> getExtractedFactors()       { return extractedFactors; }
    public List<SpecialtyScore> getSpecialtyScores() { return specialtyScores; }
    public Doctor getRecommendedDoctor()             { return recommendedDoctor; }
    public double getConfidence()                   { return confidence; }
    public String getReasoning()                    { return reasoning; }

    public static class SpecialtyScore {
        private String specialty;
        private double score;
        private List<String> matchedKeywords;

        public String getSpecialty()              { return specialty; }
        public double getScore()                  { return score; }
        public List<String> getMatchedKeywords()  { return matchedKeywords; }
    }

    public static class Doctor {
        private String id;
        private String name;
        private String specialty;

        public String getId()        { return id; }
        public String getName()      { return name; }
        public String getSpecialty() { return specialty; }
    }
}

package com.example.smartqueue.models.response;

import java.util.List;

public class SymptomPredictResponse {
    private boolean success;
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

    public boolean isSuccess() { return success; }

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

        public String getId() { return id; }
        public String getName() { return name; }
        public String getSpecialty() { return specialty; }
    }
}

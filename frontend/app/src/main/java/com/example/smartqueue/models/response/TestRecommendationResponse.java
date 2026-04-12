package com.example.smartqueue.models.response;

import java.util.List;

/**
 * Response from POST /test-recommendations on the ML service
 * (proxied through Node backend if needed).
 */
public class TestRecommendationResponse {
    private List<Recommendation> recommendations;
    private String source;
    private boolean lowConfidence;

    public List<Recommendation> getRecommendations() { return recommendations; }
    public String getSource() { return source; }
    public boolean isLowConfidence() { return lowConfidence; }

    public static class Recommendation {
        private String test;
        private String rationale;
        private String urgency;

        public String getTest() { return test; }
        public String getRationale() { return rationale; }
        public String getUrgency() { return urgency; }
    }
}

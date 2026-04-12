package com.example.smartqueue.models.response;

import java.util.List;

public class MlOpsLogsResponse {
    private boolean success;
    private Summary summary;
    private List<LogEntry> logs;

    public boolean isSuccess() { return success; }
    public Summary getSummary() { return summary; }
    public List<LogEntry> getLogs() { return logs; }

    public static class Summary {
        private int totalRequests;
        private int successfulRequests;
        private int failedRequests;
        private int retryEvents;
        private int retryRecoveredRequests;
        private double successRate;
        private String lastSuccessAt;
        private String lastFailureAt;
        private String lastFailureMessage;
        private Integer lastFailureStatus;
        private String lastFailureCode;

        public int getTotalRequests() { return totalRequests; }
        public int getSuccessfulRequests() { return successfulRequests; }
        public int getFailedRequests() { return failedRequests; }
        public int getRetryEvents() { return retryEvents; }
        public int getRetryRecoveredRequests() { return retryRecoveredRequests; }
        public double getSuccessRate() { return successRate; }
        public String getLastSuccessAt() { return lastSuccessAt; }
        public String getLastFailureAt() { return lastFailureAt; }
        public String getLastFailureMessage() { return lastFailureMessage; }
        public Integer getLastFailureStatus() { return lastFailureStatus; }
        public String getLastFailureCode() { return lastFailureCode; }
    }

    public static class LogEntry {
        private String timestamp;
        private String operation;
        private String source;
        private String url;
        private String result;
        private int attempt;
        private int maxAttempts;
        private boolean willRetry;
        private Integer retryDelayMs;
        private Integer status;
        private String errorCode;
        private String errorMessage;
        private Integer latencyMs;

        public String getTimestamp() { return timestamp; }
        public String getOperation() { return operation; }
        public String getSource() { return source; }
        public String getUrl() { return url; }
        public String getResult() { return result; }
        public int getAttempt() { return attempt; }
        public int getMaxAttempts() { return maxAttempts; }
        public boolean isWillRetry() { return willRetry; }
        public Integer getRetryDelayMs() { return retryDelayMs; }
        public Integer getStatus() { return status; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public Integer getLatencyMs() { return latencyMs; }
    }
}

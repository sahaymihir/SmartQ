const MAX_LOG_ENTRIES = 250;
const mlOpsLogs = [];

const clampLimit = (value, fallback = 20, max = 100) => {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }
  return Math.min(Math.floor(parsed), max);
};

const addMlOpsLog = (entry = {}) => {
  const enriched = {
    timestamp: entry.timestamp || new Date().toISOString(),
    operation: entry.operation || 'ml_request',
    source: entry.source || 'backend',
    url: entry.url || '',
    result: entry.result || 'unknown',
    attempt: Number(entry.attempt || 1),
    maxAttempts: Number(entry.maxAttempts || 1),
    willRetry: Boolean(entry.willRetry),
    retryDelayMs: entry.retryDelayMs == null ? null : Number(entry.retryDelayMs),
    status: entry.status == null ? null : Number(entry.status),
    errorCode: entry.errorCode || null,
    errorMessage: entry.errorMessage || null,
    latencyMs: entry.latencyMs == null ? null : Number(entry.latencyMs),
  };

  mlOpsLogs.unshift(enriched);
  if (mlOpsLogs.length > MAX_LOG_ENTRIES) {
    mlOpsLogs.length = MAX_LOG_ENTRIES;
  }

  return enriched;
};

const getMlOpsLogs = (limit) => mlOpsLogs.slice(0, clampLimit(limit));

const clearMlOpsLogs = () => {
  mlOpsLogs.length = 0;
};

const getMlOpsSummary = () => {
  if (!mlOpsLogs.length) {
    return {
      totalRequests: 0,
      successfulRequests: 0,
      failedRequests: 0,
      retryEvents: 0,
      retryRecoveredRequests: 0,
      successRate: 0,
      lastSuccessAt: null,
      lastFailureAt: null,
      lastFailureMessage: null,
      lastFailureStatus: null,
      lastFailureCode: null,
    };
  }

  const finalEvents = mlOpsLogs.filter((log) => log.result === 'success' || log.result === 'failure');

  const totalRequests = finalEvents.length;
  const successfulRequests = finalEvents.filter((log) => log.result === 'success').length;
  const failedRequests = finalEvents.filter((log) => log.result === 'failure').length;
  const retryRecoveredRequests = finalEvents.filter(
    (log) => log.result === 'success' && Number(log.attempt || 1) > 1
  ).length;

  const retryEvents = mlOpsLogs.filter((log) => log.willRetry || log.result === 'retrying').length;
  const lastSuccess = mlOpsLogs.find((log) => log.result === 'success');
  const lastFailure = mlOpsLogs.find((log) => log.result === 'failure');

  return {
    totalRequests,
    successfulRequests,
    failedRequests,
    retryEvents,
    retryRecoveredRequests,
    successRate: totalRequests ? Number(((successfulRequests / totalRequests) * 100).toFixed(1)) : 0,
    lastSuccessAt: lastSuccess?.timestamp || null,
    lastFailureAt: lastFailure?.timestamp || null,
    lastFailureMessage: lastFailure?.errorMessage || null,
    lastFailureStatus: lastFailure?.status || null,
    lastFailureCode: lastFailure?.errorCode || null,
  };
};

module.exports = {
  addMlOpsLog,
  clearMlOpsLogs,
  getMlOpsLogs,
  getMlOpsSummary,
};

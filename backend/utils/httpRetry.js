const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
const { addMlOpsLog } = require('../store/mlOpsLogStore');

const RETRYABLE_STATUS_CODES = new Set([408, 425, 429, 500, 502, 503, 504]);
const RETRYABLE_ERROR_CODES = new Set([
  'ECONNABORTED',
  'ECONNRESET',
  'ECONNREFUSED',
  'EHOSTUNREACH',
  'ENETUNREACH',
  'ENOTFOUND',
  'ETIMEDOUT',
]);

const isRetryableAxiosError = (error) => {
  if (!error) return false;

  const status = Number(error.response?.status);
  if (RETRYABLE_STATUS_CODES.has(status)) {
    return true;
  }

  const code = String(error.code || '').toUpperCase();
  return RETRYABLE_ERROR_CODES.has(code);
};

const postWithRetry = async (
  axios,
  url,
  payload,
  {
    timeout,
    retries = 2,
    initialDelayMs = 500,
    maxDelayMs = 3000,
    operation = 'ml_request',
    source = 'backend',
  } = {}
) => {
  let attempt = 0;
  let lastError;

  while (attempt <= retries) {
    const startedAt = Date.now();
    try {
      const response = await axios.post(url, payload, { timeout });
      addMlOpsLog({
        operation,
        source,
        url,
        result: 'success',
        attempt: attempt + 1,
        maxAttempts: retries + 1,
        status: response.status,
        latencyMs: Date.now() - startedAt,
      });
      return response;
    } catch (error) {
      lastError = error;
      const willRetry = attempt < retries && isRetryableAxiosError(error);
      const retryDelayMs = willRetry
        ? Math.min(maxDelayMs, initialDelayMs * (2 ** attempt))
        : null;

      addMlOpsLog({
        operation,
        source,
        url,
        result: willRetry ? 'retrying' : 'failure',
        attempt: attempt + 1,
        maxAttempts: retries + 1,
        willRetry,
        retryDelayMs,
        status: error.response?.status,
        errorCode: error.code || null,
        errorMessage: error.response?.data?.detail || error.message || 'Request failed',
        latencyMs: Date.now() - startedAt,
      });

      if (attempt >= retries || !isRetryableAxiosError(error)) {
        throw error;
      }

      await sleep(retryDelayMs);
      attempt += 1;
    }
  }

  throw lastError;
};

module.exports = {
  isRetryableAxiosError,
  postWithRetry,
};

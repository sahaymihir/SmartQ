const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

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
  } = {}
) => {
  let attempt = 0;
  let lastError;

  while (attempt <= retries) {
    try {
      return await axios.post(url, payload, { timeout });
    } catch (error) {
      lastError = error;
      if (attempt >= retries || !isRetryableAxiosError(error)) {
        throw error;
      }

      const backoff = Math.min(maxDelayMs, initialDelayMs * (2 ** attempt));
      await sleep(backoff);
      attempt += 1;
    }
  }

  throw lastError;
};

module.exports = {
  isRetryableAxiosError,
  postWithRetry,
};

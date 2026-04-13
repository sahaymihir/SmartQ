const MENTAL_STATUS_TO_GCS = Object.freeze({
  alert: 15,
  drowsy: 13,
  unresponsive: 8,
});

const toTrimmedLower = (value) => {
  if (value === undefined || value === null) {
    return '';
  }
  return String(value).trim().toLowerCase();
};

const normalizeMentalStatusTriage = (value) => {
  const normalized = toTrimmedLower(value);
  if (!normalized) {
    return undefined;
  }

  if (
    normalized.startsWith('alert') ||
    normalized.includes('fully alert') ||
    normalized.includes('awake') ||
    normalized.includes('normal')
  ) {
    return 'alert';
  }

  if (
    normalized.startsWith('drows') ||
    normalized.includes('confus') ||
    normalized.includes('mumbling')
  ) {
    return 'drowsy';
  }

  if (
    normalized.startsWith('unresponsive') ||
    normalized.includes('unconscious') ||
    normalized.includes('not responding')
  ) {
    return 'unresponsive';
  }

  return normalized;
};

const toFiniteNumber = (value) => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
};

const deriveGcsTotal = ({ mental_status_triage, gcs_total } = {}) => {
  const normalizedMentalStatus = normalizeMentalStatusTriage(mental_status_triage);
  if (normalizedMentalStatus && MENTAL_STATUS_TO_GCS[normalizedMentalStatus] != null) {
    return MENTAL_STATUS_TO_GCS[normalizedMentalStatus];
  }

  const legacyGcs = toFiniteNumber(gcs_total);
  if (legacyGcs == null) {
    return undefined;
  }

  const roundedGcs = Math.round(legacyGcs);
  if (roundedGcs < 3 || roundedGcs > 15) {
    return undefined;
  }

  return roundedGcs;
};

const scoreRespiratoryRate = (respiratoryRate) => {
  if (respiratoryRate <= 8) return 3;
  if (respiratoryRate <= 11) return 1;
  if (respiratoryRate <= 20) return 0;
  if (respiratoryRate <= 24) return 2;
  return 3;
};

const scoreSpO2 = (spo2) => {
  if (spo2 <= 91) return 3;
  if (spo2 <= 93) return 2;
  if (spo2 <= 95) return 1;
  return 0;
};

const scoreTemperature = (temperatureC) => {
  if (temperatureC <= 35.0) return 3;
  if (temperatureC <= 36.0) return 1;
  if (temperatureC <= 38.0) return 0;
  if (temperatureC <= 39.0) return 1;
  return 2;
};

const scoreSystolicBp = (systolicBp) => {
  if (systolicBp <= 90) return 3;
  if (systolicBp <= 100) return 2;
  if (systolicBp <= 110) return 1;
  if (systolicBp <= 219) return 0;
  return 3;
};

const scoreHeartRate = (heartRate) => {
  if (heartRate <= 40) return 3;
  if (heartRate <= 50) return 1;
  if (heartRate <= 90) return 0;
  if (heartRate <= 110) return 1;
  if (heartRate <= 130) return 2;
  return 3;
};

const scoreAlertness = ({ mental_status_triage, gcs_total } = {}) => {
  const normalizedMentalStatus = normalizeMentalStatusTriage(mental_status_triage);
  if (normalizedMentalStatus) {
    return normalizedMentalStatus === 'alert' ? 0 : 3;
  }

  const gcsTotal = deriveGcsTotal({ gcs_total });
  if (gcsTotal == null) {
    return undefined;
  }

  return gcsTotal >= 15 ? 0 : 3;
};

const calculateNews2Score = (payload = {}) => {
  const respiratoryRate = toFiniteNumber(payload.respiratory_rate);
  const spo2 = toFiniteNumber(payload.spo2);
  const temperatureC = toFiniteNumber(payload.temperature_c);
  const systolicBp = toFiniteNumber(payload.systolic_bp);
  const heartRate = toFiniteNumber(payload.heart_rate);
  const alertnessScore = scoreAlertness(payload);

  if (
    respiratoryRate == null ||
    spo2 == null ||
    temperatureC == null ||
    systolicBp == null ||
    heartRate == null ||
    alertnessScore == null
  ) {
    return undefined;
  }

  return (
    scoreRespiratoryRate(respiratoryRate) +
    scoreSpO2(spo2) +
    scoreTemperature(temperatureC) +
    scoreSystolicBp(systolicBp) +
    scoreHeartRate(heartRate) +
    alertnessScore
  );
};

const deriveClinicalScores = (payload = {}) => {
  const mentalStatus = normalizeMentalStatusTriage(payload.mental_status_triage);
  const gcsTotal = deriveGcsTotal({
    mental_status_triage: mentalStatus,
    gcs_total: payload.gcs_total,
  });
  const news2Score = calculateNews2Score({
    ...payload,
    mental_status_triage: mentalStatus,
    gcs_total: gcsTotal,
  });

  return {
    mental_status_triage: mentalStatus ?? null,
    gcs_total: gcsTotal ?? null,
    news2_score: news2Score ?? null,
  };
};

const withDerivedClinicalScores = (payload = {}) => ({
  ...payload,
  ...deriveClinicalScores(payload),
});

module.exports = {
  MENTAL_STATUS_TO_GCS,
  normalizeMentalStatusTriage,
  deriveGcsTotal,
  calculateNews2Score,
  deriveClinicalScores,
  withDerivedClinicalScores,
};

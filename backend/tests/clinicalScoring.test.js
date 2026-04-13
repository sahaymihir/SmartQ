const assert = require('assert');

const {
  calculateNews2Score,
  deriveClinicalScores,
  deriveGcsTotal,
  normalizeMentalStatusTriage,
} = require('../utils/clinicalScoring');

assert.strictEqual(normalizeMentalStatusTriage('Alert'), 'alert');
assert.strictEqual(normalizeMentalStatusTriage('Drowsy'), 'drowsy');
assert.strictEqual(normalizeMentalStatusTriage('Unresponsive'), 'unresponsive');

assert.strictEqual(deriveGcsTotal({ mental_status_triage: 'alert' }), 15);
assert.strictEqual(deriveGcsTotal({ mental_status_triage: 'drowsy' }), 13);
assert.strictEqual(deriveGcsTotal({ mental_status_triage: 'unresponsive' }), 8);

assert.strictEqual(
  calculateNews2Score({
    mental_status_triage: 'alert',
    respiratory_rate: 18,
    spo2: 98,
    temperature_c: 37.0,
    systolic_bp: 120,
    heart_rate: 80,
  }),
  0
);

assert.strictEqual(
  calculateNews2Score({
    mental_status_triage: 'alert',
    respiratory_rate: 23,
    spo2: 94,
    temperature_c: 38.5,
    systolic_bp: 105,
    heart_rate: 95,
  }),
  6
);

assert.strictEqual(
  calculateNews2Score({
    mental_status_triage: 'unresponsive',
    respiratory_rate: 30,
    spo2: 90,
    temperature_c: 34.5,
    systolic_bp: 85,
    heart_rate: 140,
  }),
  18
);

assert.deepStrictEqual(
  deriveClinicalScores({
    mental_status_triage: 'Alert',
    heart_rate: 88,
    spo2: 98,
  }),
  {
    mental_status_triage: 'alert',
    gcs_total: 15,
    news2_score: null,
  }
);

console.log('clinicalScoring tests passed');

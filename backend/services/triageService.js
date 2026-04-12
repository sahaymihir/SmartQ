const axios = require('axios');
const { getPriorityLabel } = require('../utils/queueHelpers');

const TRIAGE_API_URL = process.env.TRIAGE_API_URL || 'http://localhost:8000';
const TRIAGE_MODEL_VERSION = process.env.TRIAGE_MODEL_VERSION || 'v3';
const TRIAGE_TIMEOUT_MS = Number(process.env.TRIAGE_TIMEOUT_MS || 5000);

const TRIAGE_PAYLOAD_FIELDS = [
  'news2_score',
  'gcs_total',
  'pain_score',
  'spo2',
  'temperature_c',
  'respiratory_rate',
  'heart_rate',
  'mental_status_triage',
  'diastolic_bp',
  'systolic_bp',
  'height_cm',
  'weight_kg',
  'age',
  'arrival_hour',
  'chief_complaint_system',
  'num_comorbidities',
  'num_active_medications',
  'arrival_month',
  'pain_location',
  'arrival_day',
  'transport_origin',
  'language',
  'site_id',
  'arrival_mode',
  'arrival_season',
  'insurance_type',
  'shift',
  'num_prior_admissions_12m',
  'num_prior_ed_visits_12m',
  'age_group',
  'sex',
];

const SNAPSHOT_FIELDS = [
  'symptoms',
  'symptomsVoiceTranscript',
  'intakeLanguage',
  ...TRIAGE_PAYLOAD_FIELDS,
];

const mapPriorityClassToScore = (priorityClass) => {
  if (priorityClass == null) return 5;
  if (priorityClass <= 2) return 10;
  if (priorityClass === 3) return 7;
  return 5;
};

/**
 * Normalise the symptom text from multimodal intake fields.
 * Priority order: symptomsText (typed) → symptomsVoiceTranscript → legacy symptoms field.
 */
const getCanonicalSymptoms = (requestBody = {}) =>
  requestBody.symptomsText ||
  requestBody.symptomsVoiceTranscript ||
  requestBody.symptoms ||
  '';

const buildTriagePayload = (patient, requestBody = {}) => {
  const payload = {
    age: patient.age,
  };

  const canonicalSymptoms = getCanonicalSymptoms(requestBody);
  if (canonicalSymptoms) {
    payload.symptoms = canonicalSymptoms;
  }

  for (const field of TRIAGE_PAYLOAD_FIELDS) {
    if (field === 'age') {
      continue;
    }
    if (requestBody[field] !== undefined && requestBody[field] !== null && requestBody[field] !== '') {
      payload[field] = requestBody[field];
    }
  }

  return payload;
};

const buildVisitSnapshot = (patient, requestBody = {}) => {
  const snapshot = {
    age: patient.age,
  };

  for (const field of SNAPSHOT_FIELDS) {
    if (requestBody[field] !== undefined && requestBody[field] !== null && requestBody[field] !== '') {
      snapshot[field] = requestBody[field];
    }
  }

  // Persist normalised canonical symptoms string in the snapshot.
  const canonicalSymptoms = getCanonicalSymptoms(requestBody);
  if (canonicalSymptoms) {
    snapshot.symptoms = canonicalSymptoms;
  }

  return snapshot;
};

const getAgeBaselineScore = (age) => (Number(age) >= 60 ? 10 : 5);

// ─── Composite priority helpers ───────────────────────────────
// Each component contributes an additive weight to the final score
// while the guardrail ensures the result is never below the age baseline.

const SYMPTOM_SEVERITY_KEYWORDS = [
  'chest pain',
  'difficulty breathing',
  'breathlessness',
  'unconscious',
  'seizure',
  'stroke',
  'severe bleeding',
  'heart attack',
  'high fever',
  'extreme pain',
];

const computeSymptomNlpBoost = (symptomsText) => {
  if (!symptomsText) return 0;
  const lower = symptomsText.toLowerCase();
  const hits = SYMPTOM_SEVERITY_KEYWORDS.filter((kw) => lower.includes(kw));
  // Each red-flag keyword contributes 0.5 up to a maximum of 2 points.
  return Math.min(hits.length * 0.5, 2);
};

const buildPriorityComponents = ({
  ageBasedPriorityScore,
  mlPriorityScore,
  symptomsText,
  ocrFlags,
  clinicianOverride,
}) => ({
  age: ageBasedPriorityScore,
  triage: mlPriorityScore != null ? mlPriorityScore : 0,
  symptomNlp: computeSymptomNlpBoost(symptomsText),
  ocrFlags: ocrFlags != null ? ocrFlags : 0,
  clinicianOverride: clinicianOverride != null ? clinicianOverride : 0,
});

const computePriorityFinalScore = (components, baseScore) => {
  // Base score is already max(age, ml). Layer symptom NLP and OCR flags on top.
  return Math.min(
    10,
    baseScore + (components.symptomNlp || 0) + (components.ocrFlags || 0) + (components.clinicianOverride || 0)
  );
};

const buildDecisionTrace = ({
  priorityFinalScore,
  components,
  triageSource,
  overrideReason,
  triageLowConfidence,
}) => {
  const parts = [
    `source=${triageSource}`,
    `age=${components.age}`,
    `triage=${components.triage}`,
    `nlpBoost=${components.symptomNlp}`,
    `ocrFlags=${components.ocrFlags}`,
    `override=${components.clinicianOverride}`,
    `finalScore=${priorityFinalScore}`,
    `overrideReason=${overrideReason}`,
  ];
  if (triageLowConfidence) parts.push('lowConfidence=true');
  return parts.join(';');
};

// ─────────────────────────────────────────────────────────────

const buildFallbackDecision = (patient, requestBody = {}) => {
  const ageBasedPriorityScore = getAgeBaselineScore(patient.age);
  const symptomsText = getCanonicalSymptoms(requestBody);
  const components = buildPriorityComponents({
    ageBasedPriorityScore,
    mlPriorityScore: null,
    symptomsText,
    ocrFlags: 0,
    clinicianOverride: 0,
  });
  const priorityFinalScore = computePriorityFinalScore(components, ageBasedPriorityScore);
  const overrideReason = ageBasedPriorityScore >= 10 ? 'age_gte_60' : 'standard_age_rule';

  return {
    ageBasedPriorityScore,
    mlPriorityScore: null,
    priorityScore: priorityFinalScore,
    priorityLabel: getPriorityLabel(priorityFinalScore),
    triagePriorityClass: null,
    triageConfidence: 0,
    triageLowConfidence: false,
    triageRecommendation: ageBasedPriorityScore >= 10
      ? 'Age-based high-priority routing'
      : 'Standard queue routing',
    triageAllClassProbs: {},
    triageModelVersion: 'rules-v1',
    triageSource: 'age_rule_fallback',
    overrideReason,
    manualReviewRequired: false,
    aiConfidence: 0,
    priorityComponents: components,
    priorityFinalScore,
    priorityDecisionTrace: buildDecisionTrace({
      priorityFinalScore,
      components,
      triageSource: 'age_rule_fallback',
      overrideReason,
      triageLowConfidence: false,
    }),
  };
};

const determineOverrideReason = ({
  ageBasedPriorityScore,
  mlPriorityScore,
  triagePriorityClass,
  triageLowConfidence,
}) => {
  if (triageLowConfidence) {
    return 'low_confidence_guardrail';
  }
  if (mlPriorityScore > ageBasedPriorityScore) {
    return `ml_priority_class_${triagePriorityClass}_override`;
  }
  if (ageBasedPriorityScore > mlPriorityScore) {
    return ageBasedPriorityScore >= 10 ? 'age_gte_60_guardrail' : 'age_guardrail';
  }
  return 'age_and_ml_aligned';
};

const determineTriageDecision = async (patient, requestBody = {}) => {
  const fallback = buildFallbackDecision(patient, requestBody);
  const triagePayload = buildTriagePayload(patient, requestBody);
  const symptomsText = getCanonicalSymptoms(requestBody);

  try {
    const response = await axios.post(
      `${TRIAGE_API_URL}/predict`,
      triagePayload,
      { timeout: TRIAGE_TIMEOUT_MS }
    );

    const data = response.data || {};
    if (!data.priority_class) {
      return fallback;
    }

    const ageBasedPriorityScore = getAgeBaselineScore(patient.age);
    const mlPriorityScore = mapPriorityClassToScore(data.priority_class);
    const baseScore = Math.max(ageBasedPriorityScore, mlPriorityScore);
    const triageLowConfidence = Boolean(data.low_confidence);
    const overrideReason = determineOverrideReason({
      ageBasedPriorityScore,
      mlPriorityScore,
      triagePriorityClass: data.priority_class,
      triageLowConfidence,
    });

    const components = buildPriorityComponents({
      ageBasedPriorityScore,
      mlPriorityScore,
      symptomsText,
      ocrFlags: 0,
      clinicianOverride: 0,
    });
    const priorityFinalScore = computePriorityFinalScore(components, baseScore);
    const triageSource = triageLowConfidence ? 'ml_guardrailed_low_confidence' : 'ml_v3';

    return {
      ageBasedPriorityScore,
      mlPriorityScore,
      priorityScore: priorityFinalScore,
      priorityLabel: getPriorityLabel(priorityFinalScore),
      triagePriorityClass: data.priority_class,
      triageConfidence: Number(data.confidence || 0),
      triageLowConfidence,
      triageRecommendation: data.recommendation || '',
      triageAllClassProbs: data.all_class_probs || {},
      triageModelVersion: data.model_version || TRIAGE_MODEL_VERSION,
      triageSource,
      overrideReason,
      manualReviewRequired: triageLowConfidence,
      aiConfidence: Number(data.confidence || 0),
      priorityComponents: components,
      priorityFinalScore,
      priorityDecisionTrace: buildDecisionTrace({
        priorityFinalScore,
        components,
        triageSource,
        overrideReason,
        triageLowConfidence,
      }),
    };
  } catch (error) {
    console.warn('⚠️  Triage ML unavailable, using age-based fallback:', error.message);
    return fallback;
  }
};

module.exports = {
  TRIAGE_PAYLOAD_FIELDS,
  buildTriagePayload,
  buildVisitSnapshot,
  determineTriageDecision,
  getAgeBaselineScore,
  mapPriorityClassToScore,
};

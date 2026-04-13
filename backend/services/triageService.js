const axios = require('axios');
const { getPriorityLabel } = require('../utils/queueHelpers');
const { runPatientFlow } = require('./patientFlowService');
const { postWithRetry } = require('../utils/httpRetry');
const { withDerivedClinicalScores } = require('../utils/clinicalScoring');

const TRIAGE_API_URL = process.env.TRIAGE_API_URL || 'http://localhost:8000';
const TRIAGE_MODEL_VERSION = process.env.TRIAGE_MODEL_VERSION || 'v3';
const TRIAGE_TIMEOUT_MS = Number(process.env.TRIAGE_TIMEOUT_MS || 10000);

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
  const enrichedBody = withDerivedClinicalScores(requestBody);
  const payload = {
    age: patient.age,
  };

  for (const field of TRIAGE_PAYLOAD_FIELDS) {
    if (field === 'age') {
      continue;
    }
    if (enrichedBody[field] !== undefined && enrichedBody[field] !== null && enrichedBody[field] !== '') {
      payload[field] = enrichedBody[field];
    }
  }

  return payload;
};

const buildVisitSnapshot = (patient, requestBody = {}) => {
  const enrichedBody = withDerivedClinicalScores(requestBody);
  const snapshot = {
    age: patient.age,
  };

  for (const field of SNAPSHOT_FIELDS) {
    if (enrichedBody[field] !== undefined && enrichedBody[field] !== null && enrichedBody[field] !== '') {
      snapshot[field] = enrichedBody[field];
    }
  }

  // Persist normalised canonical symptoms string in the snapshot.
  const canonicalSymptoms = getCanonicalSymptoms(enrichedBody);
  if (canonicalSymptoms) {
    snapshot.symptoms = canonicalSymptoms;
  }

  return snapshot;
};

const buildPatientFlowPayload = (patient, requestBody = {}) => {
  const enrichedBody = withDerivedClinicalScores(requestBody);
  const symptoms = getCanonicalSymptoms(enrichedBody);
  if (!symptoms) {
    return null;
  }

  const payload = {
    symptoms,
    age: patient.age,
    sex: enrichedBody.sex,
    mental_status_triage: enrichedBody.mental_status_triage,
    chief_complaint_system: enrichedBody.chief_complaint_system,
    language: enrichedBody.language || enrichedBody.intakeLanguage,
    temperature_c: enrichedBody.temperature_c,
    pain_score: enrichedBody.pain_score,
    spo2: enrichedBody.spo2,
    respiratory_rate: enrichedBody.respiratory_rate,
    heart_rate: enrichedBody.heart_rate,
    systolic_bp: enrichedBody.systolic_bp,
    diastolic_bp: enrichedBody.diastolic_bp,
    gcs_total: enrichedBody.gcs_total,
    news2_score: enrichedBody.news2_score,
  };

  return Object.fromEntries(
    Object.entries(payload).filter(([, value]) => value !== undefined && value !== null && value !== '')
  );
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
  const derivedChiefComplaintSystem = requestBody.chief_complaint_system || 'other';
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
    modelPriorityClass: null,
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
    derivedChiefComplaintSystem,
    queueSelectedRoute: '',
    queueRouteType: '',
    queueRationale: '',
    queueCurrentLength: 0,
    queueAvailableDoctors: 0,
    queueAvgWaitMinutes: null,
    routingLane: 'normal',
    requiresImmediateReview: false,
    escalationReason: '',
    safetyMatches: [],
    priorityComponents: components,
    priorityFinalScore,
    priorityDecisionTrace: buildDecisionTrace({
      priorityFinalScore,
      components,
      triageSource: 'age_rule_fallback',
      overrideReason,
      triageLowConfidence: false,
    }),
    testRecommendations: [],
  };
};

const shouldUseImmediateReviewLane = (priorityClass, safetyMatches = []) => {
  if (priorityClass === 1) {
    return true;
  }

  return safetyMatches.some((match) => {
    const severity = String(match?.severity || '').toLowerCase();
    return Number(match?.forcedPriorityClass) === 1 || severity === 'critical';
  });
};

const buildEscalationReason = (priorityClass, safetyMatches = []) => {
  const strongestMatch = safetyMatches.find((match) => {
    const severity = String(match?.severity || '').toLowerCase();
    return Number(match?.forcedPriorityClass) === 1 || severity === 'critical';
  });

  if (strongestMatch?.ruleId) {
    return strongestMatch.ruleId;
  }

  if (priorityClass === 1) {
    return 'ktas_1_immediate_review';
  }

  return '';
};

const buildPatientFlowDecision = (patient, requestBody = {}, flow = {}) => {
  const symptomsText = getCanonicalSymptoms(requestBody);
  const ageBasedPriorityScore = getAgeBaselineScore(patient.age);
  const safetyMatches = Array.isArray(flow.safety) ? flow.safety : [];
  const operationalPriorityClass =
    flow.priority?.guardrailedPriorityClass != null
      ? Number(flow.priority.guardrailedPriorityClass)
      : null;
  const rawModelPriorityClass =
    flow.priority?.modelPriorityClass != null
      ? Number(flow.priority.modelPriorityClass)
      : null;
  const mlPriorityScore = mapPriorityClassToScore(operationalPriorityClass);
  const baseScore = Math.max(ageBasedPriorityScore, mlPriorityScore);
  const triageLowConfidence = Boolean(flow.priority?.lowConfidence);
  const requiresImmediateReview = shouldUseImmediateReviewLane(operationalPriorityClass, safetyMatches);
  const escalationReason = requiresImmediateReview
    ? buildEscalationReason(operationalPriorityClass, safetyMatches)
    : '';
  const overrideReason = safetyMatches.length > 0
    ? `safety_${escalationReason || 'guardrail'}`
    : determineOverrideReason({
        ageBasedPriorityScore,
        mlPriorityScore,
        triagePriorityClass: operationalPriorityClass,
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
  const triageSource = flow.priority?.source || flow.flowSource || 'patient_flow_v1';

  // Extract test recommendations from the ML flow (used for high-wait suggestions)
  const testRecommendations = Array.isArray(flow.tests?.recommendations)
    ? flow.tests.recommendations
    : [];

  return {
    ageBasedPriorityScore,
    mlPriorityScore,
    modelPriorityClass: rawModelPriorityClass,
    derivedChiefComplaintSystem: flow.derivedChiefComplaintSystem || requestBody.chief_complaint_system || 'other',
    queueSelectedRoute: flow.queueAssignment?.selectedRoute || '',
    queueRouteType: flow.queueAssignment?.routeType || '',
    queueRationale: flow.queueAssignment?.rationale || '',
    queueCurrentLength: Number(flow.queueAssignment?.currentQueueLength || 0),
    queueAvailableDoctors: Number(flow.queueAssignment?.availableDoctors || 0),
    queueAvgWaitMinutes:
      flow.queueAssignment?.avgWaitMinutes == null
        ? null
        : Number(flow.queueAssignment.avgWaitMinutes || 0),
    priorityScore: priorityFinalScore,
    priorityLabel: getPriorityLabel(priorityFinalScore),
    triagePriorityClass: operationalPriorityClass,
    triageConfidence: Number(flow.priority?.modelConfidence || 0),
    triageLowConfidence,
    triageRecommendation:
      flow.priority?.guardrailedRecommendation ||
      flow.priority?.modelRecommendation ||
      '',
    triageAllClassProbs: flow.priority?.allClassProbs || {},
    triageModelVersion: flow.flowSource || TRIAGE_MODEL_VERSION,
    triageSource,
    overrideReason,
    manualReviewRequired: triageLowConfidence,
    aiConfidence: Number(flow.priority?.modelConfidence || 0),
    routingLane: requiresImmediateReview ? 'immediate_review' : 'normal',
    requiresImmediateReview,
    escalationReason,
    safetyMatches,
    priorityComponents: components,
    priorityFinalScore,
    priorityDecisionTrace: buildDecisionTrace({
      priorityFinalScore,
      components,
      triageSource,
      overrideReason,
      triageLowConfidence,
    }),
    testRecommendations,
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
  const patientFlowPayload = buildPatientFlowPayload(patient, requestBody);

  if (patientFlowPayload) {
    try {
      const flow = await runPatientFlow(patientFlowPayload);
      if (flow?.priority?.guardrailedPriorityClass != null) {
        return buildPatientFlowDecision(patient, requestBody, flow);
      }
    } catch (error) {
      console.warn('⚠️  Patient flow unavailable, falling back to triage-only scoring:', error.message);
    }
  }

  try {
    const response = await postWithRetry(
      axios,
      `${TRIAGE_API_URL}/predict`,
      triagePayload,
      {
        timeout: TRIAGE_TIMEOUT_MS,
        retries: Number(process.env.TRIAGE_RETRY_COUNT || 2),
        initialDelayMs: Number(process.env.TRIAGE_RETRY_DELAY_MS || 500),
        operation: 'triage_predict',
        source: 'triageService',
      }
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
    const requiresImmediateReview = data.priority_class === 1;
    const escalationReason = requiresImmediateReview ? 'ktas_1_immediate_review' : '';

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
      modelPriorityClass: data.priority_class,
      derivedChiefComplaintSystem: requestBody.chief_complaint_system || 'other',
      queueSelectedRoute: '',
      queueRouteType: '',
      queueRationale: '',
      queueCurrentLength: 0,
      queueAvailableDoctors: 0,
      queueAvgWaitMinutes: null,
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
      routingLane: requiresImmediateReview ? 'immediate_review' : 'normal',
      requiresImmediateReview,
      escalationReason,
      safetyMatches: [],
      priorityComponents: components,
      priorityFinalScore,
      priorityDecisionTrace: buildDecisionTrace({
        priorityFinalScore,
        components,
        triageSource,
        overrideReason,
        triageLowConfidence,
      }),
      testRecommendations: [],
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

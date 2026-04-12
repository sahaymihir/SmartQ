const axios = require('axios');
const { postWithRetry } = require('../utils/httpRetry');

const {
  buildAlternativeSpecialists,
  buildSpecialtyScores,
  routeToSupportedSpecialty,
} = require('./specialtyService');

const ML_SERVICE_URL = process.env.SPECIALTY_API_URL || process.env.TRIAGE_API_URL || 'http://localhost:8000';
const FLOW_TIMEOUT_MS = Number(process.env.PATIENT_FLOW_TIMEOUT_MS || process.env.TRIAGE_TIMEOUT_MS || 12000);
const PATIENT_FLOW_SOURCE = 'patient_flow_v1';

const normalizeSpecialtyScores = (specialty = {}, symptoms = '') => {
  const primarySpecialist = specialty.primarySpecialist || 'General Practice';
  const routedSpecialty = routeToSupportedSpecialty(primarySpecialist, specialty.routedSpecialty);
  const specialtyScores = buildSpecialtyScores(specialty.alternatives || []);

  if (!specialtyScores.length) {
    specialtyScores.push({
      specialty: primarySpecialist,
      routedSpecialty,
      score: Number(specialty.confidence || 0),
      matchedKeywords: Array.isArray(specialty.extractedSignals) ? specialty.extractedSignals : [],
      matchedSignals: Array.isArray(specialty.extractedSignals) ? specialty.extractedSignals : [],
    });
  }

  return {
    primarySpecialist,
    routedSpecialty,
    confidence: Number(specialty.confidence || 0),
    lowConfidence: Boolean(specialty.lowConfidence),
    normalizedSymptoms: specialty.normalizedSymptoms || symptoms,
    extractedSignals: Array.isArray(specialty.extractedSignals) ? specialty.extractedSignals : [],
    specialtyScores,
    alternativeSpecialists: buildAlternativeSpecialists(specialtyScores, primarySpecialist),
    reasoning: specialty.reasoning || '',
    modelSource: specialty.modelSource || 'specialty_hybrid_v1',
  };
};

const normalizeQueueAssignment = (queueAssignment = {}) => ({
  selectedRoute: queueAssignment.selectedRoute || 'General OPD',
  routeType: queueAssignment.routeType || 'primary',
  rationale: queueAssignment.rationale || 'Queue assignment data unavailable.',
  currentQueueLength: Number(queueAssignment.currentQueueLength || 0),
  availableDoctors: Number(queueAssignment.availableDoctors || 0),
  avgWaitMinutes:
    queueAssignment.avgWaitMinutes == null ? null : Number(queueAssignment.avgWaitMinutes || 0),
});

const normalizePriority = (priority = {}) => ({
  modelPriorityClass:
    priority.modelPriorityClass == null ? null : Number(priority.modelPriorityClass),
  modelConfidence: Number(priority.modelConfidence || 0),
  lowConfidence: Boolean(priority.lowConfidence),
  modelRecommendation: priority.modelRecommendation || '',
  allClassProbs: priority.allClassProbs || {},
  guardrailedPriorityClass:
    priority.guardrailedPriorityClass == null ? null : Number(priority.guardrailedPriorityClass),
  guardrailedRecommendation: priority.guardrailedRecommendation || '',
  source: priority.source || 'ml_v3',
});

const normalizeSafetyMatches = (safety = []) =>
  Array.isArray(safety)
    ? safety.map((match) => ({
        ruleId: match.ruleId || 'unknown_rule',
        severity: match.severity || 'info',
        forcedPriorityClass:
          match.forcedPriorityClass == null ? null : Number(match.forcedPriorityClass),
        preferredRoute: match.preferredRoute || null,
        rationale: match.rationale || '',
      }))
    : [];

const normalizeTests = (tests = {}) => ({
  recommendations: Array.isArray(tests.recommendations)
    ? tests.recommendations.map((entry) => ({
        test: entry.test || 'Recommended test',
        rationale: entry.rationale || '',
        urgency: entry.urgency || 'routine',
      }))
    : [],
  source: tests.source || 'rule_based_v1',
  lowConfidence: Boolean(tests.low_confidence),
});

const runPatientFlow = async (payload = {}) => {
  const symptoms = String(payload.symptoms || '').trim();
  if (!symptoms) {
    throw new Error('Symptoms text is required');
  }

  try {
    const response = await postWithRetry(axios, `${ML_SERVICE_URL}/patient-flow`, payload, {
      timeout: FLOW_TIMEOUT_MS,
      retries: Number(process.env.PATIENT_FLOW_RETRY_COUNT || 2),
      initialDelayMs: Number(process.env.PATIENT_FLOW_RETRY_DELAY_MS || 500),
      operation: 'patient_flow',
      source: 'patientFlowService',
    });

    const data = response.data || {};
    const specialty = normalizeSpecialtyScores(data.specialty || {}, symptoms);

    return {
      normalizedSymptoms: data.normalizedSymptoms || specialty.normalizedSymptoms || symptoms,
      derivedChiefComplaintSystem:
        data.derivedChiefComplaintSystem || payload.chief_complaint_system || 'other',
      safety: normalizeSafetyMatches(data.safety),
      priority: normalizePriority(data.priority),
      specialty,
      queueAssignment: normalizeQueueAssignment(data.queueAssignment),
      tests: normalizeTests(data.tests),
      flowSource: PATIENT_FLOW_SOURCE,
    };
  } catch (error) {
    const detail = error.response?.data?.detail || error.message || 'Patient flow request failed';
    const wrapped = new Error(detail);
    wrapped.status = error.response?.status || 500;
    throw wrapped;
  }
};

module.exports = {
  runPatientFlow,
};

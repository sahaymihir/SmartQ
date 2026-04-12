const axios = require('axios');
const { postWithRetry } = require('../utils/httpRetry');

const ML_SERVICE_URL = process.env.SPECIALTY_API_URL || process.env.TRIAGE_API_URL || 'http://localhost:8000';
const SPECIALTY_TIMEOUT_MS = Number(process.env.SPECIALTY_TIMEOUT_MS || process.env.TRIAGE_TIMEOUT_MS || 10000);
const MODEL_SOURCE = 'specialty_hybrid_v1';

const ROUTE_MAP = {
  Cardiology: 'Cardiology',
  Orthopaedics: 'Orthopaedics',
  Neurology: 'Neurology',
  Dermatology: 'Dermatology',
  Gastroenterology: 'Gastroenterology',
  Paediatrics: 'Paediatrics',
  Pulmonology: 'Pulmonology',
  'General Practice': 'General OPD',
  'Infectious Disease': 'General OPD',
  'Otolaryngology (ENT)': 'General OPD',
  Hematology: 'General OPD',
  Endocrinology: 'General OPD',
  'Nephrology / Urology': 'General OPD',
  'Emergency Medicine': 'General OPD',
};

const routeToSupportedSpecialty = (specialist, preferredRoute) => {
  if (preferredRoute && Object.values(ROUTE_MAP).includes(preferredRoute)) {
    return preferredRoute;
  }
  return ROUTE_MAP[specialist] || 'General OPD';
};

const buildFallbackPrediction = (symptoms = '') => ({
  primarySpecialist: 'General Practice',
  routedSpecialty: 'General OPD',
  confidence: 0.24,
  lowConfidence: true,
  normalizedSymptoms: String(symptoms || '').trim().toLowerCase(),
  extractedSignals: [],
  specialtyScores: [
    {
      specialty: 'General Practice',
      routedSpecialty: 'General OPD',
      score: 0.24,
      matchedKeywords: [],
      matchedSignals: [],
    },
  ],
  alternativeSpecialists: [],
  reasoning:
    'Specialty service was unavailable, so SmartQ routed this conservatively to General OPD for manual review.',
  modelSource: `${MODEL_SOURCE}_fallback`,
});

const buildSpecialtyScores = (alternatives = []) =>
  alternatives
    .map((entry) => {
      const specialty = entry.specialist || entry.specialty || 'General Practice';
      const matchedSignals = Array.isArray(entry.matchedSignals)
        ? entry.matchedSignals
        : Array.isArray(entry.matchedKeywords)
          ? entry.matchedKeywords
          : [];

      return {
        specialty,
        routedSpecialty: routeToSupportedSpecialty(specialty, entry.routedSpecialty),
        score: Number(entry.confidence || entry.score || 0),
        matchedKeywords: matchedSignals,
        matchedSignals,
      };
    })
    .filter((entry) => entry.score > 0);

const buildAlternativeSpecialists = (specialtyScores, primarySpecialist) =>
  specialtyScores
    .filter((entry) => entry.specialty !== primarySpecialist)
    .map((entry) => ({
      specialist: entry.specialty,
      routedSpecialty: entry.routedSpecialty,
      confidence: entry.score,
      matchedSignals: entry.matchedKeywords,
    }));

const defaultReasoning = (primarySpecialist, routedSpecialty, lowConfidence) => {
  let text = `Primary clinical fit: ${primarySpecialist}. SmartQ routed this to ${routedSpecialty}.`;
  if (lowConfidence) {
    text += ' The symptom description overlapped multiple specialties, so manual review is recommended.';
  }
  return text;
};

const predictSpecialty = async (payload = {}) => {
  const symptoms = String(payload.symptoms || '').trim();
  if (!symptoms) {
    throw new Error('Symptoms text is required');
  }

  try {
    const response = await postWithRetry(axios, `${ML_SERVICE_URL}/specialty`, payload, {
      timeout: SPECIALTY_TIMEOUT_MS,
      retries: Number(process.env.SPECIALTY_RETRY_COUNT || 2),
      initialDelayMs: Number(process.env.SPECIALTY_RETRY_DELAY_MS || 500),
      operation: 'specialty_predict',
      source: 'specialtyService',
    });

    const data = response.data || {};
    if (!data.primarySpecialist) {
      return buildFallbackPrediction(symptoms);
    }

    const primarySpecialist = data.primarySpecialist;
    const routedSpecialty = routeToSupportedSpecialty(primarySpecialist, data.routedSpecialty);
    const specialtyScores = buildSpecialtyScores(data.alternatives || []);

    if (!specialtyScores.length) {
      specialtyScores.push({
        specialty: primarySpecialist,
        routedSpecialty,
        score: Number(data.confidence || 0),
        matchedKeywords: Array.isArray(data.extractedSignals) ? data.extractedSignals : [],
        matchedSignals: Array.isArray(data.extractedSignals) ? data.extractedSignals : [],
      });
    }

    return {
      primarySpecialist,
      routedSpecialty,
      confidence: Number(data.confidence || 0),
      lowConfidence: Boolean(data.lowConfidence),
      normalizedSymptoms: data.normalizedSymptoms || symptoms,
      extractedSignals: Array.isArray(data.extractedSignals) ? data.extractedSignals : [],
      specialtyScores,
      alternativeSpecialists: buildAlternativeSpecialists(specialtyScores, primarySpecialist),
      reasoning:
        data.reasoning ||
        defaultReasoning(primarySpecialist, routedSpecialty, Boolean(data.lowConfidence)),
      modelSource: data.modelSource || MODEL_SOURCE,
    };
  } catch (error) {
    console.warn('⚠️  Specialty ML unavailable, using General OPD fallback:', error.message);
    return buildFallbackPrediction(symptoms);
  }
};

module.exports = {
  buildAlternativeSpecialists,
  buildSpecialtyScores,
  predictSpecialty,
  routeToSupportedSpecialty,
};

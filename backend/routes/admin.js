const express = require('express');
const router = express.Router();
const { protect, adminOnly } = require('../middleware/authMiddleware');
const { Token, Queue } = require('../models/Queue');
const User = require('../models/User');
const { hasFinalizedPrescription } = require('../services/prescriptionService');
const predictionHistory = require('../store/predictionStore');
const { getMlOpsLogs, getMlOpsSummary } = require('../store/mlOpsLogStore');
const { mapPriorityClassToScore } = require('../services/triageService');
const { runPatientFlow } = require('../services/patientFlowService');
const { resetAndSeedDemoData } = require('../services/demoSeedService');
const { routeToSupportedSpecialty } = require('../services/specialtyService');
const {
  ACTIVE_TOKEN_STATUSES,
  buildDayQuery,
  getNextWaitingToken,
  getPriorityLabel,
  getTodayQueue,
  isImmediateReviewToken,
  recomputeWaitingQueue,
  sortActiveQueueTokens,
} = require('../utils/queueHelpers');

const today = () => new Date().toISOString().split('T')[0];
const resolveDoctorId = (req) =>
  req.user?.role === 'doctor' ? req.user._id : (req.query.doctorId || req.user._id);

const requireSeedAccess = (req, res, next) => {
  return protect(req, res, () => {
    if (req.user && req.user.role === 'superuser') {
      return next();
    }
    return res.status(403).json({
      success: false,
      message: 'Access denied. Superuser only.',
    });
  });
};

// All admin routes require login + admin/doctor role.
router.use(protect, adminOnly);

const ADMIN_MODEL_EVAL_SCENARIOS = {
  trauma_child_polyfracture: {
    key: 'trauma_child_polyfracture',
    title: 'Trauma child (multiple fractures)',
    description:
      'Child age 5 with broken right/left leg, hand, and skull under trauma complaint for clinical flow walkthrough.',
    payload: {
      symptoms: 'broken right leg, left leg, hand, and skull',
      age: 5,
      chief_complaint_system: 'trauma',
      sex: 'M',
      mental_status_triage: 'alert',
      pain_score: 9,
      language: 'en',
    },
  },
};

const resolveAdminEvalScenario = (scenarioKey) => {
  if (!scenarioKey) {
    return null;
  }
  return ADMIN_MODEL_EVAL_SCENARIOS[String(scenarioKey).trim()] || null;
};

// ─────────────────────────────────────────────────────────────
// GET /api/admin/queue?doctorId=xxx
// Get full queue list for admin dashboard
// ─────────────────────────────────────────────────────────────
router.get('/queue', async (req, res) => {
  try {
    const doctorId = resolveDoctorId(req);

    const tokens = await Token.find({
      doctor: doctorId,
      status: { $in: ['waiting', 'called', 'arrived'] },
      createdAt: buildDayQuery(today())
    })
    .populate('patient', 'name age phone');

    const orderedTokens = sortActiveQueueTokens(tokens);

    const queue = await Queue.findOne({ doctor: doctorId, date: today() });

    res.json({
      success: true,
      queue: orderedTokens.map(t => ({
        tokenId: t._id,
        patientId: t.patient?._id,
        tokenNumber: t.tokenNumber,
        patientName: t.patient?.name || 'Unknown patient',
        patientAge: t.patient?.age || 0,
        patientPhone: t.patient?.phone || '',
        position: t.position,
        etaMinutes: t.etaMinutes,
        priority: t.priority,
        status: t.status,
        checkedIn: t.checkedIn,
        triagePriorityClass: t.triagePriorityClass,
        modelPriorityClass: t.modelPriorityClass,
        priorityFinalScore: t.priorityFinalScore,
        manualReviewRequired: t.manualReviewRequired,
        routingLane: t.routingLane || 'normal',
        requiresImmediateReview: Boolean(t.requiresImmediateReview),
        escalationReason: t.escalationReason || t.overrideReason || '',
        overrideReason: t.overrideReason || '',
      })),
      avgConsultationMinutes: queue?.avgConsultationMinutes || 8,
      isPaused: queue?.isPaused || false,
      currentToken: queue?.currentToken || 0
    });

  } catch (err) {
    console.error('Admin queue error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/admin/next?doctorId=xxx
// Mark current patient as completed, call next
// ─────────────────────────────────────────────────────────────
router.post('/next', async (req, res) => {
  try {
    const doctorId = resolveDoctorId(req);

    // Mark current "called" token as completed
    const currentToken = await Token.findOne({
      doctor: doctorId,
      status: 'called',
      createdAt: buildDayQuery(today())
    });

    if (currentToken) {
      const prescriptionReady = await hasFinalizedPrescription(currentToken);
      if (!prescriptionReady) {
        return res.status(409).json({
          success: false,
          requiresPrescription: true,
          tokenId: currentToken._id,
          message: 'Finalize this patient’s prescription before completing the visit.',
        });
      }

      const consultationStart = currentToken.updatedAt;
      const durationMinutes = Math.round((Date.now() - consultationStart) / 60000);

      currentToken.status = 'completed';
      await currentToken.save();

      // Update moving average
      const queue = await Queue.findOne({ doctor: doctorId, date: today() });
      if (queue && durationMinutes > 0 && durationMinutes < 60) {
        queue.updateAvgConsultation(durationMinutes);
        await queue.save();
      }
    }

    const queue = await getTodayQueue(doctorId, today());

    // Immediate-review tokens are called before the normal waiting lane.
    const nextToken = await getNextWaitingToken(doctorId, today());
    if (nextToken) {
      await nextToken.populate('patient', 'name');
    }

    if (!nextToken) {
      queue.currentToken = 0;
      await queue.save();
      return res.json({ success: true, message: 'Queue is empty — no more patients' });
    }

    nextToken.status = 'called';
    nextToken.calledAt = new Date();
    nextToken.consultationStartedAt = new Date();
    await nextToken.save();

    queue.currentToken = nextToken.tokenNumber;
    await queue.save();
    await recomputeWaitingQueue(doctorId, queue.avgConsultationMinutes, today());

    res.json({
      success: true,
      message: isImmediateReviewToken(nextToken)
        ? `Immediate review: Token #${nextToken.tokenNumber} — ${nextToken.patient?.name || 'patient'}`
        : `Now calling Token #${nextToken.tokenNumber} — ${nextToken.patient?.name || 'patient'}`,
      calledToken: {
        tokenId: nextToken._id,
        tokenNumber: nextToken.tokenNumber,
        patientName: nextToken.patient?.name || 'patient',
        routingLane: nextToken.routingLane || 'normal',
        requiresImmediateReview: Boolean(nextToken.requiresImmediateReview),
      }
    });

  } catch (err) {
    console.error('Next patient error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/admin/noshow?tokenId=xxx
// Mark a patient as no-show and remove from queue
// ─────────────────────────────────────────────────────────────
router.post('/noshow', async (req, res) => {
  try {
    const { tokenId } = req.query;
    if (!tokenId) {
      return res.status(400).json({ success: false, message: 'tokenId is required' });
    }

    const token = await Token.findById(tokenId);
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (req.user.role === 'doctor' && token.doctor.toString() !== req.user._id.toString()) {
      return res.status(403).json({
        success: false,
        message: 'You can only manage patients in your own queue',
      });
    }

    if (!ACTIVE_TOKEN_STATUSES.includes(token.status)) {
      return res.status(400).json({
        success: false,
        message: 'Only active queue tokens can be marked as no-show',
      });
    }

    token.status = 'no_show';
    await token.save();

    const queue = await getTodayQueue(token.doctor, today());
    if (queue.currentToken === token.tokenNumber) {
      queue.currentToken = 0;
      await queue.save();
    }
    await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, today());

    res.json({ success: true, message: 'Patient marked as no-show' });

  } catch (err) {
    console.error('No-show error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/admin/pause?doctorId=xxx&paused=true
// Pause or resume the queue
// ─────────────────────────────────────────────────────────────
router.post('/pause', async (req, res) => {
  try {
    const doctorId = resolveDoctorId(req);
    const paused = req.query.paused === 'true';

    const queue = await getTodayQueue(doctorId, today());

    queue.isPaused = paused;
    await queue.save();

    res.json({
      success: true,
      message: paused ? 'Queue paused' : 'Queue resumed',
      isPaused: queue.isPaused
    });

  } catch (err) {
    console.error('Pause error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

const parseOptionalText = (value) => {
  const text = String(value || '').trim();
  return text.length ? text : undefined;
};

const parseOptionalNumber = (value, fieldLabel, { min, max, integer = false } = {}) => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }

  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`${fieldLabel} must be a valid number`);
  }
  if (min != null && parsed < min) {
    throw new Error(`${fieldLabel} must be at least ${min}`);
  }
  if (max != null && parsed > max) {
    throw new Error(`${fieldLabel} must be at most ${max}`);
  }
  if (integer && !Number.isInteger(parsed)) {
    throw new Error(`${fieldLabel} must be a whole number`);
  }

  return parsed;
};

const buildAdminEvalPayload = (body = {}) => {
  const scenarioKey = parseOptionalText(body.scenario_key);
  const selectedScenario = resolveAdminEvalScenario(scenarioKey);
  // Scenario defaults are merge-first to provide a one-click walkthrough baseline,
  // while still allowing explicit request fields to override for ad-hoc what-if simulation.
  const sourceBody = selectedScenario
    ? { ...selectedScenario.payload, ...body }
    : body;

  const symptoms = parseOptionalText(sourceBody.symptoms);
  if (!symptoms) {
    throw new Error('Symptoms text is required');
  }

  const age = parseOptionalNumber(sourceBody.age, 'Age', { min: 0, max: 130, integer: true });
  if (age == null) {
    throw new Error('Valid age is required');
  }

  return {
    symptoms,
    age,
    scenarioKey: selectedScenario?.key,
    sex: parseOptionalText(sourceBody.sex),
    mental_status_triage: parseOptionalText(sourceBody.mental_status_triage),
    chief_complaint_system: parseOptionalText(sourceBody.chief_complaint_system),
    language: parseOptionalText(sourceBody.language || sourceBody.intakeLanguage),
    temperature_c: parseOptionalNumber(sourceBody.temperature_c, 'Temperature', { min: 0, max: 50 }),
    pain_score: parseOptionalNumber(sourceBody.pain_score, 'Pain score', { min: 0, max: 10 }),
    spo2: parseOptionalNumber(sourceBody.spo2, 'SpO2', { min: 0, max: 100 }),
    respiratory_rate: parseOptionalNumber(sourceBody.respiratory_rate, 'Respiratory rate', { min: 0, max: 80 }),
    heart_rate: parseOptionalNumber(sourceBody.heart_rate, 'Heart rate', { min: 0, max: 250 }),
    systolic_bp: parseOptionalNumber(sourceBody.systolic_bp, 'Systolic BP', { min: 0, max: 300 }),
    diastolic_bp: parseOptionalNumber(sourceBody.diastolic_bp, 'Diastolic BP', { min: 0, max: 200 }),
    gcs_total: parseOptionalNumber(sourceBody.gcs_total, 'GCS total', { min: 0, max: 15, integer: true }),
    news2_score: parseOptionalNumber(sourceBody.news2_score, 'NEWS2 score', { min: 0, max: 25 }),
  };
};

const getDoctorRoute = (doctor = {}) =>
  routeToSupportedSpecialty(doctor.specialty || 'General OPD', doctor.specialty || 'General OPD');

const buildAvailableRoutes = async () => {
  const doctors = await User.find({ role: 'doctor' }).select('_id specialty').lean();
  if (!doctors.length) {
    return [];
  }

  const doctorIds = doctors.map((doctor) => doctor._id);
  const activeTokens = await Token.find({
    doctor: { $in: doctorIds },
    status: { $in: ACTIVE_TOKEN_STATUSES },
    createdAt: { $gte: new Date(today()) },
  }).select('doctor').lean();

  const queues = await Queue.find({
    doctor: { $in: doctorIds },
    date: today(),
  }).select('doctor avgConsultationMinutes isPaused').lean();

  const queueByDoctorId = new Map(queues.map((queue) => [String(queue.doctor), queue]));
  const routeStats = new Map();
  const doctorRouteById = new Map();

  doctors.forEach((doctor) => {
    const doctorId = String(doctor._id);
    const route = getDoctorRoute(doctor);
    doctorRouteById.set(doctorId, route);

    if (!routeStats.has(route)) {
      routeStats.set(route, {
        route,
        currentQueueLength: 0,
        availableDoctors: 0,
        totalConsultationMinutes: 0,
        consultationSamples: 0,
        acceptsFallback: route === 'General OPD',
      });
    }

    const stats = routeStats.get(route);
    const queue = queueByDoctorId.get(doctorId);
    const isAvailable = !queue?.isPaused;
    if (isAvailable) {
      stats.availableDoctors += 1;
      stats.totalConsultationMinutes += Number(queue?.avgConsultationMinutes || 8);
      stats.consultationSamples += 1;
    }
  });

  activeTokens.forEach((token) => {
    const route = doctorRouteById.get(String(token.doctor));
    if (route && routeStats.has(route)) {
      routeStats.get(route).currentQueueLength += 1;
    }
  });

  return Array.from(routeStats.values())
    .map((stats) => {
      const avgConsultationMinutes = stats.consultationSamples > 0
        ? stats.totalConsultationMinutes / stats.consultationSamples
        : 8;
      const doctorDivisor = Math.max(stats.availableDoctors, 1);

      return {
        route: stats.route,
        currentQueueLength: stats.currentQueueLength,
        availableDoctors: stats.availableDoctors,
        avgWaitMinutes: Number(((stats.currentQueueLength * avgConsultationMinutes) / doctorDivisor).toFixed(1)),
        acceptsFallback: stats.acceptsFallback,
      };
    })
    .sort((left, right) => left.route.localeCompare(right.route));
};

const chooseRecommendedDoctor = async (route) => {
  const doctors = await User.find({
    role: 'doctor',
    specialty: route,
  }).select('name specialty _id').lean();

  if (!doctors.length) {
    return null;
  }

  const doctorIds = doctors.map((doctor) => doctor._id);
  const queues = await Queue.find({
    doctor: { $in: doctorIds },
    date: today(),
  }).select('doctor isPaused').lean();
  const pausedDoctorIds = new Set(
    queues.filter((queue) => queue.isPaused).map((queue) => String(queue.doctor))
  );

  const tokenCounts = await Token.aggregate([
    {
      $match: {
        doctor: { $in: doctorIds },
        status: { $in: ACTIVE_TOKEN_STATUSES },
        createdAt: { $gte: new Date(today()) },
      },
    },
    {
      $group: {
        _id: '$doctor',
        queueLength: { $sum: 1 },
      },
    },
  ]);

  const queueLengthByDoctorId = new Map(
    tokenCounts.map((entry) => [String(entry._id), Number(entry.queueLength || 0)])
  );

  return doctors
    .map((doctor) => ({
      ...doctor,
      queueLength: queueLengthByDoctorId.get(String(doctor._id)) || 0,
      paused: pausedDoctorIds.has(String(doctor._id)),
    }))
    .sort((left, right) => {
      if (left.paused !== right.paused) {
        return left.paused ? 1 : -1;
      }
      if (left.queueLength !== right.queueLength) {
        return left.queueLength - right.queueLength;
      }
      return left.name.localeCompare(right.name);
    })[0];
};

const findRecommendedDoctor = async (selectedRoute) => {
  let recommendedDoc = await chooseRecommendedDoctor(selectedRoute);

  let doctorRoutingNote = '';
  if (!recommendedDoc && selectedRoute !== 'General OPD') {
    recommendedDoc = await chooseRecommendedDoctor('General OPD');
    doctorRoutingNote = ` No staffed ${selectedRoute} doctor was free, so SmartQ fell back to General OPD.`;
  }

  if (!recommendedDoc) {
    recommendedDoc = await User.findOne({ role: 'doctor' }).select('name specialty _id').lean();
    if (recommendedDoc) {
      doctorRoutingNote = ` No staffed ${selectedRoute} route was available, so SmartQ used the next available doctor.`;
    }
  }

  const recommendedDoctor = recommendedDoc
    ? {
        id: recommendedDoc._id,
        name: recommendedDoc.name,
        specialty: recommendedDoc.specialty || selectedRoute,
      }
    : {
        id: null,
        name: 'No doctor available',
        specialty: selectedRoute,
      };

  return { recommendedDoctor, doctorRoutingNote };
};

const buildAdminPriorityComponents = (age, rawPriorityClass, finalPriorityClass) => {
  const ageScore = Number(age) >= 60 ? 10 : 5;
  const rawScore = rawPriorityClass == null ? 0 : mapPriorityClassToScore(rawPriorityClass);
  const finalScore = finalPriorityClass == null ? 0 : mapPriorityClassToScore(finalPriorityClass);

  return {
    age: ageScore,
    triage: rawScore,
    symptomNlp: 0,
    ocrFlags: 0,
    clinicianOverride: Math.max(0, finalScore - rawScore),
  };
};

router.get('/model-eval-scenarios', async (req, res) => {
  const scenarios = Object.values(ADMIN_MODEL_EVAL_SCENARIOS).map((scenario) => ({
    key: scenario.key,
    title: scenario.title,
    description: scenario.description,
    payload: scenario.payload,
  }));

  res.json({
    success: true,
    scenarios,
  });
});

// ─────────────────────────────────────────────────────────────
// POST /api/admin/model-eval-run
// Admin-only simulation of layered patient flow:
// safety -> priority -> specialty -> queue -> tests
// ─────────────────────────────────────────────────────────────
router.post('/model-eval-run', async (req, res) => {
  try {
    const payload = buildAdminEvalPayload(req.body);
    payload.availableRoutes = await buildAvailableRoutes();

    const flow = await runPatientFlow(payload);
    const finalPriorityClass = flow.priority.guardrailedPriorityClass;
    const rawPriorityClass = flow.priority.modelPriorityClass;
    const finalPriorityScore = finalPriorityClass == null ? null : mapPriorityClassToScore(finalPriorityClass);
    const rawPriorityScore = rawPriorityClass == null ? null : mapPriorityClassToScore(rawPriorityClass);
    const priorityComponents = buildAdminPriorityComponents(
      payload.age,
      rawPriorityClass,
      finalPriorityClass
    );

    const selectedRoute = flow.queueAssignment.selectedRoute || 'General OPD';
    const { recommendedDoctor, doctorRoutingNote } = await findRecommendedDoctor(selectedRoute);

    const safetySummary = flow.safety.length
      ? ` Safety rules: ${flow.safety.map((rule) => rule.ruleId).join(', ')}.`
      : '';
    const reasoning = [
      flow.specialty.reasoning,
      flow.queueAssignment.rationale,
      doctorRoutingNote,
      safetySummary,
    ]
      .filter(Boolean)
      .join(' ')
      .trim();

    const evaluation = {
      symptoms: payload.symptoms,
      age: payload.age,
      sex: payload.sex || null,
      mentalStatusTriage: payload.mental_status_triage || null,
      chiefComplaintSystem: payload.chief_complaint_system || null,
      temperatureC: payload.temperature_c ?? null,
      painScore: payload.pain_score ?? null,
      spo2: payload.spo2 ?? null,
      respiratoryRate: payload.respiratory_rate ?? null,
      heartRate: payload.heart_rate ?? null,
      systolicBp: payload.systolic_bp ?? null,
      diastolicBp: payload.diastolic_bp ?? null,
      gcsTotal: payload.gcs_total ?? null,
      news2Score: payload.news2_score ?? null,
      derivedChiefComplaintSystem: flow.derivedChiefComplaintSystem,
      normalizedSymptoms: flow.normalizedSymptoms,
      extractedFactors: flow.specialty.extractedSignals,
      extractedSignals: flow.specialty.extractedSignals,
      specialtyScores: flow.specialty.specialtyScores,
      alternativeSpecialists: flow.specialty.alternativeSpecialists,
      primarySpecialist: flow.specialty.primarySpecialist,
      routedSpecialty: flow.specialty.routedSpecialty,
      queueSelectedRoute: selectedRoute,
      queueRouteType: flow.queueAssignment.routeType,
      queueRationale: flow.queueAssignment.rationale,
      queueCurrentLength: flow.queueAssignment.currentQueueLength,
      queueAvailableDoctors: flow.queueAssignment.availableDoctors,
      queueAvgWaitMinutes: flow.queueAssignment.avgWaitMinutes,
      recommendedDoctor,
      confidence: flow.specialty.confidence,
      lowConfidence: flow.specialty.lowConfidence,
      reasoning,
      modelSource: flow.specialty.modelSource,
      flowSource: flow.flowSource,
      priorityLabel: finalPriorityScore == null ? null : getPriorityLabel(finalPriorityScore),
      priorityScore: rawPriorityScore,
      priorityFinalScore: finalPriorityScore,
      triagePriorityClass: finalPriorityClass,
      triageConfidence: flow.priority.modelConfidence,
      triageLowConfidence: flow.priority.lowConfidence,
      triageRecommendation: flow.priority.guardrailedRecommendation || flow.priority.modelRecommendation,
      triageSource: flow.priority.source,
      priorityComponents,
      priorityDecisionTrace: [
        `rawClass=${rawPriorityClass ?? 'n/a'}`,
        `finalClass=${finalPriorityClass ?? 'n/a'}`,
        `source=${flow.priority.source || 'ml_v3'}`,
        `safety=${flow.safety.length ? flow.safety.map((rule) => rule.ruleId).join(',') : 'none'}`,
      ].join(';'),
      triageAllClassProbs: flow.priority.allClassProbs || {},
      triageModelVersion: 'ml_v3',
      modelPriorityClass: rawPriorityClass,
      guardrailedPriorityClass: finalPriorityClass,
      guardrailedRecommendation: flow.priority.guardrailedRecommendation || flow.priority.modelRecommendation,
      safetyMatches: flow.safety,
      testRecommendations: flow.tests.recommendations,
      testSource: flow.tests.source,
      testLowConfidence: flow.tests.lowConfidence,
      scenarioKey: payload.scenarioKey || null,
      availableRoutes: payload.availableRoutes,
      timestamp: new Date().toISOString(),
      patientName: 'Admin simulation',
      patientId: req.user._id,
    };

    predictionHistory.unshift(evaluation);
    if (predictionHistory.length > 100) {
      predictionHistory.pop();
    }

    res.json({
      success: true,
      ...evaluation,
    });
  } catch (err) {
    console.error('Admin model eval error:', err);
    res.status(err.status || 500).json({
      success: false,
      message: err.message || 'Server error',
    });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/admin/model-eval-history
// Returns all symptom prediction evaluations (in-memory, capped at 100)
// ─────────────────────────────────────────────────────────────
router.get('/model-eval-history', async (req, res) => {
  res.json({ success: true, history: predictionHistory });
});

// ─────────────────────────────────────────────────────────────
// GET /api/admin/ml-ops-logs?limit=20
// Returns recent ML request lifecycle logs and aggregate reliability stats
// ─────────────────────────────────────────────────────────────
router.get('/ml-ops-logs', async (req, res) => {
  const limit = Number(req.query.limit || 20);
  res.json({
    success: true,
    summary: getMlOpsSummary(),
    logs: getMlOpsLogs(limit),
  });
});

// ─────────────────────────────────────────────────────────────
// POST /api/admin/seed
// Completely resets demo data, then recreates a realistic
// SmartQ dataset for classroom/demo use.
// Restricted to superuser accounts only.
// ─────────────────────────────────────────────────────────────
router.post('/seed', requireSeedAccess, async (req, res) => {
  try {
    const result = await resetAndSeedDemoData();

    res.json({
      success: true,
      message: result.message,
      ...result.counts,
      credentials: result.credentials,
    });
  } catch (err) {
    console.error('Demo seed error:', err);
    res.status(500).json({
      success: false,
      message: err.message || 'Failed to reset and seed demo data.',
    });
  }
});

module.exports = router;

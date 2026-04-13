const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const rateLimit = require('express-rate-limit');
const { protect, staffOnly } = require('../middleware/authMiddleware');
const { Queue, Token } = require('../models/Queue');
const User = require('../models/User');
const {
  buildPrescriptionView,
  hasLegacyPrescription,
} = require('../services/prescriptionService');
const { notifyWaitingDoctorEtaChanges } = require('../services/queueNotificationService');
const { buildVisitSnapshot, determineTriageDecision, getAgeBaselineScore, mapPriorityClassToScore } = require('../services/triageService');
const { routeToSupportedSpecialty } = require('../services/specialtyService');
const {
  ACTIVE_TOKEN_STATUSES,
  buildDayQuery,
  computeETA,
  diffMinutes,
  IMMEDIATE_REVIEW_LANE,
  getTodayDateString,
  getTodayQueue,
  isImmediateReviewToken,
  promoteTokenByPriority,
  reorderWaitingQueueByUrgency,
  recomputeWaitingQueue,
  getPriorityLabel,
  sortWaitingTokensForCall,
} = require('../utils/queueHelpers');

// Minimum predicted wait time (minutes) before test recommendations are surfaced.
const HIGH_WAIT_THRESHOLD_MIN = Number(process.env.TEST_SUGGEST_WAIT_THRESHOLD_MIN || 30);
const PATIENTS_AHEAD_THRESHOLD = Number(
  process.env.TEST_SUGGEST_PATIENTS_AHEAD_THRESHOLD || 3
);

// Helper: check whether a value is a valid MongoDB ObjectId string.
const isValidObjectId = (value) =>
  typeof value === 'string' && mongoose.Types.ObjectId.isValid(value);

// Rate limiters for the two staff-facing endpoints added by this feature.
const nurseTriageLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  message: { success: false, message: 'Too many nurse-triage requests, please try again later.' },
});

const emergencyLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  message: { success: false, message: 'Too many emergency-token requests, please try again later.' },
});

const joinLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 40,
  message: { success: false, message: 'Too many queue-join requests, please try again shortly.' },
});

const noShowLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  message: { success: false, message: 'Too many no-show updates, please try again shortly.' },
});

const isSameDayCheckIn = (checkInDate, todayDateString) => {
  if (!checkInDate) {
    return false;
  }
  const checkIn = new Date(checkInDate);
  if (Number.isNaN(checkIn.getTime())) {
    return false;
  }
  return checkIn.toISOString().split('T')[0] === todayDateString;
};

const buildTokenResponse = (token) => ({
  tokenId: token._id,
  tokenNumber: token.tokenNumber,
  position: token.position,
  etaMinutes: token.etaMinutes,
  predictedWaitMinutes: token.predictedWaitMinutes,
  actualWaitMinutes: token.actualWaitMinutes,
  predictedConsultMinutes: token.predictedConsultMinutes,
  actualConsultMinutes: token.actualConsultMinutes,
  status: token.status,
  checkedIn: token.checkedIn,
  snoozeCount: token.snoozeCount || 0,
  priority: token.priority,
  priorityScore: token.priorityScore,
  aiConfidence: token.aiConfidence,
  triagePriorityClass: token.triagePriorityClass,
  modelPriorityClass: token.modelPriorityClass,
  triageConfidence: token.triageConfidence,
  triageLowConfidence: token.triageLowConfidence,
  triageRecommendation: token.triageRecommendation,
  triageSource: token.triageSource,
  triageModelVersion: token.triageModelVersion,
  manualReviewRequired: token.manualReviewRequired,
  routingLane: token.routingLane || 'normal',
  requiresImmediateReview: Boolean(token.requiresImmediateReview),
  escalationReason: token.escalationReason || '',
  safetyMatches: token.safetyMatches || [],
  overrideReason: token.overrideReason,
  triage: {
    priorityClass: token.triagePriorityClass,
    modelPriorityClass: token.modelPriorityClass,
    confidence: token.triageConfidence,
    lowConfidence: token.triageLowConfidence,
    recommendation: token.triageRecommendation,
    source: token.triageSource,
    modelVersion: token.triageModelVersion,
    overrideReason: token.overrideReason,
    manualReviewRequired: token.manualReviewRequired,
    routingLane: token.routingLane || 'normal',
    requiresImmediateReview: Boolean(token.requiresImmediateReview),
    escalationReason: token.escalationReason || '',
    safetyMatches: token.safetyMatches || [],
    allClassProbs: token.triageAllClassProbs || {},
  },
  priorityComponents: token.priorityComponents || {},
  priorityFinalScore: token.priorityFinalScore,
  priorityDecisionTrace: token.priorityDecisionTrace || '',
  derivedChiefComplaintSystem:
    token.derivedChiefComplaintSystem ||
    token.visitSnapshot?.derivedChiefComplaintSystem ||
    token.visitSnapshot?.chief_complaint_system ||
    '',
  queueSelectedRoute: token.queueSelectedRoute || '',
  queueRouteType: token.queueRouteType || '',
  queueRationale: token.queueRationale || '',
  queueCurrentLength: Number(token.queueCurrentLength || 0),
  queueAvailableDoctors: Number(token.queueAvailableDoctors || 0),
  queueAvgWaitMinutes:
    token.queueAvgWaitMinutes == null ? null : Number(token.queueAvgWaitMinutes || 0),
  timing: {
    joinedAt: token.joinedAt,
    calledAt: token.calledAt,
    consultationStartedAt: token.consultationStartedAt,
    completedAt: token.completedAt,
    predictedWaitMinutes: token.predictedWaitMinutes,
    actualWaitMinutes: token.actualWaitMinutes,
    predictedConsultMinutes: token.predictedConsultMinutes,
    actualConsultMinutes: token.actualConsultMinutes,
  },
  // Visit intent & follow-up
  visitType: token.visitType || 'new',
  followUpTokenId: token.followUpTokenId || null,
  // Nurse triage
  nurseTriaged: Boolean(token.nurseTriaged),
  nurseTriagedAt: token.nurseTriagedAt || null,
  nurseTriageNote: token.nurseTriageNote || '',
  // Test recommendations (present when wait is high)
  testRecommendations: Array.isArray(token.testRecommendations) ? token.testRecommendations : [],
  testSuggestedAt: token.testSuggestedAt || null,
});

const buildJoinMessage = (token) => {
  if (isImmediateReviewToken(token)) {
    return `Token #${token.tokenNumber} issued. Immediate review required — please alert the triage desk now.`;
  }

  return `Token #${token.tokenNumber} issued. You are #${token.position} in queue.`;
};

const getAssignedRoute = (doctorSpecialty, selectedRoute) =>
  selectedRoute || routeToSupportedSpecialty(doctorSpecialty, doctorSpecialty);

const resolveRouteDoctorIds = async (selectedRoute, assignedDoctorId, isReturningPatient) => {
  if (isReturningPatient) {
    return [new mongoose.Types.ObjectId(assignedDoctorId)];
  }

  const doctors = await User.find({ role: 'doctor' }).select('_id specialty').lean();
  const matchingDoctorIds = doctors
    .filter((doctor) => getAssignedRoute(doctor.specialty, '') === selectedRoute)
    .map((doctor) => doctor._id);

  if (matchingDoctorIds.length > 0) {
    return matchingDoctorIds;
  }

  return [new mongoose.Types.ObjectId(assignedDoctorId)];
};

const computeAverageConsultationAcrossDoctors = async (doctorIds, dateString) => {
  const queues = await Queue.find({
    doctor: { $in: doctorIds },
    date: dateString,
  }).select('avgConsultationMinutes').lean();

  if (!queues.length) {
    return 8;
  }

  const total = queues.reduce((sum, queue) => {
    return sum + Math.max(0, Number(queue.avgConsultationMinutes) || 0);
  }, 0);

  return Math.max(1, Math.round(total / queues.length));
};

const buildDoctorQueueMetrics = async (token, triageDecision, doctorSpecialty, dateString) => {
  const isReturningPatient = token.visitType === 'follow_up' || Boolean(token.followUpTokenId);
  const selectedRoute = getAssignedRoute(
    doctorSpecialty,
    triageDecision.queueSelectedRoute || token.queueSelectedRoute || ''
  );
  const routeType = triageDecision.queueRouteType || (isReturningPatient ? 'follow_up' : 'primary');
  const rationale = triageDecision.queueRationale || (
    isReturningPatient
      ? 'Returning patient kept with the previous doctor after nurse triage.'
      : 'Patient moved into the doctor-ready queue after nurse vitals were captured.'
  );
  const doctorIds = await resolveRouteDoctorIds(
    selectedRoute,
    token.doctor?._id || token.doctor,
    isReturningPatient
  );
  const availableDoctors = Math.max(1, doctorIds.length);
  const avgConsultationMinutes = await computeAverageConsultationAcrossDoctors(doctorIds, dateString);
  const safetyMatches = Array.isArray(triageDecision.safetyMatches) ? triageDecision.safetyMatches : [];
  const forcedImmediateReview = safetyMatches.some((match) => {
    const forcedPriorityClass = Number(match?.forcedPriorityClass);
    return Number.isFinite(forcedPriorityClass) && forcedPriorityClass <= 2;
  });
  const requiresImmediateReview = Boolean(triageDecision.requiresImmediateReview || forcedImmediateReview);
  const targetPriorityClass = Number(triageDecision.triagePriorityClass || token.triagePriorityClass || 5) || 5;
  const currentTokenTime = new Date(token.joinedAt || token.createdAt || Date.now()).getTime();

  let currentQueueLength = 0;
  if (!requiresImmediateReview) {
    const queuedTokens = await Token.find({
      doctor: { $in: doctorIds },
      status: 'waiting_doctor',
      createdAt: buildDayQuery(dateString),
      _id: { $ne: token._id },
    }).select('triagePriorityClass joinedAt createdAt tokenNumber routingLane requiresImmediateReview');

    currentQueueLength = queuedTokens.filter((entry) => {
      if (entry.requiresImmediateReview || entry.routingLane === IMMEDIATE_REVIEW_LANE) {
        return true;
      }

      const entryPriorityClass = Number(entry.triagePriorityClass || 5) || 5;
      if (entryPriorityClass < targetPriorityClass) {
        return true;
      }
      if (entryPriorityClass > targetPriorityClass) {
        return false;
      }

      const entryTime = new Date(entry.joinedAt || entry.createdAt || 0).getTime();
      if (entryTime !== currentTokenTime) {
        return entryTime < currentTokenTime;
      }

      return Number(entry.tokenNumber || 0) < Number(token.tokenNumber || 0);
    }).length;
  }

  return {
    selectedRoute,
    routeType,
    rationale,
    currentQueueLength,
    availableDoctors,
    avgWaitMinutes: requiresImmediateReview
      ? 0
      : Math.round((currentQueueLength * avgConsultationMinutes) / Math.max(1, availableDoctors)),
    avgConsultationMinutes,
    requiresImmediateReview,
  };
};

// ─────────────────────────────────────────────────────────────
// POST /api/queue/join?doctorId=xxx
// Patient joins the queue for a specific doctor
// ─────────────────────────────────────────────────────────────
router.post('/join', joinLimiter, protect, async (req, res) => {
  try {
    const requestedDoctorId = req.query.doctorId;
    const { symptoms } = req.body;
    const patient = req.user;
    let doctorId = requestedDoctorId;

    // Visit intent: 'new' (default) or 'follow_up'
    const visitType = ['new', 'follow_up'].includes(req.body.visitType)
      ? req.body.visitType
      : 'new';

    if (patient.role !== 'patient') {
      return res.status(403).json({
        success: false,
        message: 'Only patient accounts can join a queue',
      });
    }

    const today = getTodayDateString();
    const patientPresence = await User.findById(patient._id)
      .select('lastHospitalCheckInAt')
      .lean();
    const checkedInToday = isSameDayCheckIn(patientPresence?.lastHospitalCheckInAt, today);
    if (!checkedInToday) {
      return res.status(409).json({
        success: false,
        requiresCheckIn: true,
        message: 'Please check in at hospital first before joining a queue.',
      });
    }

    const existingToken = await Token.findOne({
      patient: patient._id,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(today),
    }).populate('doctor', 'name');

    if (existingToken) {
      const sameDoctor = existingToken.doctor?._id?.toString() === doctorId;
      return res.status(409).json({
        success: false,
        message: sameDoctor
          ? 'You already have an active token in this queue'
          : `You already have an active token${existingToken.doctor?.name ? ` with ${existingToken.doctor.name}` : ''}`
      });
    }

    // ─── Follow-up linkage ─────────────────────────────────────
    // Resolve the prior token reference for continuity tracking.
    let followUpTokenId = null;
    if (visitType === 'follow_up') {
      if (!req.body.followUpTokenId) {
        return res.status(400).json({
          success: false,
          message: 'Please select a previous completed visit for this follow-up.',
        });
      }

      if (!isValidObjectId(req.body.followUpTokenId)) {
        return res.status(400).json({
          success: false,
          message: 'Invalid followUpTokenId: must be a valid MongoDB ObjectId',
        });
      }

      const priorToken = await Token.findOne({
        _id: new mongoose.Types.ObjectId(req.body.followUpTokenId),
        patient: patient._id,
        status: 'completed',
      }).lean();

      if (!priorToken) {
        return res.status(400).json({
          success: false,
          message: 'The selected follow-up visit was not found in your completed visit history.',
        });
      }

      followUpTokenId = priorToken._id;
      doctorId = priorToken.doctor?.toString();

      if (!doctorId) {
        return res.status(409).json({
          success: false,
          followUpDoctorUnavailable: true,
          requiresManualAssignment: true,
          message: 'Previous follow-up doctor is unavailable. Please ask nurse desk for manual assignment.',
        });
      }

      if (requestedDoctorId && requestedDoctorId !== doctorId) {
        return res.status(400).json({
          success: false,
          message: 'Follow-up visits must continue with the same doctor from the selected prior visit.',
        });
      }
    }

    if (!doctorId) {
      return res.status(400).json({ success: false, message: 'doctorId is required' });
    }
    if (!isValidObjectId(doctorId)) {
      return res.status(400).json({ success: false, message: 'Invalid doctorId: must be a valid MongoDB ObjectId' });
    }
    const doctorObjectId = new mongoose.Types.ObjectId(doctorId);

    const assignedDoctor = await User.findById(doctorObjectId).select('_id role name specialty').lean();
    if (!assignedDoctor || assignedDoctor.role !== 'doctor') {
      return res.status(404).json({
        success: false,
        message: 'Selected doctor was not found',
      });
    }

    const queue = await getTodayQueue(doctorId);

    if (!queue.isActive) {
      return res.status(400).json({ success: false, message: 'This queue is not active today' });
    }
    if (queue.isPaused) {
      if (visitType === 'follow_up') {
        return res.status(409).json({
          success: false,
          followUpDoctorUnavailable: true,
          requiresManualAssignment: true,
          message: 'Your previous doctor is currently unavailable. Please ask nurse desk for manual assignment.',
        });
      }
      return res.status(400).json({ success: false, message: 'This queue is currently paused' });
    }

    const triageDecision = await determineTriageDecision(patient, req.body);
    const routingLane = triageDecision.routingLane || 'normal';

    const waitingCount = await Token.countDocuments({
      doctor: doctorObjectId,
      status: 'waiting',
      routingLane: { $ne: IMMEDIATE_REVIEW_LANE },
      createdAt: buildDayQuery(today),
    });

    const position = routingLane === IMMEDIATE_REVIEW_LANE ? 0 : waitingCount + 1;
    const tokenNumber = queue.totalTokensIssued + 1;
    const predictedWaitMinutes =
      routingLane === IMMEDIATE_REVIEW_LANE ? 0 : computeETA(position, queue.avgConsultationMinutes);
    const joinedAt = new Date();

    // ─── High-wait test recommendations ───────────────────────
    // Surface ML-suggested routine tests to patients with long predicted waits
    // so they can initiate them in parallel before the consultation.
    const mlTestRecs = Array.isArray(triageDecision.testRecommendations)
      ? triageDecision.testRecommendations
      : [];
    const testRecommendations = [];
    const testSuggestedAt = null;
    const queueSelectedRoute = getAssignedRoute(
      assignedDoctor.specialty,
      triageDecision.queueSelectedRoute || ''
    );
    const queueRouteType = triageDecision.queueRouteType || (visitType === 'follow_up' ? 'follow_up' : 'primary');
    const queueRationale = triageDecision.queueRationale || 'Awaiting nurse vitals before entering the doctor-ready queue.';
    const visitSnapshot = buildVisitSnapshot(patient, req.body);
    visitSnapshot.derivedChiefComplaintSystem =
      triageDecision.derivedChiefComplaintSystem || req.body.chief_complaint_system || '';

    const token = await Token.create({
      patient: patient._id,
      doctor: doctorObjectId,
      tokenNumber,
      position,
      etaMinutes: predictedWaitMinutes,
      priority: triageDecision.priorityLabel,
      priorityScore: triageDecision.priorityScore,
      // Normalised symptom text (prefer symptomsText, then voice, then legacy)
      symptoms:
        symptoms ||
        req.body.symptomsText ||
        req.body.symptomsVoiceTranscript ||
        '',
      symptomsVoiceTranscript: req.body.symptomsVoiceTranscript || '',
      intakeLanguage: req.body.intakeLanguage || 'en',
      aiConfidence: triageDecision.aiConfidence,
      ageBasedPriorityScore: triageDecision.ageBasedPriorityScore,
      mlPriorityScore: triageDecision.mlPriorityScore,
      modelPriorityClass: triageDecision.modelPriorityClass,
      triagePriorityClass: triageDecision.triagePriorityClass,
      triageConfidence: triageDecision.triageConfidence,
      triageLowConfidence: triageDecision.triageLowConfidence,
      triageRecommendation: triageDecision.triageRecommendation,
      triageAllClassProbs: triageDecision.triageAllClassProbs,
      triageModelVersion: triageDecision.triageModelVersion,
      triageSource: triageDecision.triageSource,
      overrideReason: triageDecision.overrideReason,
      manualReviewRequired: triageDecision.manualReviewRequired,
      routingLane,
      requiresImmediateReview: Boolean(triageDecision.requiresImmediateReview),
      escalationReason: triageDecision.escalationReason || '',
      safetyMatches: triageDecision.safetyMatches || [],
      priorityComponents: triageDecision.priorityComponents,
      priorityFinalScore: triageDecision.priorityFinalScore,
      priorityDecisionTrace: triageDecision.priorityDecisionTrace,
      derivedChiefComplaintSystem:
        triageDecision.derivedChiefComplaintSystem || req.body.chief_complaint_system || '',
      queueSelectedRoute,
      queueRouteType,
      queueRationale,
      queueCurrentLength: 0,
      queueAvailableDoctors: 0,
      queueAvgWaitMinutes: predictedWaitMinutes,
      visitSnapshot,
      joinedAt,
      predictedWaitMinutes,
      predictedConsultMinutes: queue.avgConsultationMinutes,
      checkedIn: true,
      checkedInAt: patientPresence?.lastHospitalCheckInAt || joinedAt,
      // Visit intent fields
      visitType,
      followUpTokenId,
      // Test suggestions
      testRecommendations,
      testSuggestedAt,
    });

    queue.totalTokensIssued = tokenNumber;
    await queue.save();

    if (routingLane !== IMMEDIATE_REVIEW_LANE) {
      await reorderWaitingQueueByUrgency(doctorObjectId, queue.avgConsultationMinutes, today, 'waiting');
      await promoteTokenByPriority(doctorObjectId, token._id, queue.avgConsultationMinutes, today, 'waiting');
    }
    const updatedToken = await Token.findById(token._id);

    // Apply pre-doctor test suggestions only after final queue placement is known.
    if (updatedToken && routingLane !== IMMEDIATE_REVIEW_LANE && mlTestRecs.length > 0) {
      const patientsAhead = Math.max(0, Number(updatedToken.position ?? 1) - 1);
      const livePredictedWaitMinutes = computeETA(updatedToken.position, queue.avgConsultationMinutes);
      const shouldSuggestTests =
        patientsAhead >= PATIENTS_AHEAD_THRESHOLD ||
        livePredictedWaitMinutes >= HIGH_WAIT_THRESHOLD_MIN;

      if (shouldSuggestTests) {
        updatedToken.testRecommendations = mlTestRecs;
        updatedToken.testSuggestedAt = new Date();
        await updatedToken.save();
      }
    }

    res.status(201).json({
      success: true,
      ...buildTokenResponse(updatedToken),
      message: buildJoinMessage(updatedToken),
    });

  } catch (err) {
    console.error('Join queue error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/queue/status
// Get patient's current queue position + ETA
// ─────────────────────────────────────────────────────────────
router.get('/status', protect, async (req, res) => {
  try {
    const today = getTodayDateString();

    const token = await Token.findOne({
      patient: req.user._id,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(today),
    }).populate('doctor', 'name');

    if (!token) {
      return res.status(404).json({
        success: false,
        message: 'No active token found for today'
      });
    }

    const queue = await getTodayQueue(token.doctor._id);
    const liveETA = ['waiting', 'waiting_doctor'].includes(token.status) && !isImmediateReviewToken(token)
      ? (
        token.status === 'waiting_doctor' && token.queueAvgWaitMinutes != null
          ? Number(token.queueAvgWaitMinutes || 0)
          : computeETA(token.position, queue.avgConsultationMinutes)
      )
      : 0;

    res.json({
      success: true,
      ...buildTokenResponse({
        ...token.toObject(),
        etaMinutes: liveETA,
      }),
      doctorName: token.doctor.name,
    });

  } catch (err) {
    console.error('Queue status error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/queue/nurse-board
// Nurse-facing list of patients still waiting for vitals capture.
// Allowed roles: nurse, admin, doctor.
// ─────────────────────────────────────────────────────────────
router.get('/nurse-board', protect, staffOnly, async (req, res) => {
  try {
    const today = getTodayDateString();
    const tokens = await Token.find({
      status: 'waiting',
      createdAt: buildDayQuery(today),
    })
      .populate('patient', 'name age phone')
      .populate('doctor', 'name specialty');

    const orderedTokens = sortWaitingTokensForCall(tokens);

    res.json({
      success: true,
      queue: orderedTokens.map((token) => ({
        tokenId: token._id,
        patientId: token.patient?._id,
        patientName: token.patient?.name || 'Unknown patient',
        patientAge: token.patient?.age || 0,
        patientPhone: token.patient?.phone || '',
        tokenNumber: token.tokenNumber,
        position: token.position,
        etaMinutes: token.etaMinutes,
        priority: token.priority,
        status: token.status,
        checkedIn: token.checkedIn,
        symptoms: token.symptoms || '',
        doctorName: token.doctor?.name || 'Unassigned doctor',
        doctorSpecialty: token.doctor?.specialty || '',
        triagePriorityClass: token.triagePriorityClass,
        modelPriorityClass: token.modelPriorityClass,
        priorityFinalScore: token.priorityFinalScore,
        manualReviewRequired: token.manualReviewRequired,
        routingLane: token.routingLane || 'normal',
        requiresImmediateReview: Boolean(token.requiresImmediateReview),
        escalationReason: token.escalationReason || token.overrideReason || '',
        overrideReason: token.overrideReason || '',
      })),
    });
  } catch (err) {
    console.error('Nurse board error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/queue/snooze?positions=2
// Push patient back by N positions (default 2)
// ─────────────────────────────────────────────────────────────
router.post('/snooze', protect, async (req, res) => {
  try {
    const pushBack = parseInt(req.query.positions, 10) || 2;
    const today = getTodayDateString();

    const token = await Token.findOne({
      patient: req.user._id,
      status: { $in: ['waiting', 'waiting_doctor'] },
      createdAt: buildDayQuery(today),
    });

    if (!token) {
      return res.status(404).json({ success: false, message: 'No active waiting token found' });
    }

    if (isImmediateReviewToken(token)) {
      return res.status(400).json({
        success: false,
        message: 'Immediate review cases cannot snooze in the queue',
      });
    }

    if (token.snoozeCount >= 2) {
      return res.status(400).json({
        success: false,
        message: 'Maximum snooze limit (2) reached'
      });
    }

    const totalWaiting = await Token.countDocuments({
      doctor: token.doctor,
      status: token.status,
      routingLane: { $ne: IMMEDIATE_REVIEW_LANE },
      createdAt: buildDayQuery(today),
    });

    const oldPosition = token.position;
    const newPosition = Math.min(oldPosition + pushBack, totalWaiting);

    if (newPosition === oldPosition) {
      return res.json({
        success: true,
        message: `Queue snoozed. Position remains #${oldPosition}`,
        newPosition: oldPosition,
      });
    }

    await Token.updateMany(
      {
        doctor: token.doctor,
        status: token.status,
        position: { $gt: oldPosition, $lte: newPosition },
        createdAt: buildDayQuery(today),
      },
      { $inc: { position: -1 } }
    );

    token.position = newPosition;
    token.snoozeCount += 1;
    await token.save();

    const queue = await getTodayQueue(token.doctor, today);
    await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, today, token.status);

    res.json({
      success: true,
      message: `Queue snoozed. New position: #${newPosition}`,
      newPosition
    });

  } catch (err) {
    console.error('Snooze error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/queue/checkin
// Geofence auto check-in (called from Android when geofence triggers)
// ─────────────────────────────────────────────────────────────
router.post('/checkin', protect, async (req, res) => {
  try {
    if (req.user.role !== 'patient') {
      return res.status(403).json({
        success: false,
        message: 'Only patient accounts can check in',
      });
    }

    const today = getTodayDateString();
    const now = new Date();

    await User.updateOne(
      { _id: req.user._id, role: 'patient' },
      { $set: { lastHospitalCheckInAt: now } }
    );

    const token = await Token.findOne({
      patient: req.user._id,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(today),
    });

    if (!token) {
      return res.json({
        success: true,
        checkedIn: true,
        message: 'Check-in successful. You can now join a queue.'
      });
    }

    if (token.checkedIn) {
      return res.json({ success: true, message: 'Already checked in' });
    }

    token.checkedIn = true;
    token.checkedInAt = now;
    await token.save();

    res.json({
      success: true,
      message: 'Check-in successful! Hospital has been notified of your arrival.'
    });

  } catch (err) {
    console.error('Check-in error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/queue/checkin-status
// Returns whether patient has checked in today.
// ─────────────────────────────────────────────────────────────
router.get('/checkin-status', protect, async (req, res) => {
  try {
    const today = getTodayDateString();
    const patient = await User.findById(req.user._id)
      .select('role lastHospitalCheckInAt')
      .lean();

    if (!patient || patient.role !== 'patient') {
      return res.status(403).json({
        success: false,
        message: 'Only patient accounts can check status',
      });
    }

    const checkedInToday = isSameDayCheckIn(patient.lastHospitalCheckInAt, today);
    return res.json({
      success: true,
      checkedIn: checkedInToday,
      checkedInAt: patient.lastHospitalCheckInAt || null,
    });
  } catch (err) {
    console.error('Check-in status error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/queue/leave
// Patient cancels their active token for today
// ─────────────────────────────────────────────────────────────
router.post('/leave', protect, async (req, res) => {
  try {
    const today = getTodayDateString();

    const token = await Token.findOne({
      patient: req.user._id,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(today),
    });

    if (!token) {
      return res.status(404).json({ success: false, message: 'No active token found' });
    }

    const previousStatus = token.status;
    const wasCalled = previousStatus === 'called';
    const now = new Date();
    token.status = 'cancelled';
    token.completedAt = now;

    if (!Number.isFinite(token.actualWaitMinutes)) {
      token.actualWaitMinutes = diffMinutes(
        token.joinedAt || token.createdAt,
        token.calledAt || now
      );
    }

    await token.save();

    const queue = await getTodayQueue(token.doctor, today);
    if (wasCalled && queue.currentToken === token.tokenNumber) {
      queue.currentToken = 0;
      await queue.save();
    }

    if (['waiting', 'waiting_doctor'].includes(previousStatus)) {
      await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, today, previousStatus);
      if (previousStatus === 'waiting_doctor') {
        await notifyWaitingDoctorEtaChanges(token.doctor, today);
      }
    }

    res.json({
      success: true,
      message: `Token #${token.tokenNumber} cancelled successfully`,
    });
  } catch (err) {
    console.error('Leave queue error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/queue/noshow?tokenId=xxx
// Staff clears a no-show patient from the active queue
// ─────────────────────────────────────────────────────────────
router.post('/noshow', noShowLimiter, protect, staffOnly, async (req, res) => {
  try {
    const { tokenId } = req.query;
    if (!tokenId) {
      return res.status(400).json({ success: false, message: 'tokenId is required' });
    }
    if (!isValidObjectId(tokenId)) {
      return res.status(400).json({
        success: false,
        message: 'Invalid tokenId: must be a valid MongoDB ObjectId',
      });
    }

    const token = await Token.findById(tokenId);
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (req.user.role === 'doctor' && String(token.doctor) !== String(req.user._id)) {
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

    const previousStatus = token.status;
    const wasCalled = previousStatus === 'called';
    const now = new Date();
    token.status = 'no_show';
    token.completedAt = now;
    if (!Number.isFinite(token.actualWaitMinutes)) {
      token.actualWaitMinutes = diffMinutes(
        token.joinedAt || token.createdAt,
        token.calledAt || now
      );
    }
    await token.save();

    const queueDate = new Date(token.createdAt || now).toISOString().split('T')[0];
    const queue = await Queue.findOne({ doctor: token.doctor, date: queueDate });
    if (queue) {
      if (wasCalled && queue.currentToken === token.tokenNumber) {
        queue.currentToken = 0;
        await queue.save();
      }
      if (['waiting', 'waiting_doctor'].includes(previousStatus)) {
        await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, queueDate, previousStatus);
        if (previousStatus === 'waiting_doctor') {
          await notifyWaitingDoctorEtaChanges(token.doctor, queueDate);
        }
      }
    }

    res.json({ success: true, message: 'Patient marked as no-show' });
  } catch (err) {
    console.error('Staff no-show error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/queue/history
// Patient's past completed consultations (up to 20 most recent)
// ─────────────────────────────────────────────────────────────
router.get('/history', protect, async (req, res) => {
  try {
    if (req.user.role !== 'patient') {
      return res.status(403).json({
        success: false,
        message: 'Only patients can access consultation history',
      });
    }

    const limit = Math.min(parseInt(req.query.limit, 10) || 20, 100);

    const tokens = await Token.find({
      patient: req.user._id,
      status: 'completed',
    })
      .populate('doctor', 'name specialty')
      .populate('prescriptionId')
      .sort({ completedAt: -1 })
      .limit(limit)
      .lean();

    const history = tokens.map((token) => {
      const prescriptionView = buildPrescriptionView(token, token.prescriptionId);
      return {
      ...prescriptionView,
      tokenId: token._id,
      tokenNumber: token.tokenNumber,
      date: token.completedAt || token.createdAt,
      doctorName: token.doctor?.name || 'Unknown doctor',
      doctorId: token.doctor?._id || null,
      doctorSpecialty: token.doctor?.specialty || '',
      symptoms: token.symptoms || '',
      diagnosis: prescriptionView.conclusion || token.prescription?.diagnosis || '',
      medicines: prescriptionView.medications || token.prescription?.medicines || '',
      notes: prescriptionView.adviceNotes || token.prescription?.notes || '',
      symptomsSummary: prescriptionView.symptomsSummary || token.symptoms || '',
      conclusionPreview: prescriptionView.conclusion || token.prescription?.diagnosis || '',
      prescriptionSource: token.prescription?.source || 'doctor_typed',
      ocrStatus: token.prescription?.ocrStatus || 'none',
      triageRecommendation: token.triageRecommendation || '',
      priority: token.priority,
      visitType: token.visitType || 'new',
      hasPrescription: Boolean(token.prescriptionId || hasLegacyPrescription(token)),
      actualWaitMinutes: token.actualWaitMinutes,
      actualConsultMinutes: token.actualConsultMinutes,
    };
    });

    res.json({
      success: true,
      total: history.length,
      history,
    });
  } catch (err) {
    console.error('History error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// PATCH /api/queue/nurse-triage/:tokenId
// Nurse submits actual vitals and updates the token's triage.
// Allowed roles: nurse, admin, doctor.
// ─────────────────────────────────────────────────────────────
router.patch('/nurse-triage/:tokenId', nurseTriageLimiter, protect, staffOnly, async (req, res) => {
  try {
    const { tokenId } = req.params;
    const today = getTodayDateString();

    const token = await Token.findById(tokenId)
      .populate('patient', 'name age phone')
      .populate('doctor', 'name specialty');
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (!['waiting', 'waiting_doctor', 'called', 'arrived'].includes(token.status)) {
      return res.status(400).json({
        success: false,
        message: 'Nurse triage can only be applied to active tokens',
      });
    }

    // Merge nurse vitals with any existing symptom context from patient intake
    const nurseVitals = {
      heart_rate: req.body.heart_rate,
      respiratory_rate: req.body.respiratory_rate,
      spo2: req.body.spo2,
      temperature_c: req.body.temperature_c,
      systolic_bp: req.body.systolic_bp,
      diastolic_bp: req.body.diastolic_bp,
      gcs_total: req.body.gcs_total,
      pain_score: req.body.pain_score,
      news2_score: req.body.news2_score,
      mental_status_triage: req.body.mental_status_triage,
    };

    // Re-run triage with nurse-measured vitals merged on top of original intake data
    const mergedBody = {
      symptoms: token.symptoms || '',
      symptomsText: token.symptoms || '',
      symptomsVoiceTranscript: token.symptomsVoiceTranscript || '',
      intakeLanguage: token.intakeLanguage || 'en',
      sex: token.visitSnapshot?.sex,
      chief_complaint_system: token.visitSnapshot?.chief_complaint_system,
      mental_status_triage: req.body.mental_status_triage || token.visitSnapshot?.mental_status_triage,
      ...nurseVitals,
    };

    const triageDecision = await determineTriageDecision(token.patient, mergedBody);
    const queueMetrics = await buildDoctorQueueMetrics(
      token,
      triageDecision,
      token.doctor?.specialty || '',
      today
    );

    // Apply optional clinician priority boost (nurse/doctor override)
    const clinicianOverride = Number(req.body.clinicianPriorityOverride || 0);
    const overriddenScore = Math.min(10, triageDecision.priorityScore + clinicianOverride);
    const overriddenLabel = getPriorityLabel(overriddenScore);

    // Update token with nurse-measured results
    token.nurseTriaged = true;
    token.nurseTriagedAt = new Date();
    token.nurseVitals = nurseVitals;
    token.nurseTriageNote = req.body.nurseTriageNote || '';
    token.status = 'waiting_doctor';

    // Apply new triage decision
    token.priorityScore = overriddenScore;
    token.priority = overriddenLabel;
    token.priorityFinalScore = overriddenScore;
    token.aiConfidence = triageDecision.aiConfidence;
    token.ageBasedPriorityScore = triageDecision.ageBasedPriorityScore;
    token.mlPriorityScore = triageDecision.mlPriorityScore;
    token.triagePriorityClass = triageDecision.triagePriorityClass;
    token.modelPriorityClass = triageDecision.modelPriorityClass;
    token.triageConfidence = triageDecision.triageConfidence;
    token.triageLowConfidence = triageDecision.triageLowConfidence;
    token.triageRecommendation = triageDecision.triageRecommendation;
    token.triageSource = 'nurse_triage';
    token.triageAllClassProbs = triageDecision.triageAllClassProbs || {};
    token.triageModelVersion = triageDecision.triageModelVersion || token.triageModelVersion;
    token.manualReviewRequired = triageDecision.manualReviewRequired;
    token.safetyMatches = triageDecision.safetyMatches || [];
    token.overrideReason = clinicianOverride > 0
      ? `clinician_override_+${clinicianOverride}`
      : triageDecision.overrideReason;
    token.priorityComponents = triageDecision.priorityComponents || {};
    token.priorityDecisionTrace = triageDecision.priorityDecisionTrace
      + (clinicianOverride > 0 ? `;nurseOverride=+${clinicianOverride}` : '');
    token.derivedChiefComplaintSystem =
      triageDecision.derivedChiefComplaintSystem ||
      token.derivedChiefComplaintSystem ||
      token.visitSnapshot?.chief_complaint_system ||
      '';
    token.queueSelectedRoute = queueMetrics.selectedRoute;
    token.queueRouteType = queueMetrics.routeType;
    token.queueRationale = queueMetrics.rationale;
    token.queueCurrentLength = queueMetrics.currentQueueLength;
    token.queueAvailableDoctors = queueMetrics.availableDoctors;
    token.queueAvgWaitMinutes = queueMetrics.avgWaitMinutes;
    token.testRecommendations = Array.isArray(triageDecision.testRecommendations)
      ? triageDecision.testRecommendations
      : [];
    token.testSuggestedAt = token.testRecommendations.length > 0 ? new Date() : null;
    token.predictedConsultMinutes = queueMetrics.avgConsultationMinutes;
    token.predictedWaitMinutes = queueMetrics.avgWaitMinutes;
    token.etaMinutes = queueMetrics.avgWaitMinutes;
    token.visitSnapshot = {
      ...(token.visitSnapshot || {}),
      ...Object.fromEntries(
        Object.entries({
          temperature_c: nurseVitals.temperature_c,
          spo2: nurseVitals.spo2,
          heart_rate: nurseVitals.heart_rate,
          respiratory_rate: nurseVitals.respiratory_rate,
          systolic_bp: nurseVitals.systolic_bp,
          diastolic_bp: nurseVitals.diastolic_bp,
          gcs_total: nurseVitals.gcs_total,
          pain_score: nurseVitals.pain_score,
          news2_score: nurseVitals.news2_score,
          mental_status_triage: mergedBody.mental_status_triage,
        }).filter(([, value]) => value !== undefined && value !== null && value !== '')
      ),
      derivedChiefComplaintSystem:
        triageDecision.derivedChiefComplaintSystem ||
        token.visitSnapshot?.chief_complaint_system ||
        '',
    };

    token.routingLane = queueMetrics.requiresImmediateReview ? IMMEDIATE_REVIEW_LANE : 'normal';
    token.requiresImmediateReview = queueMetrics.requiresImmediateReview;
    token.escalationReason = queueMetrics.requiresImmediateReview
      ? triageDecision.escalationReason || 'nurse_triage_escalation'
      : '';
    token.position = queueMetrics.requiresImmediateReview ? 0 : Math.max(1, Number(token.position) || 1);

    await token.save();

    // Re-sort the doctor-ready queue since the priority may have changed.
    const queue = await getTodayQueue(token.doctor._id || token.doctor, today);
    if (!queueMetrics.requiresImmediateReview) {
      await reorderWaitingQueueByUrgency(
        token.doctor._id || token.doctor,
        queue.avgConsultationMinutes,
        today,
        'waiting_doctor'
      );
      await recomputeWaitingQueue(
        token.doctor._id || token.doctor,
        queue.avgConsultationMinutes,
        today,
        'waiting_doctor'
      );
    }
    await notifyWaitingDoctorEtaChanges(token.doctor._id || token.doctor, today);

    const updatedToken = await Token.findById(token._id);
    res.json({
      success: true,
      message: queueMetrics.requiresImmediateReview
        ? 'Nurse triage recorded. Immediate escalation required.'
        : 'Nurse triage recorded. Token moved to the doctor queue.',
      ...buildTokenResponse(updatedToken),
    });

  } catch (err) {
    console.error('Nurse triage error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/queue/emergency
// Staff (nurse/admin/doctor) creates an emergency token for an
// unconscious or non-ambulatory patient, bypassing self-intake.
// The patient account must already exist (use POST /api/auth/register
// to pre-create a record if the patient is new to the system).
// ─────────────────────────────────────────────────────────────
router.post('/emergency', emergencyLimiter, protect, staffOnly, async (req, res) => {
  try {
    const { patientId, doctorId, reportedSymptoms, estimatedAge } = req.body;

    if (!patientId) {
      return res.status(400).json({
        success: false,
        message: 'patientId is required. Register the patient first if they are new.',
      });
    }

    if (!isValidObjectId(patientId)) {
      return res.status(400).json({ success: false, message: 'Invalid patientId: must be a valid MongoDB ObjectId' });
    }

    const User = require('../models/User');
    const patient = await User.findById(new mongoose.Types.ObjectId(patientId));
    if (!patient || patient.role !== 'patient') {
      return res.status(404).json({ success: false, message: 'Patient not found' });
    }

    // Auto-route to first available doctor if none specified
    let assignedDoctorId = doctorId;
    if (!assignedDoctorId) {
      const firstAvailableDoctor = await User.findOne({ role: 'doctor' }).select('_id');
      if (!firstAvailableDoctor) {
        return res.status(400).json({ success: false, message: 'No doctors available for routing' });
      }
      assignedDoctorId = firstAvailableDoctor._id.toString();
    }

    const today = getTodayDateString();
    const queue = await getTodayQueue(assignedDoctorId);

    if (!queue.isActive) {
      return res.status(400).json({ success: false, message: 'The target queue is not active today' });
    }

    const tokenNumber = queue.totalTokensIssued + 1;
    const joinedAt = new Date();

    // Emergency override: KTAS 1 (highest priority, score 10)
    const ageBasedScore = getAgeBaselineScore(estimatedAge || patient.age);
    const mlPriorityScore = mapPriorityClassToScore(1); // KTAS 1
    const priorityFinalScore = 10; // Emergency always gets maximum score
    const priorityLabel = getPriorityLabel(priorityFinalScore);
    const components = {
      age: ageBasedScore,
      triage: mlPriorityScore,
      symptomNlp: 0,
      ocrFlags: 0,
      clinicianOverride: 0,
    };

    const token = await Token.create({
      patient: patient._id,
      doctor: assignedDoctorId,
      tokenNumber,
      position: 0,
      etaMinutes: 0,
      priority: priorityLabel,
      priorityScore: priorityFinalScore,
      symptoms: reportedSymptoms || 'Emergency — reported by attending staff',
      intakeLanguage: 'en',
      aiConfidence: 1.0,
      ageBasedPriorityScore: ageBasedScore,
      mlPriorityScore,
      modelPriorityClass: 1,
      triagePriorityClass: 1,
      triageConfidence: 1.0,
      triageLowConfidence: false,
      triageRecommendation: 'Immediate emergency assessment required',
      triageModelVersion: 'emergency_override_v1',
      triageSource: 'emergency_staff_intake',
      overrideReason: 'emergency_staff_intake',
      manualReviewRequired: false,
      routingLane: IMMEDIATE_REVIEW_LANE,
      requiresImmediateReview: true,
      escalationReason: 'emergency_staff_created',
      safetyMatches: [],
      priorityComponents: components,
      priorityFinalScore,
      priorityDecisionTrace: `source=emergency_staff_intake;ageScore=${ageBasedScore};mlScore=${mlPriorityScore};finalScore=${priorityFinalScore};createdBy=${req.user.role}`,
      visitSnapshot: { emergencyAdmission: true, reportedSymptoms: reportedSymptoms || '' },
      joinedAt,
      predictedWaitMinutes: 0,
      predictedConsultMinutes: queue.avgConsultationMinutes,
      visitType: 'emergency',
    });

    queue.totalTokensIssued = tokenNumber;
    await queue.save();

    const updatedToken = await Token.findById(token._id);
    res.status(201).json({
      success: true,
      message: `Emergency Token #${tokenNumber} created. Patient is in immediate review lane.`,
      ...buildTokenResponse(updatedToken),
    });

  } catch (err) {
    console.error('Emergency token creation error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

module.exports = router;

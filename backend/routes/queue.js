const express = require('express');
const router = express.Router();
const { protect } = require('../middleware/authMiddleware');
const { Token } = require('../models/Queue');
const { buildVisitSnapshot, determineTriageDecision } = require('../services/triageService');
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
  recomputeWaitingQueue,
} = require('../utils/queueHelpers');

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
});

const buildJoinMessage = (token) => {
  if (isImmediateReviewToken(token)) {
    return `Token #${token.tokenNumber} issued. Immediate review required — please alert the triage desk now.`;
  }

  return `Token #${token.tokenNumber} issued. You are #${token.position} in queue.`;
};

// ─────────────────────────────────────────────────────────────
// POST /api/queue/join?doctorId=xxx
// Patient joins the queue for a specific doctor
// ─────────────────────────────────────────────────────────────
router.post('/join', protect, async (req, res) => {
  try {
    const { doctorId } = req.query;
    const { symptoms } = req.body;
    const patient = req.user;

    if (!doctorId) {
      return res.status(400).json({ success: false, message: 'doctorId is required' });
    }

    if (patient.role !== 'patient') {
      return res.status(403).json({
        success: false,
        message: 'Only patient accounts can join a queue',
      });
    }

    const today = getTodayDateString();
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

    const queue = await getTodayQueue(doctorId);

    if (!queue.isActive) {
      return res.status(400).json({ success: false, message: 'This queue is not active today' });
    }
    if (queue.isPaused) {
      return res.status(400).json({ success: false, message: 'This queue is currently paused' });
    }

    const triageDecision = await determineTriageDecision(patient, req.body);
    const routingLane = triageDecision.routingLane || 'normal';

    const waitingCount = await Token.countDocuments({
      doctor: doctorId,
      status: 'waiting',
      routingLane: { $ne: IMMEDIATE_REVIEW_LANE },
      createdAt: buildDayQuery(today),
    });

    const position = routingLane === IMMEDIATE_REVIEW_LANE ? 0 : waitingCount + 1;
    const tokenNumber = queue.totalTokensIssued + 1;
    const predictedWaitMinutes =
      routingLane === IMMEDIATE_REVIEW_LANE ? 0 : computeETA(position, queue.avgConsultationMinutes);
    const joinedAt = new Date();

    const token = await Token.create({
      patient: patient._id,
      doctor: doctorId,
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
      visitSnapshot: buildVisitSnapshot(patient, req.body),
      joinedAt,
      predictedWaitMinutes,
      predictedConsultMinutes: queue.avgConsultationMinutes,
    });

    queue.totalTokensIssued = tokenNumber;
    await queue.save();

    if (routingLane !== IMMEDIATE_REVIEW_LANE) {
      await promoteTokenByPriority(doctorId, token._id, queue.avgConsultationMinutes, today);
    }
    const updatedToken = await Token.findById(token._id);

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
    const liveETA = token.status === 'waiting' && !isImmediateReviewToken(token)
      ? computeETA(token.position, queue.avgConsultationMinutes)
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
// POST /api/queue/snooze?positions=2
// Push patient back by N positions (default 2)
// ─────────────────────────────────────────────────────────────
router.post('/snooze', protect, async (req, res) => {
  try {
    const pushBack = parseInt(req.query.positions, 10) || 2;
    const today = getTodayDateString();

    const token = await Token.findOne({
      patient: req.user._id,
      status: 'waiting',
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
      status: 'waiting',
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
        status: 'waiting',
        position: { $gt: oldPosition, $lte: newPosition },
        createdAt: buildDayQuery(today),
      },
      { $inc: { position: -1 } }
    );

    token.position = newPosition;
    token.snoozeCount += 1;
    await token.save();

    const queue = await getTodayQueue(token.doctor, today);
    await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, today);

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
    const today = getTodayDateString();

    const token = await Token.findOne({
      patient: req.user._id,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(today),
    });

    if (!token) {
      return res.status(404).json({ success: false, message: 'No active token found' });
    }

    if (token.checkedIn) {
      return res.json({ success: true, message: 'Already checked in' });
    }

    token.checkedIn = true;
    token.checkedInAt = new Date();
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

    const wasCalled = token.status === 'called';
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

    await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, today);

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
      .populate('doctor', 'name')
      .sort({ completedAt: -1 })
      .limit(limit)
      .lean();

    const history = tokens.map((token) => ({
      tokenId: token._id,
      tokenNumber: token.tokenNumber,
      date: token.completedAt || token.createdAt,
      doctorName: token.doctor?.name || 'Unknown doctor',
      symptoms: token.symptoms || '',
      diagnosis: token.prescription?.diagnosis || '',
      medicines: token.prescription?.medicines || '',
      notes: token.prescription?.notes || '',
      prescriptionSource: token.prescription?.source || 'doctor_typed',
      ocrStatus: token.prescription?.ocrStatus || 'none',
      triageRecommendation: token.triageRecommendation || '',
      priority: token.priority,
      actualWaitMinutes: token.actualWaitMinutes,
      actualConsultMinutes: token.actualConsultMinutes,
    }));

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

module.exports = router;

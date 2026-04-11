const express = require('express');
const router = express.Router();
const { protect } = require('../middleware/authMiddleware');
const { Token } = require('../models/Queue');
const { buildVisitSnapshot, determineTriageDecision } = require('../services/triageService');
const {
  ACTIVE_TOKEN_STATUSES,
  buildDayQuery,
  computeETA,
  getTodayDateString,
  getTodayQueue,
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
  triageConfidence: token.triageConfidence,
  triageLowConfidence: token.triageLowConfidence,
  triageRecommendation: token.triageRecommendation,
  triageSource: token.triageSource,
  triageModelVersion: token.triageModelVersion,
  manualReviewRequired: token.manualReviewRequired,
  overrideReason: token.overrideReason,
  triage: {
    priorityClass: token.triagePriorityClass,
    confidence: token.triageConfidence,
    lowConfidence: token.triageLowConfidence,
    recommendation: token.triageRecommendation,
    source: token.triageSource,
    modelVersion: token.triageModelVersion,
    overrideReason: token.overrideReason,
    manualReviewRequired: token.manualReviewRequired,
    allClassProbs: token.triageAllClassProbs || {},
  },
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
      doctor: doctorId,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(today),
    });

    if (existingToken) {
      return res.status(409).json({
        success: false,
        message: 'You already have an active token in this queue'
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

    const waitingCount = await Token.countDocuments({
      doctor: doctorId,
      status: 'waiting',
      createdAt: buildDayQuery(today),
    });

    const position = waitingCount + 1;
    const tokenNumber = queue.totalTokensIssued + 1;
    const predictedWaitMinutes = computeETA(position, queue.avgConsultationMinutes);
    const joinedAt = new Date();

    const token = await Token.create({
      patient: patient._id,
      doctor: doctorId,
      tokenNumber,
      position,
      etaMinutes: predictedWaitMinutes,
      priority: triageDecision.priorityLabel,
      priorityScore: triageDecision.priorityScore,
      symptoms: symptoms || '',
      aiConfidence: triageDecision.aiConfidence,
      ageBasedPriorityScore: triageDecision.ageBasedPriorityScore,
      mlPriorityScore: triageDecision.mlPriorityScore,
      triagePriorityClass: triageDecision.triagePriorityClass,
      triageConfidence: triageDecision.triageConfidence,
      triageLowConfidence: triageDecision.triageLowConfidence,
      triageRecommendation: triageDecision.triageRecommendation,
      triageAllClassProbs: triageDecision.triageAllClassProbs,
      triageModelVersion: triageDecision.triageModelVersion,
      triageSource: triageDecision.triageSource,
      overrideReason: triageDecision.overrideReason,
      manualReviewRequired: triageDecision.manualReviewRequired,
      visitSnapshot: buildVisitSnapshot(patient, req.body),
      joinedAt,
      predictedWaitMinutes,
      predictedConsultMinutes: queue.avgConsultationMinutes,
    });

    queue.totalTokensIssued = tokenNumber;
    await queue.save();

    await promoteTokenByPriority(doctorId, token._id, queue.avgConsultationMinutes, today);
    const updatedToken = await Token.findById(token._id);

    res.status(201).json({
      success: true,
      ...buildTokenResponse(updatedToken),
      message: `Token #${tokenNumber} issued. You are #${updatedToken.position} in queue.`
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
    const liveETA = token.status === 'waiting'
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

    if (token.snoozeCount >= 2) {
      return res.status(400).json({
        success: false,
        message: 'Maximum snooze limit (2) reached'
      });
    }

    const totalWaiting = await Token.countDocuments({
      doctor: token.doctor,
      status: 'waiting',
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

module.exports = router;

const express = require('express');
const router = express.Router();
const { protect } = require('../middleware/authMiddleware');
const { Token, Queue } = require('../models/Queue');

// ─── Helper: get or create today's queue for a doctor ─────
const getTodayQueue = async (doctorId) => {
  const today = new Date().toISOString().split('T')[0]; // "YYYY-MM-DD"
  let queue = await Queue.findOne({ doctor: doctorId, date: today });
  if (!queue) {
    queue = await Queue.create({ doctor: doctorId, date: today });
  }
  return queue;
};

// ─── Helper: compute ETA for a given position ─────────────
const computeETA = (position, avgConsultationMinutes) => {
  // position 1 = next up, so ETA ≈ (position - 1) * avg
  return Math.max(0, (position - 1) * avgConsultationMinutes);
};

// ─── Helper: determine priority label ─────────────────────
const getPriorityLabel = (priorityScore) => {
  if (priorityScore >= 10) return 'high';
  if (priorityScore >= 7)  return 'medium';
  return 'normal';
};

// ─────────────────────────────────────────────────────────────
// POST /api/queue/join?doctorId=xxx
// Patient joins the queue for a specific doctor
// ─────────────────────────────────────────────────────────────
router.post('/join', protect, async (req, res) => {
  try {
    const { doctorId } = req.query;
    const patient = req.user;

    if (!doctorId) {
      return res.status(400).json({ success: false, message: 'doctorId is required' });
    }

    // Check if patient already has an active token for this doctor today
    const today = new Date().toISOString().split('T')[0];
    const existingToken = await Token.findOne({
      patient: patient._id,
      doctor: doctorId,
      status: { $in: ['waiting', 'called', 'arrived'] },
      createdAt: { $gte: new Date(today) }
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

    // Count current waiting patients to determine position
    const waitingCount = await Token.countDocuments({
      doctor: doctorId,
      status: 'waiting',
      createdAt: { $gte: new Date(today) }
    });

    const position = waitingCount + 1;
    const tokenNumber = queue.totalTokensIssued + 1;
    const etaMinutes = computeETA(position, queue.avgConsultationMinutes);
    const priorityLabel = getPriorityLabel(patient.priorityScore);

    // Create token
    const token = await Token.create({
      patient: patient._id,
      doctor: doctorId,
      tokenNumber,
      position,
      etaMinutes,
      priority: priorityLabel,
      priorityScore: patient.priorityScore
    });

    // Update queue stats
    queue.totalTokensIssued = tokenNumber;
    await queue.save();

    // ─── Priority reorder: if high priority, bump up ────────
    // High priority patients are inserted before normal ones
    if (patient.priorityScore >= 10) {
      await reorderQueueForPriority(doctorId, token._id, today);
    }

    // Fetch updated position after reorder
    const updatedToken = await Token.findById(token._id);

    res.status(201).json({
      success: true,
      tokenId: updatedToken._id,
      tokenNumber: updatedToken.tokenNumber,
      position: updatedToken.position,
      etaMinutes: updatedToken.etaMinutes,
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
    const today = new Date().toISOString().split('T')[0];

    const token = await Token.findOne({
      patient: req.user._id,
      status: { $in: ['waiting', 'called', 'arrived'] },
      createdAt: { $gte: new Date(today) }
    }).populate('doctor', 'name');

    if (!token) {
      return res.status(404).json({
        success: false,
        message: 'No active token found for today'
      });
    }

    // Recalculate live ETA
    const queue = await getTodayQueue(token.doctor._id);
    const liveETA = computeETA(token.position, queue.avgConsultationMinutes);

    res.json({
      success: true,
      tokenId: token._id,
      tokenNumber: token.tokenNumber,
      position: token.position,
      etaMinutes: liveETA,
      status: token.status,
      priority: token.priority,
      checkedIn: token.checkedIn,
      doctorName: token.doctor.name
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
    const pushBack = parseInt(req.query.positions) || 2;
    const today = new Date().toISOString().split('T')[0];

    const token = await Token.findOne({
      patient: req.user._id,
      status: 'waiting',
      createdAt: { $gte: new Date(today) }
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

    const oldPosition = token.position;
    const newPosition = oldPosition + pushBack;

    // Shift up everyone between oldPosition+1 and newPosition
    await Token.updateMany(
      {
        doctor: token.doctor,
        status: 'waiting',
        position: { $gt: oldPosition, $lte: newPosition },
        createdAt: { $gte: new Date(today) }
      },
      { $inc: { position: -1 } }
    );

    // Update snoozed patient's position
    token.position = newPosition;
    token.snoozeCount += 1;
    await token.save();

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
    const today = new Date().toISOString().split('T')[0];

    const token = await Token.findOne({
      patient: req.user._id,
      status: 'waiting',
      createdAt: { $gte: new Date(today) }
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

// ─── Priority Reorder Helper ───────────────────────────────
// Inserts high-priority patient before normal-priority patients
async function reorderQueueForPriority(doctorId, highPriorityTokenId, today) {
  const allTokens = await Token.find({
    doctor: doctorId,
    status: 'waiting',
    createdAt: { $gte: new Date(today) }
  }).sort({ position: 1 });

  // Find first normal-priority patient
  const firstNormalIdx = allTokens.findIndex(
    t => t.priority === 'normal' && t._id.toString() !== highPriorityTokenId.toString()
  );

  if (firstNormalIdx === -1) return; // no normal patients to jump ahead of

  const targetPosition = allTokens[firstNormalIdx].position;

  // Shift everyone from targetPosition down by 1
  await Token.updateMany(
    {
      doctor: doctorId,
      status: 'waiting',
      position: { $gte: targetPosition },
      _id: { $ne: highPriorityTokenId },
      createdAt: { $gte: new Date(today) }
    },
    { $inc: { position: 1 } }
  );

  // Assign the high priority patient the target position
  await Token.findByIdAndUpdate(highPriorityTokenId, { position: targetPosition });
}

module.exports = router;
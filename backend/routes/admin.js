const express = require('express');
const router = express.Router();
const { protect, adminOnly } = require('../middleware/authMiddleware');
const { Token, Queue } = require('../models/Queue');

const today = () => new Date().toISOString().split('T')[0];

// All admin routes require login + admin role
router.use(protect, adminOnly);

// ─────────────────────────────────────────────────────────────
// GET /api/admin/queue?doctorId=xxx
// Get full queue list for admin dashboard
// ─────────────────────────────────────────────────────────────
router.get('/queue', async (req, res) => {
  try {
    const doctorId = req.query.doctorId || req.user._id;

    const tokens = await Token.find({
      doctor: doctorId,
      status: { $in: ['waiting', 'called', 'arrived'] },
      createdAt: { $gte: new Date(today()) }
    })
    .populate('patient', 'name age phone')
    .sort({ position: 1 });

    const queue = await Queue.findOne({ doctor: doctorId, date: today() });

    res.json({
      success: true,
      queue: tokens.map(t => ({
        tokenId: t._id,
        tokenNumber: t.tokenNumber,
        patientName: t.patient.name,
        patientAge: t.patient.age,
        patientPhone: t.patient.phone,
        position: t.position,
        etaMinutes: t.etaMinutes,
        priority: t.priority,
        status: t.status,
        checkedIn: t.checkedIn
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
    const doctorId = req.query.doctorId || req.user._id;

    // Mark current "called" token as completed
    const currentToken = await Token.findOne({
      doctor: doctorId,
      status: 'called',
      createdAt: { $gte: new Date(today()) }
    });

    if (currentToken) {
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

    // Get next waiting patient (lowest position)
    const nextToken = await Token.findOne({
      doctor: doctorId,
      status: 'waiting',
      createdAt: { $gte: new Date(today()) }
    }).sort({ position: 1 }).populate('patient', 'name');

    if (!nextToken) {
      return res.json({ success: true, message: 'Queue is empty — no more patients' });
    }

    nextToken.status = 'called';
    await nextToken.save();

    // Shift everyone else up by 1
    await Token.updateMany(
      {
        doctor: doctorId,
        status: 'waiting',
        position: { $gt: 1 },
        createdAt: { $gte: new Date(today()) }
      },
      { $inc: { position: -1 } }
    );

    res.json({
      success: true,
      message: `Now calling Token #${nextToken.tokenNumber} — ${nextToken.patient.name}`,
      calledToken: {
        tokenNumber: nextToken.tokenNumber,
        patientName: nextToken.patient.name
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

    const token = await Token.findById(tokenId);
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    const removedPosition = token.position;
    token.status = 'no_show';
    await token.save();

    // Shift everyone behind up by 1
    await Token.updateMany(
      {
        doctor: token.doctor,
        status: 'waiting',
        position: { $gt: removedPosition },
        createdAt: { $gte: new Date(today()) }
      },
      { $inc: { position: -1 } }
    );

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
    const doctorId = req.query.doctorId || req.user._id;
    const paused = req.query.paused === 'true';

    const queue = await Queue.findOne({ doctor: doctorId, date: today() });
    if (!queue) {
      return res.status(404).json({ success: false, message: 'No queue found for today' });
    }

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

module.exports = router;
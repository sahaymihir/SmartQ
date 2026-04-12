const express = require('express');
const router = express.Router();
const { protect, adminOnly } = require('../middleware/authMiddleware');
const { Token, Queue } = require('../models/Queue');
const { notifyTokenCalled, notifyQueuePaused } = require('../services/notificationService');
const User = require('../models/User');
const predictionHistory = require('../store/predictionStore');
const {
  ACTIVE_TOKEN_STATUSES,
  buildDayQuery,
  diffMinutes,
  getTodayDateString,
  getTodayQueue,
  recomputeWaitingQueue,
} = require('../utils/queueHelpers');

router.use(protect, adminOnly);

const average = (values) => {
  const valid = values.filter((value) => Number.isFinite(value));
  if (!valid.length) {
    return 0;
  }
  return Number((valid.reduce((sum, value) => sum + value, 0) / valid.length).toFixed(2));
};

const sum = (values) => values.reduce((total, value) => total + value, 0);

const resolveDate = (req) => req.query.date || getTodayDateString();

const resolveDoctorId = (req, { required = false } = {}) => {
  if (req.query.doctorId) {
    return req.query.doctorId;
  }
  if (req.user.role === 'doctor') {
    return req.user._id.toString();
  }
  return required ? null : null;
};

const buildTokenMatch = (dateString, doctorId) => ({
  createdAt: buildDayQuery(dateString),
  ...(doctorId ? { doctor: doctorId } : {}),
});

const serializeQueueToken = (token) => ({
  tokenId: token._id,
  patientId: token.patient?._id || token.patient,
  tokenNumber: token.tokenNumber,
  patientName: token.patient?.name,
  patientAge: token.patient?.age,
  patientPhone: token.patient?.phone,
  position: token.position,
  etaMinutes: token.etaMinutes,
  predictedWaitMinutes: token.predictedWaitMinutes,
  actualWaitMinutes: token.actualWaitMinutes,
  predictedConsultMinutes: token.predictedConsultMinutes,
  actualConsultMinutes: token.actualConsultMinutes,
  priority: token.priority,
  priorityScore: token.priorityScore,
  status: token.status,
  checkedIn: token.checkedIn,
  symptoms: token.symptoms,
  aiConfidence: token.aiConfidence,
  triagePriorityClass: token.triagePriorityClass,
  triageConfidence: token.triageConfidence,
  triageLowConfidence: token.triageLowConfidence,
  triageRecommendation: token.triageRecommendation,
  triageSource: token.triageSource,
  triageModelVersion: token.triageModelVersion,
  manualReviewRequired: token.manualReviewRequired,
  overrideReason: token.overrideReason,
  joinedAt: token.joinedAt,
  calledAt: token.calledAt,
  consultationStartedAt: token.consultationStartedAt,
  completedAt: token.completedAt,
  prescription: token.prescription || null,
});

const STATUS_RANK = {
  called: 0,
  arrived: 1,
  waiting: 2,
};

const loadAnalyticsTokens = async (dateString, doctorId) => {
  return Token.find(buildTokenMatch(dateString, doctorId))
    .populate('doctor', 'name')
    .lean();
};

const buildDoctorMetrics = (tokens) => {
  const metricsByDoctor = new Map();

  for (const token of tokens) {
    const doctorId = token.doctor?._id?.toString?.() || token.doctor?.toString?.() || 'unknown';
    const doctorName = token.doctor?.name || 'Unknown doctor';
    if (!metricsByDoctor.has(doctorId)) {
      metricsByDoctor.set(doctorId, {
        doctorId,
        doctorName,
        totalTokens: 0,
        completedCount: 0,
        noShowCount: 0,
        lowConfidenceCount: 0,
        avgActualWaitMinutes: 0,
        avgActualConsultMinutes: 0,
        avgEtaErrorMinutes: 0,
        _actualWaits: [],
        _actualConsults: [],
        _etaErrors: [],
      });
    }

    const metric = metricsByDoctor.get(doctorId);
    metric.totalTokens += 1;
    if (token.status === 'completed') metric.completedCount += 1;
    if (token.status === 'no_show') metric.noShowCount += 1;
    if (token.triageLowConfidence) metric.lowConfidenceCount += 1;
    if (Number.isFinite(token.actualWaitMinutes)) metric._actualWaits.push(token.actualWaitMinutes);
    if (Number.isFinite(token.actualConsultMinutes)) metric._actualConsults.push(token.actualConsultMinutes);
    if (Number.isFinite(token.actualWaitMinutes) && Number.isFinite(token.predictedWaitMinutes)) {
      metric._etaErrors.push(Math.abs(token.actualWaitMinutes - token.predictedWaitMinutes));
    }
  }

  return Array.from(metricsByDoctor.values()).map((metric) => ({
    doctorId: metric.doctorId,
    doctorName: metric.doctorName,
    totalTokens: metric.totalTokens,
    completedCount: metric.completedCount,
    noShowCount: metric.noShowCount,
    lowConfidenceCount: metric.lowConfidenceCount,
    avgActualWaitMinutes: average(metric._actualWaits),
    avgActualConsultMinutes: average(metric._actualConsults),
    avgEtaErrorMinutes: average(metric._etaErrors),
  }));
};

router.get('/queue', async (req, res) => {
  try {
    const dateString = resolveDate(req);
    const doctorId = resolveDoctorId(req, { required: true });

    if (!doctorId) {
      return res.status(400).json({
        success: false,
        message: 'doctorId is required for admin queue views',
      });
    }

    const tokens = await Token.find({
      doctor: doctorId,
      status: { $in: ACTIVE_TOKEN_STATUSES },
      createdAt: buildDayQuery(dateString),
    })
      .populate('patient', 'name age phone')
      .sort({ position: 1 });

    tokens.sort((left, right) => {
      const leftRank = STATUS_RANK[left.status] ?? 99;
      const rightRank = STATUS_RANK[right.status] ?? 99;
      if (leftRank !== rightRank) {
        return leftRank - rightRank;
      }
      if (left.position !== right.position) {
        return left.position - right.position;
      }
      return left.tokenNumber - right.tokenNumber;
    });

    const queue = await Queue.findOne({ doctor: doctorId, date: dateString });

    res.json({
      success: true,
      date: dateString,
      doctorId,
      queue: tokens.map(serializeQueueToken),
      avgConsultationMinutes: queue?.avgConsultationMinutes || 8,
      isPaused: queue?.isPaused || false,
      currentToken: queue?.currentToken || 0,
    });
  } catch (err) {
    console.error('Admin queue error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.post('/next', async (req, res) => {
  try {
    const dateString = resolveDate(req);
    const doctorId = resolveDoctorId(req, { required: true });

    if (!doctorId) {
      return res.status(400).json({
        success: false,
        message: 'doctorId is required to advance a queue',
      });
    }

    const queue = await getTodayQueue(doctorId, dateString);
    const now = new Date();
    let completedTokenPayload = null;

    const currentToken = await Token.findOne({
      doctor: doctorId,
      status: 'called',
      createdAt: buildDayQuery(dateString),
    }).populate('patient', 'name');

    if (currentToken) {
      currentToken.status = 'completed';
      currentToken.completedAt = now;

      if (!Number.isFinite(currentToken.actualWaitMinutes)) {
        currentToken.actualWaitMinutes = diffMinutes(
          currentToken.joinedAt || currentToken.createdAt,
          currentToken.calledAt || now
        );
      }

      currentToken.actualConsultMinutes = diffMinutes(
        currentToken.consultationStartedAt || currentToken.calledAt || currentToken.joinedAt || currentToken.createdAt,
        now
      );

      await currentToken.save();

      if (
        Number.isFinite(currentToken.actualConsultMinutes) &&
        currentToken.actualConsultMinutes > 0 &&
        currentToken.actualConsultMinutes < 240
      ) {
        queue.updateAvgConsultation(currentToken.actualConsultMinutes);
        await queue.save();
      }

      completedTokenPayload = {
        tokenNumber: currentToken.tokenNumber,
        patientName: currentToken.patient?.name,
        actualConsultMinutes: currentToken.actualConsultMinutes,
      };
    }

    const nextToken = await Token.findOne({
      doctor: doctorId,
      status: 'waiting',
      createdAt: buildDayQuery(dateString),
    })
      .sort({ position: 1 })
      .populate('patient', 'name');

    if (!nextToken) {
      queue.currentToken = 0;
      await queue.save();
      await recomputeWaitingQueue(doctorId, queue.avgConsultationMinutes, dateString);

      return res.json({
        success: true,
        message: completedTokenPayload
          ? 'Consultation completed. Queue is empty — no more patients'
          : 'Queue is empty — no more patients',
        completedToken: completedTokenPayload,
      });
    }

    nextToken.status = 'called';
    nextToken.calledAt = now;
    nextToken.consultationStartedAt = nextToken.consultationStartedAt || now;
    nextToken.actualWaitMinutes = diffMinutes(nextToken.joinedAt || nextToken.createdAt, now);
    nextToken.etaMinutes = 0;
    await nextToken.save();

    queue.currentToken = nextToken.tokenNumber;
    await queue.save();

    await recomputeWaitingQueue(doctorId, queue.avgConsultationMinutes, dateString);

    // Push notification — non-fatal if it fails
    const patientId = nextToken.patient?._id || nextToken.patient;
    if (patientId) {
      notifyTokenCalled(patientId, nextToken.tokenNumber).catch(() => {});
    }

    res.json({
      success: true,
      message: `Now calling Token #${nextToken.tokenNumber} — ${nextToken.patient?.name}`,
      calledToken: {
        tokenNumber: nextToken.tokenNumber,
        patientName: nextToken.patient?.name,
        actualWaitMinutes: nextToken.actualWaitMinutes,
      },
      completedToken: completedTokenPayload,
    });
  } catch (err) {
    console.error('Next patient error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

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

    token.status = 'no_show';
    token.completedAt = new Date();
    if (!Number.isFinite(token.actualWaitMinutes)) {
      token.actualWaitMinutes = diffMinutes(token.joinedAt || token.createdAt, token.completedAt);
    }
    await token.save();

    const queue = await getTodayQueue(token.doctor, resolveDate(req));
    await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, resolveDate(req));

    res.json({ success: true, message: 'Patient marked as no-show' });
  } catch (err) {
    console.error('No-show error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.post('/pause', async (req, res) => {
  try {
    const doctorId = resolveDoctorId(req, { required: true });
    const paused = req.query.paused === 'true';
    const dateString = resolveDate(req);

    if (!doctorId) {
      return res.status(400).json({
        success: false,
        message: 'doctorId is required to pause or resume a queue',
      });
    }

    const queue = await Queue.findOne({ doctor: doctorId, date: dateString }).populate('doctor', 'name');
    if (!queue) {
      return res.status(404).json({ success: false, message: 'No queue found for today' });
    }

    queue.isPaused = paused;
    await queue.save();

    // Push notifications to all waiting patients — non-fatal
    const waitingTokens = await Token.find({
      doctor: doctorId,
      status: 'waiting',
      createdAt: buildDayQuery(dateString),
    }).lean();

    const doctorName = queue.doctor?.name || '';
    for (const token of waitingTokens) {
      if (token.patient) {
        notifyQueuePaused(token.patient, doctorName, paused).catch(() => {});
      }
    }

    res.json({
      success: true,
      message: paused ? 'Queue paused' : 'Queue resumed',
      isPaused: queue.isPaused,
    });
  } catch (err) {
    console.error('Pause error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.post('/prescription', async (req, res) => {
  try {
    const { tokenId, diagnosis, medicines, notes } = req.body || {};

    if (!tokenId) {
      return res.status(400).json({ success: false, message: 'tokenId is required' });
    }

    const token = await Token.findById(tokenId).populate('patient', 'name');
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (req.user.role === 'doctor' && token.doctor.toString() !== req.user._id.toString()) {
      return res.status(403).json({
        success: false,
        message: 'You can only prescribe for patients in your own queue',
      });
    }

    token.prescription = {
      diagnosis: diagnosis || '',
      medicines: medicines || '',
      notes: notes || '',
      prescribedAt: new Date(),
      prescribedBy: req.user._id,
    };

    await token.save();

    res.json({
      success: true,
      message: `Prescription saved for ${token.patient?.name || 'patient'}`,
    });
  } catch (err) {
    console.error('Prescription save error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.get('/analytics/summary', async (req, res) => {
  try {
    const dateString = resolveDate(req);
    const doctorId = resolveDoctorId(req);
    const [tokens, queues] = await Promise.all([
      loadAnalyticsTokens(dateString, doctorId),
      Queue.find(doctorId ? { doctor: doctorId, date: dateString } : { date: dateString })
        .populate('doctor', 'name')
        .lean(),
    ]);

    const totalTokens = tokens.length;
    const completed = tokens.filter((token) => token.status === 'completed');
    const noShows = tokens.filter((token) => token.status === 'no_show');
    const waiting = tokens.filter((token) => token.status === 'waiting');
    const called = tokens.filter((token) => token.status === 'called');
    const arrived = tokens.filter((token) => token.status === 'arrived');
    const lowConfidence = tokens.filter((token) => token.triageLowConfidence);
    const manualReview = tokens.filter((token) => token.manualReviewRequired);
    const snoozed = tokens.filter((token) => (token.snoozeCount || 0) > 0);
    const etaErrors = tokens
      .filter((token) => Number.isFinite(token.predictedWaitMinutes) && Number.isFinite(token.actualWaitMinutes))
      .map((token) => Math.abs(token.actualWaitMinutes - token.predictedWaitMinutes));

    const avgQueueConsultationMinutes = average(
      queues.map((queue) => queue.avgConsultationMinutes || 0)
    );

    res.json({
      success: true,
      date: dateString,
      doctorId: doctorId || null,
      scope: doctorId ? 'doctor' : 'all_doctors',
      hybridEtaMode: 'moving_average_production',
      etaMlPhase: 'research_only',
      summary: {
        totalTokens,
        waitingCount: waiting.length,
        calledCount: called.length,
        arrivedCount: arrived.length,
        completedCount: completed.length,
        noShowCount: noShows.length,
        lowConfidenceCount: lowConfidence.length,
        manualReviewCount: manualReview.length,
        snoozedCount: snoozed.length,
        avgConsultationMinutes: avgQueueConsultationMinutes,
        avgPredictedWaitMinutes: average(tokens.map((token) => token.predictedWaitMinutes)),
        avgActualWaitMinutes: average(tokens.map((token) => token.actualWaitMinutes)),
        avgPredictedConsultMinutes: average(tokens.map((token) => token.predictedConsultMinutes)),
        avgActualConsultMinutes: average(tokens.map((token) => token.actualConsultMinutes)),
        avgEtaErrorMinutes: average(etaErrors),
        noShowRate: totalTokens ? Number((noShows.length / totalTokens).toFixed(4)) : 0,
        snoozeRate: totalTokens ? Number((snoozed.length / totalTokens).toFixed(4)) : 0,
      },
      byDoctor: doctorId ? [] : buildDoctorMetrics(tokens),
    });
  } catch (err) {
    console.error('Analytics summary error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.get('/analytics/triage-distribution', async (req, res) => {
  try {
    const dateString = resolveDate(req);
    const doctorId = resolveDoctorId(req);
    const tokens = await loadAnalyticsTokens(dateString, doctorId);
    const totalTokens = tokens.length;

    const triageDistribution = [1, 2, 3, 4, 5].map((priorityClass) => ({
      priorityClass,
      count: tokens.filter((token) => token.triagePriorityClass === priorityClass).length,
    }));

    const priorityBuckets = ['high', 'medium', 'normal'].map((priority) => ({
      priority,
      count: tokens.filter((token) => token.priority === priority).length,
    }));

    const triageSources = Object.entries(
      tokens.reduce((acc, token) => {
        const key = token.triageSource || 'unknown';
        acc[key] = (acc[key] || 0) + 1;
        return acc;
      }, {})
    ).map(([source, count]) => ({ source, count }));

    const overrideReasons = Object.entries(
      tokens.reduce((acc, token) => {
        const key = token.overrideReason || 'none';
        acc[key] = (acc[key] || 0) + 1;
        return acc;
      }, {})
    ).map(([reason, count]) => ({ reason, count }));

    const lowConfidenceCount = tokens.filter((token) => token.triageLowConfidence).length;

    res.json({
      success: true,
      date: dateString,
      doctorId: doctorId || null,
      scope: doctorId ? 'doctor' : 'all_doctors',
      totalTokens,
      lowConfidenceCount,
      lowConfidenceShare: totalTokens ? Number((lowConfidenceCount / totalTokens).toFixed(4)) : 0,
      triageDistribution,
      priorityBuckets,
      triageSources,
      overrideReasons,
    });
  } catch (err) {
    console.error('Triage distribution error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.get('/analytics/eta-accuracy', async (req, res) => {
  try {
    const dateString = resolveDate(req);
    const doctorId = resolveDoctorId(req);
    const tokens = await loadAnalyticsTokens(dateString, doctorId);
    const measuredTokens = tokens.filter(
      (token) =>
        Number.isFinite(token.predictedWaitMinutes) &&
        Number.isFinite(token.actualWaitMinutes)
    );

    const etaErrors = measuredTokens.map(
      (token) => token.actualWaitMinutes - token.predictedWaitMinutes
    );

    res.json({
      success: true,
      date: dateString,
      doctorId: doctorId || null,
      scope: doctorId ? 'doctor' : 'all_doctors',
      sampleCount: measuredTokens.length,
      avgPredictedWaitMinutes: average(measuredTokens.map((token) => token.predictedWaitMinutes)),
      avgActualWaitMinutes: average(measuredTokens.map((token) => token.actualWaitMinutes)),
      avgPredictedConsultMinutes: average(tokens.map((token) => token.predictedConsultMinutes)),
      avgActualConsultMinutes: average(tokens.map((token) => token.actualConsultMinutes)),
      meanSignedErrorMinutes: average(etaErrors),
      meanAbsoluteErrorMinutes: average(etaErrors.map((error) => Math.abs(error))),
      totalAbsoluteErrorMinutes: sum(etaErrors.map((error) => Math.abs(error))),
      byDoctor: doctorId ? [] : buildDoctorMetrics(tokens),
    });
  } catch (err) {
    console.error('ETA accuracy error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
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
// POST /api/admin/seed
// Creates Indian dummy doctors and patients for demo/testing.
// Safe to call multiple times — skips existing emails.
// ─────────────────────────────────────────────────────────────
router.post('/seed', async (req, res) => {
  const dummyDoctors = [
    { name: 'Dr. Ananya Krishnamurthy', email: 'ananya@smartq.in', password: 'doc@1234', phone: '+91-9876543201', age: 38, role: 'doctor', specialty: 'Cardiology' },
    { name: 'Dr. Rajesh Patel', email: 'rajesh@smartq.in', password: 'doc@1234', phone: '+91-9876543202', age: 45, role: 'doctor', specialty: 'Orthopaedics' },
    { name: 'Dr. Sunita Sharma', email: 'sunita@smartq.in', password: 'doc@1234', phone: '+91-9876543203', age: 42, role: 'doctor', specialty: 'Neurology' },
    { name: 'Dr. Vikram Nair', email: 'vikram@smartq.in', password: 'doc@1234', phone: '+91-9876543204', age: 50, role: 'doctor', specialty: 'General OPD' },
    { name: 'Dr. Priya Menon', email: 'priya@smartq.in', password: 'doc@1234', phone: '+91-9876543205', age: 36, role: 'doctor', specialty: 'Dermatology' },
    { name: 'Dr. Anil Gupta', email: 'anil@smartq.in', password: 'doc@1234', phone: '+91-9876543206', age: 48, role: 'doctor', specialty: 'Gastroenterology' },
    { name: 'Dr. Kavita Reddy', email: 'kavita@smartq.in', password: 'doc@1234', phone: '+91-9876543207', age: 40, role: 'doctor', specialty: 'Paediatrics' },
    { name: 'Dr. Mohan Iyer', email: 'mohan@smartq.in', password: 'doc@1234', phone: '+91-9876543208', age: 55, role: 'doctor', specialty: 'Pulmonology' }
  ];

  const dummyPatients = [
    { name: 'Arjun Singh', email: 'arjun@patient.in', password: 'patient@1234', phone: '+91-9811111111', age: 35, role: 'patient' },
    { name: 'Priya Sharma', email: 'priya.p@patient.in', password: 'patient@1234', phone: '+91-9822222222', age: 28, role: 'patient' },
    { name: 'Ravi Kumar', email: 'ravi@patient.in', password: 'patient@1234', phone: '+91-9833333333', age: 52, role: 'patient' },
    { name: 'Sunita Devi', email: 'sunita.d@patient.in', password: 'patient@1234', phone: '+91-9844444444', age: 43, role: 'patient' },
    { name: 'Rahul Mehta', email: 'rahul@patient.in', password: 'patient@1234', phone: '+91-9855555555', age: 67, role: 'patient' },
    { name: 'Deepa Nair', email: 'deepa@patient.in', password: 'patient@1234', phone: '+91-9866666666', age: 31, role: 'patient' },
    { name: 'Sanjay Patel', email: 'sanjay@patient.in', password: 'patient@1234', phone: '+91-9877777777', age: 58, role: 'patient' },
    { name: 'Kavitha Reddy', email: 'kavitha@patient.in', password: 'patient@1234', phone: '+91-9888888888', age: 24, role: 'patient' },
    { name: 'Anil Verma', email: 'anil.v@patient.in', password: 'patient@1234', phone: '+91-9899999999', age: 71, role: 'patient' },
    { name: 'Meena Rao', email: 'meena@patient.in', password: 'patient@1234', phone: '+91-9800000001', age: 39, role: 'patient' },
    { name: 'Vikram Joshi', email: 'vikram.j@patient.in', password: 'patient@1234', phone: '+91-9800000002', age: 46, role: 'patient' },
    { name: 'Pooja Iyer', email: 'pooja@patient.in', password: 'patient@1234', phone: '+91-9800000003', age: 22, role: 'patient' }
  ];

  let doctorsCreated = 0, patientsCreated = 0, skipped = 0;

  for (const data of [...dummyDoctors, ...dummyPatients]) {
    try {
      await User.create(data);
      if (data.role === 'doctor') doctorsCreated++;
      else patientsCreated++;
    } catch (err) {
      if (err.code === 11000) skipped++; // duplicate email — skip silently
      else throw err;
    }
  }

  res.json({
    success: true,
    message: `Seed complete. Created: ${doctorsCreated} doctors, ${patientsCreated} patients. Skipped: ${skipped} duplicates.`,
    doctorsCreated,
    patientsCreated,
    skipped
  });
});

module.exports = router;

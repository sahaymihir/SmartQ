const express = require('express');
const router = express.Router();
const { protect, adminOnly } = require('../middleware/authMiddleware');
const { Token, Queue } = require('../models/Queue');
const User = require('../models/User');
const predictionHistory = require('../store/predictionStore');
const { determineTriageDecision } = require('../services/triageService');
const { predictSpecialty } = require('../services/specialtyService');

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

const SYMPTOM_COMPLAINT_HINTS = [
  { complaint: 'cardiac', patterns: [/chest pain/i, /chest pressure/i, /palpitation/i, /heart/i] },
  { complaint: 'respiratory', patterns: [/shortness of breath/i, /breath/i, /cough/i, /wheez/i, /phlegm/i] },
  { complaint: 'neurological', patterns: [/headache/i, /dizz/i, /seizure/i, /stroke/i, /numb/i, /tingling/i] },
  { complaint: 'gastrointestinal', patterns: [/abdominal/i, /stomach/i, /vomit/i, /nausea/i, /diarrhea/i, /constipat/i] },
  { complaint: 'trauma', patterns: [/fracture/i, /injury/i, /fall/i, /sprain/i, /bleeding/i, /trauma/i] },
  { complaint: 'renal', patterns: [/urination/i, /urine/i, /kidney/i, /flank/i] },
  { complaint: 'endocrine', patterns: [/diabetes/i, /sugar/i, /thyroid/i, /thirst/i] },
  { complaint: 'dermatological', patterns: [/rash/i, /itch/i, /eczema/i, /skin/i, /hives/i] },
];

const SPECIALTY_TO_COMPLAINT = {
  Cardiology: 'cardiac',
  Pulmonology: 'respiratory',
  'Infectious Disease': 'respiratory',
  Neurology: 'neurological',
  Gastroenterology: 'gastrointestinal',
  Orthopaedics: 'trauma',
  Dermatology: 'dermatological',
  'Nephrology / Urology': 'renal',
  Endocrinology: 'endocrine',
  Paediatrics: 'respiratory',
};

const inferChiefComplaintSystem = (symptoms = '', primarySpecialist = '') => {
  const text = String(symptoms || '').trim();
  for (const hint of SYMPTOM_COMPLAINT_HINTS) {
    if (hint.patterns.some((pattern) => pattern.test(text))) {
      return hint.complaint;
    }
  }
  return SPECIALTY_TO_COMPLAINT[primarySpecialist] || 'other';
};

const findRecommendedDoctor = async (routedSpecialty) => {
  let recommendedDoc = await User.findOne({
    role: 'doctor',
    specialty: routedSpecialty,
  }).select('name specialty _id');

  let doctorRoutingNote = '';
  if (!recommendedDoc && routedSpecialty !== 'General OPD') {
    recommendedDoc = await User.findOne({
      role: 'doctor',
      specialty: 'General OPD',
    }).select('name specialty _id');
    doctorRoutingNote = ` No exact ${routedSpecialty} doctor was available, so SmartQ fell back to General OPD.`;
  }

  if (!recommendedDoc) {
    recommendedDoc = await User.findOne({ role: 'doctor' }).select('name specialty _id');
    if (recommendedDoc) {
      doctorRoutingNote = ` No staffed ${routedSpecialty} route was available, so SmartQ used the next available doctor.`;
    }
  }

  const recommendedDoctor = recommendedDoc
    ? {
        id: recommendedDoc._id,
        name: recommendedDoc.name,
        specialty: recommendedDoc.specialty || routedSpecialty,
      }
    : {
        id: null,
        name: 'No doctor available',
        specialty: routedSpecialty,
      };

  return { recommendedDoctor, doctorRoutingNote };
};

// ─────────────────────────────────────────────────────────────
// POST /api/admin/model-eval-run
// Admin-only simulation of specialty routing + priority scoring
// ─────────────────────────────────────────────────────────────
router.post('/model-eval-run', async (req, res) => {
  try {
    const symptoms = String(req.body.symptoms || '').trim();
    const age = Number(req.body.age);

    if (!symptoms) {
      return res.status(400).json({ success: false, message: 'Symptoms text is required' });
    }

    if (!Number.isFinite(age) || age < 0 || age > 130) {
      return res.status(400).json({ success: false, message: 'Valid age is required' });
    }

    const specialtyPrediction = await predictSpecialty({
      symptoms,
      age,
      temperature_c: req.body.temperature_c,
      pain_score: req.body.pain_score,
      chief_complaint_system: req.body.chief_complaint_system,
      language: req.body.language || req.body.intakeLanguage,
    });

    const derivedChiefComplaintSystem =
      req.body.chief_complaint_system ||
      inferChiefComplaintSystem(symptoms, specialtyPrediction.primarySpecialist);

    const triageDecision = await determineTriageDecision(
      { age },
      {
        symptomsText: symptoms,
        symptoms,
        age,
        chief_complaint_system: derivedChiefComplaintSystem,
        temperature_c: req.body.temperature_c,
        pain_score: req.body.pain_score,
      }
    );

    const routedSpecialty = specialtyPrediction.routedSpecialty || 'General OPD';
    const { recommendedDoctor, doctorRoutingNote } = await findRecommendedDoctor(routedSpecialty);

    const reasoning = `${specialtyPrediction.reasoning}${doctorRoutingNote}`.trim();

    const evaluation = {
      symptoms,
      age,
      derivedChiefComplaintSystem,
      normalizedSymptoms: specialtyPrediction.normalizedSymptoms,
      extractedFactors: specialtyPrediction.extractedSignals,
      extractedSignals: specialtyPrediction.extractedSignals,
      specialtyScores: specialtyPrediction.specialtyScores,
      alternativeSpecialists: specialtyPrediction.alternativeSpecialists,
      primarySpecialist: specialtyPrediction.primarySpecialist,
      routedSpecialty,
      recommendedDoctor,
      confidence: specialtyPrediction.confidence,
      lowConfidence: specialtyPrediction.lowConfidence,
      reasoning,
      modelSource: specialtyPrediction.modelSource,
      priorityLabel: triageDecision.priorityLabel,
      priorityScore: triageDecision.priorityScore,
      priorityFinalScore: triageDecision.priorityFinalScore,
      triagePriorityClass: triageDecision.triagePriorityClass,
      triageConfidence: triageDecision.triageConfidence,
      triageLowConfidence: triageDecision.triageLowConfidence,
      triageRecommendation: triageDecision.triageRecommendation,
      triageSource: triageDecision.triageSource,
      priorityComponents: triageDecision.priorityComponents,
      priorityDecisionTrace: triageDecision.priorityDecisionTrace,
      triageAllClassProbs: triageDecision.triageAllClassProbs,
      triageModelVersion: triageDecision.triageModelVersion,
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

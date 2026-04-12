const express = require('express');
const rateLimit = require('express-rate-limit');

const { protect } = require('../middleware/authMiddleware');
const User = require('../models/User');
const predictionHistory = require('../store/predictionStore');
const { predictSpecialty } = require('../services/specialtyService');

const router = express.Router();

const listDoctorsLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 60,
  message: { success: false, message: 'Too many requests, please try again later.' },
});

const predictLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  message: { success: false, message: 'Too many prediction requests, please try again later.' },
});

const SPECIALTY_SYNONYM_MAP = {
  cardiologist: 'Cardiology',
  cardiology: 'Cardiology',
  orthopedician: 'Orthopaedics',
  orthopedics: 'Orthopaedics',
  orthopaedics: 'Orthopaedics',
  neurologist: 'Neurology',
  neurology: 'Neurology',
  dermatologist: 'Dermatology',
  dermatology: 'Dermatology',
  gastroenterologist: 'Gastroenterology',
  gastroenterology: 'Gastroenterology',
  pediatrician: 'Paediatrics',
  paediatrics: 'Paediatrics',
  pediatrics: 'Paediatrics',
  pulmonologist: 'Pulmonology',
  pulmonology: 'Pulmonology',
  ent: 'Otolaryngology (ENT)',
  otolaryngology: 'Otolaryngology (ENT)',
  hematologist: 'Hematology',
  hematology: 'Hematology',
  endocrinologist: 'Endocrinology',
  endocrinology: 'Endocrinology',
  nephrologist: 'Nephrology / Urology',
  urologist: 'Nephrology / Urology',
  'nephrology / urology': 'Nephrology / Urology',
  emergency: 'Emergency Medicine',
  'emergency medicine': 'Emergency Medicine',
  'general opd': 'General OPD',
  'general practice': 'General OPD',
};

const normalizeSpecialty = (value) => {
  const text = String(value || '').trim();
  if (!text) {
    return '';
  }

  return SPECIALTY_SYNONYM_MAP[text.toLowerCase()] || text;
};

router.get('/', listDoctorsLimiter, protect, async (req, res) => {
  try {
    const doctors = await User.find({ role: 'doctor' })
      .select('name specialty _id')
      .sort({ name: 1 });

    res.json({
      success: true,
      doctors: doctors.map((doctor) => ({
        id: doctor._id,
        name: doctor.name,
        specialty: doctor.specialty || 'General OPD',
      })),
    });
  } catch (err) {
    console.error('Get doctors error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.post('/symptom-predict', predictLimiter, protect, async (req, res) => {
  try {
    const { symptoms } = req.body;
    if (!symptoms || symptoms.trim().length === 0) {
      return res.status(400).json({ success: false, message: 'Symptoms text is required' });
    }

    const specialtyPrediction = await predictSpecialty({
      symptoms,
      age: req.user.age,
      temperature_c: req.body.temperature_c,
      pain_score: req.body.pain_score,
      chief_complaint_system: req.body.chief_complaint_system,
      language: req.body.language || req.body.intakeLanguage,
    });

    const routedSpecialty = specialtyPrediction.routedSpecialty || 'General OPD';

    const canonicalRoute = normalizeSpecialty(routedSpecialty);

    let recommendedDoc = await User.findOne({
      role: 'doctor',
      specialty: { $regex: `^${canonicalRoute.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, $options: 'i' },
    }).select('name specialty _id');

    let doctorRoutingNote = '';
    if (!recommendedDoc && canonicalRoute !== 'General OPD') {
      recommendedDoc = await User.findOne({
        role: 'doctor',
        specialty: 'General OPD',
      }).select('name specialty _id');
      doctorRoutingNote = ` No exact ${canonicalRoute} doctor was available, so SmartQ fell back to General OPD.`;
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
          specialty: normalizeSpecialty(recommendedDoc.specialty || canonicalRoute),
        }
      : {
          id: null,
          name: 'No doctor available',
            specialty: canonicalRoute,
        };

    const reasoning = `${specialtyPrediction.reasoning}${doctorRoutingNote}`.trim();

    const evaluation = {
      symptoms,
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
      timestamp: new Date().toISOString(),
      patientName: req.user.name,
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
    console.error('Symptom predict error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

module.exports = router;

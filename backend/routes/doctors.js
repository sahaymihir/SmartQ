const express = require('express');
const router = express.Router();
const rateLimit = require('express-rate-limit');
const { protect } = require('../middleware/authMiddleware');
const User = require('../models/User');
const predictionHistory = require('../store/predictionStore');

// ── Rate limiters ────────────────────────────────────────────
const listDoctorsLimiter = rateLimit({
  windowMs: 60 * 1000,  // 1 minute
  max: 60,
  message: { success: false, message: 'Too many requests, please try again later.' }
});

const predictLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 20,
  message: { success: false, message: 'Too many prediction requests, please try again later.' }
});

// ─── Symptom → Specialty keyword map ──────────────────────
// This is the dummy rule-based engine. Replace with ML model call later.
const SPECIALTY_KEYWORDS = [
  {
    specialty: 'Cardiology',
    keywords: [
      'chest pain', 'chest', 'heart', 'cardiac', 'palpitation',
      'angina', 'breathless', 'shortness of breath', 'blood pressure', 'hypertension'
    ]
  },
  {
    specialty: 'Orthopaedics',
    keywords: [
      'bone', 'joint', 'fracture', 'knee', 'back pain', 'shoulder',
      'hip', 'sprain', 'arthritis', 'spine', 'muscle pain'
    ]
  },
  {
    specialty: 'Neurology',
    keywords: [
      'headache', 'migraine', 'dizziness', 'dizzy', 'seizure', 'memory',
      'nerve', 'tremor', 'paralysis', 'stroke', 'numbness', 'tingling'
    ]
  },
  {
    specialty: 'Dermatology',
    keywords: [
      'rash', 'skin', 'acne', 'itching', 'allergy', 'eczema',
      'psoriasis', 'hair loss', 'blisters', 'hives', 'lesion'
    ]
  },
  {
    specialty: 'Gastroenterology',
    keywords: [
      'stomach', 'nausea', 'vomiting', 'diarrhea', 'acidity', 'abdomen',
      'constipation', 'liver', 'jaundice', 'indigestion', 'gas', 'bloating'
    ]
  },
  {
    specialty: 'Paediatrics',
    keywords: [
      'child', 'infant', 'baby', 'growth', 'vaccination', 'toddler', 'kid'
    ]
  },
  {
    specialty: 'Pulmonology',
    keywords: [
      'cough', 'breathing', 'asthma', 'wheeze', 'lung', 'respiratory',
      'tuberculosis', 'tb', 'sputum', 'phlegm'
    ]
  },
  {
    specialty: 'General OPD',
    keywords: [
      'fever', 'flu', 'fatigue', 'weakness', 'pain', 'tired',
      'malaise', 'infection', 'cold', 'body ache', 'viral'
    ]
  }
];

// ─────────────────────────────────────────────────────────────
// GET /api/doctors
// Returns all registered doctors (accessible to all logged-in users)
// ─────────────────────────────────────────────────────────────
router.get('/', listDoctorsLimiter, protect, async (req, res) => {
  try {
    const doctors = await User.find({ role: 'doctor' })
      .select('name specialty _id')
      .sort({ name: 1 });

    res.json({
      success: true,
      doctors: doctors.map(d => ({
        id: d._id,
        name: d.name,
        specialty: d.specialty || 'General OPD'
      }))
    });
  } catch (err) {
    console.error('Get doctors error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/doctors/symptom-predict
// Body: { symptoms: "..." }
// Dummy NLP endpoint — scores symptoms against specialty keyword lists,
// picks the best matching doctor from the database.
// TODO: Replace scoring logic with real ML model call.
// ─────────────────────────────────────────────────────────────
router.post('/symptom-predict', predictLimiter, protect, async (req, res) => {
  try {
    const { symptoms } = req.body;
    if (!symptoms || symptoms.trim().length === 0) {
      return res.status(400).json({ success: false, message: 'Symptoms text is required' });
    }

    const symptomsLower = symptoms.toLowerCase();

    // ── Step 1: keyword extraction ──────────────────────────
    const extractedFactors = [];

    // ── Step 2: score each specialty ───────────────────────
    const specialtyScores = SPECIALTY_KEYWORDS.map(entry => {
      const matchedKeywords = entry.keywords.filter(kw => symptomsLower.includes(kw));
      matchedKeywords.forEach(kw => {
        if (!extractedFactors.includes(kw)) extractedFactors.push(kw);
      });
      // Score = matched proportion, scaled so even 1 match gives a meaningful signal
      const score = matchedKeywords.length === 0
        ? 0.03
        : parseFloat(Math.min(1.0, (matchedKeywords.length / entry.keywords.length) * 3.5).toFixed(2));
      return { specialty: entry.specialty, score, matchedKeywords };
    });

    // Sort descending by score
    specialtyScores.sort((a, b) => b.score - a.score);

    const topSpecialty = specialtyScores[0].specialty;
    const confidence = specialtyScores[0].score;

    // ── Step 3: find a matching doctor from DB ──────────────
    let recommendedDoc = await User.findOne({ role: 'doctor', specialty: topSpecialty }).select('name specialty _id');
    // Fallback to General OPD if specialty not covered
    if (!recommendedDoc) {
      recommendedDoc = await User.findOne({ role: 'doctor', specialty: 'General OPD' }).select('name specialty _id');
    }

    const recommendedDoctor = recommendedDoc
      ? { id: recommendedDoc._id, name: recommendedDoc.name, specialty: recommendedDoc.specialty }
      : { id: null, name: 'No doctor available', specialty: topSpecialty };

    const topKeywords = (specialtyScores[0].matchedKeywords || []).slice(0, 4).join(', ');
    const reasoning = topKeywords
      ? `Detected "${topKeywords}" — these symptoms most commonly indicate ${topSpecialty}. Confidence: ${(confidence * 100).toFixed(0)}%.`
      : `No strong specialty signal detected. Defaulting to General OPD. Confidence: ${(confidence * 100).toFixed(0)}%.`;

    const evaluation = {
      symptoms,
      extractedFactors,
      specialtyScores,
      recommendedDoctor,
      confidence,
      reasoning,
      timestamp: new Date().toISOString(),
      patientName: req.user.name,
      patientId: req.user._id
    };

    // Store in shared history (capped at 100 entries)
    predictionHistory.unshift(evaluation);
    if (predictionHistory.length > 100) predictionHistory.pop();

    res.json({ success: true, ...evaluation });
  } catch (err) {
    console.error('Symptom predict error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

module.exports = router;

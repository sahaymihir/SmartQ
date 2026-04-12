const User = require('../models/User');
const Prescription = require('../models/Prescription');
const { Queue, Token } = require('../models/Queue');
const { savePrescriptionForToken } = require('./prescriptionService');
const {
  computeETA,
  getPriorityLabel,
  getTodayDateString,
  IMMEDIATE_REVIEW_LANE,
} = require('../utils/queueHelpers');
const predictionHistory = require('../store/predictionStore');
const { addMlOpsLog, clearMlOpsLogs } = require('../store/mlOpsLogStore');
const seededUsers = require('../seeded-users.json');

const MINUTE_MS = 60 * 1000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

const PRIORITY_SCORE_BY_CLASS = {
  1: 10,
  2: 10,
  3: 7,
  4: 5,
  5: 5,
};

const TRIAGE_RECOMMENDATION_BY_CLASS = {
  1: 'Resuscitation',
  2: 'Very urgent',
  3: 'Urgent',
  4: 'Less urgent',
  5: 'Non-urgent',
};

const historicalVisitTemplates = [
  {
    key: 'harsha_cardiology_review',
    patientEmail: 'harsha.patient@smartq.in',
    doctorEmail: 'dr.ananya@smartq.in',
    daysAgo: 18,
    joinedHour: 9,
    tokenNumber: 1,
    symptoms: 'Blood pressure spikes with occasional chest heaviness while climbing stairs',
    visitType: 'new',
    triagePriorityClass: 3,
    actualWaitMinutes: 22,
    actualConsultMinutes: 14,
    prescription: {
      symptomsSummary: 'BP review with intermittent exertional chest heaviness and headache.',
      testsDone: 'ECG, fasting lipid profile, BP monitoring chart',
      medications: 'Telmisartan 40 mg OD, Rosuvastatin 10 mg HS',
      conclusion: 'Stage 1 hypertension with stable exertional angina symptoms',
      adviceNotes: 'Low-salt diet, daily walking, review after 2 weeks or earlier if chest pain worsens.',
    },
  },
  {
    key: 'sanjay_ortho_follow_up_source',
    patientEmail: 'sanjay.patient@smartq.in',
    doctorEmail: 'dr.rajesh@smartq.in',
    daysAgo: 24,
    joinedHour: 11,
    tokenNumber: 1,
    symptoms: 'Persistent right knee pain with swelling after a twisting injury',
    visitType: 'new',
    triagePriorityClass: 3,
    actualWaitMinutes: 19,
    actualConsultMinutes: 16,
    prescription: {
      symptomsSummary: 'Right knee pain and swelling after twisting injury at work.',
      testsDone: 'Knee X-ray, ligament stability exam',
      medications: 'Aceclofenac 100 mg BD after food, topical diclofenac gel',
      conclusion: 'Knee sprain with early osteoarthritis flare',
      adviceNotes: 'Use hinged knee support, avoid squatting, start physiotherapy review in 3 weeks.',
    },
  },
  {
    key: 'anil_cardiology_review_source',
    patientEmail: 'anil.patient@smartq.in',
    doctorEmail: 'dr.ananya@smartq.in',
    daysAgo: 10,
    joinedHour: 10,
    tokenNumber: 2,
    symptoms: 'Pressure in chest after walking half a block with occasional sweating',
    visitType: 'new',
    triagePriorityClass: 2,
    actualWaitMinutes: 11,
    actualConsultMinutes: 18,
    prescription: {
      symptomsSummary: 'Exertional chest pressure in senior patient with diabetes and hypertension.',
      testsDone: 'ECG, Troponin I, 2D Echo, HbA1c',
      medications: 'Aspirin 75 mg OD, Metoprolol 25 mg BD, Nitroglycerin SOS',
      conclusion: 'High-risk chronic coronary syndrome under evaluation',
      adviceNotes: 'Avoid exertion, keep nitroglycerin handy, return urgently for rest pain or sweating.',
    },
  },
  {
    key: 'priya_derm_review',
    patientEmail: 'priya.patient@smartq.in',
    doctorEmail: 'dr.priya@smartq.in',
    daysAgo: 15,
    joinedHour: 14,
    tokenNumber: 1,
    symptoms: 'Dry itchy patches over elbows and neck worsening after detergents',
    visitType: 'new',
    triagePriorityClass: 4,
    actualWaitMinutes: 14,
    actualConsultMinutes: 12,
    prescription: {
      symptomsSummary: 'Itchy eczematous rash over flexural areas with irritant exposure.',
      testsDone: 'Clinical examination only',
      medications: 'Mometasone cream OD, Cetirizine 10 mg HS, fragrance-free moisturizer',
      conclusion: 'Irritant eczema flare',
      adviceNotes: 'Avoid harsh soaps, moisturize thrice daily, review if rash spreads or oozes.',
    },
  },
  {
    key: 'pooja_derm_review',
    patientEmail: 'pooja.patient@smartq.in',
    doctorEmail: 'dr.priya@smartq.in',
    daysAgo: 45,
    joinedHour: 16,
    tokenNumber: 2,
    symptoms: 'Acne flare with painful cheek nodules and post-inflammatory marks',
    visitType: 'new',
    triagePriorityClass: 4,
    actualWaitMinutes: 17,
    actualConsultMinutes: 11,
    prescription: {
      symptomsSummary: 'Moderate inflammatory acne with painful cheek lesions.',
      testsDone: 'Clinical examination only',
      medications: 'Clindamycin gel BD, Benzoyl peroxide wash OD',
      conclusion: 'Inflammatory acne vulgaris',
      adviceNotes: 'Do not squeeze lesions, wash face gently, review after 4 weeks.',
    },
  },
  {
    key: 'deepa_pulmo_review',
    patientEmail: 'deepa.patient@smartq.in',
    doctorEmail: 'dr.mohan@smartq.in',
    daysAgo: 9,
    joinedHour: 12,
    tokenNumber: 1,
    symptoms: 'Night-time wheeze and dry cough after dust exposure',
    visitType: 'new',
    triagePriorityClass: 3,
    actualWaitMinutes: 16,
    actualConsultMinutes: 15,
    prescription: {
      symptomsSummary: 'Recurrent wheeze with dry cough, worse at night and with dust exposure.',
      testsDone: 'Peak flow check, chest auscultation',
      medications: 'Budesonide inhaler BID, Salbutamol inhaler SOS',
      conclusion: 'Bronchial asthma flare',
      adviceNotes: 'Avoid dust, review inhaler technique, seek urgent care if rescue inhaler is needed repeatedly.',
    },
  },
  {
    key: 'fatima_gastro_review',
    patientEmail: 'fatima.patient@smartq.in',
    doctorEmail: 'dr.anil@smartq.in',
    daysAgo: 27,
    joinedHour: 13,
    tokenNumber: 1,
    symptoms: 'Burning upper abdominal pain with bloating after meals',
    visitType: 'new',
    triagePriorityClass: 4,
    actualWaitMinutes: 21,
    actualConsultMinutes: 13,
    prescription: {
      symptomsSummary: 'Post-meal burning epigastric pain with bloating and reflux symptoms.',
      testsDone: 'Abdominal exam, H. pylori advice, LFT review',
      medications: 'Pantoprazole 40 mg OD, Antacid syrup SOS',
      conclusion: 'Acid peptic disease / gastritis',
      adviceNotes: 'Avoid spicy meals, early dinner, review if vomiting or black stools develop.',
    },
  },
  {
    key: 'aarohi_peds_review',
    patientEmail: 'aarohi.patient@smartq.in',
    doctorEmail: 'dr.kavita@smartq.in',
    daysAgo: 32,
    joinedHour: 10,
    tokenNumber: 1,
    symptoms: 'Fever with runny nose and reduced appetite for two days',
    visitType: 'new',
    triagePriorityClass: 4,
    actualWaitMinutes: 13,
    actualConsultMinutes: 10,
    prescription: {
      symptomsSummary: 'Child with fever, cold symptoms, and reduced oral intake.',
      testsDone: 'ENT exam, hydration check',
      medications: 'Paracetamol syrup SOS, saline nasal drops',
      conclusion: 'Viral upper respiratory infection',
      adviceNotes: 'Plenty of fluids, monitor urine output, return if breathing becomes fast or noisy.',
    },
  },
  {
    key: 'kiran_neuro_review',
    patientEmail: 'kiran.patient@smartq.in',
    doctorEmail: 'dr.sunita@smartq.in',
    daysAgo: 12,
    joinedHour: 15,
    tokenNumber: 1,
    symptoms: 'Recurrent one-sided headache with light sensitivity and nausea',
    visitType: 'new',
    triagePriorityClass: 4,
    actualWaitMinutes: 18,
    actualConsultMinutes: 16,
    prescription: {
      symptomsSummary: 'Migraine pattern headache with photophobia and nausea.',
      testsDone: 'Focused neurological exam',
      medications: 'Sumatriptan SOS, Naproxen 250 mg BD as needed',
      conclusion: 'Migraine without focal neurological deficit',
      adviceNotes: 'Maintain sleep hygiene, hydrate well, keep headache diary for review.',
    },
  },
  {
    key: 'naveen_general_review',
    patientEmail: 'naveen.patient@smartq.in',
    doctorEmail: 'dr.farah@smartq.in',
    daysAgo: 5,
    joinedHour: 17,
    tokenNumber: 1,
    symptoms: 'Fatigue, poor sleep, and work stress for the past month',
    visitType: 'new',
    triagePriorityClass: 5,
    actualWaitMinutes: 12,
    actualConsultMinutes: 12,
    prescription: {
      symptomsSummary: 'Fatigue and poor sleep linked to work stress; no red-flag symptoms.',
      testsDone: 'General examination, sleep history',
      medications: 'Multivitamin OD for 30 days',
      conclusion: 'Stress-related fatigue with sleep disturbance',
      adviceNotes: 'Sleep hygiene, evening screen reduction, follow up if persistent after 2 weeks.',
    },
  },
];

const activeQueueTemplates = [
  {
    doctorEmail: 'dr.vikram@smartq.in',
    avgConsultationMinutes: 9,
    consultationDurations: [8, 10, 9, 11, 7],
    tokens: [
      {
        patientEmail: 'aarav.patient@smartq.in',
        tokenNumber: 1,
        status: 'called',
        symptoms: 'Fever, cough, body ache, and tiredness for three days',
        joinedMinutesAgo: 56,
        calledMinutesAgo: 12,
        consultationStartedMinutesAgo: 10,
        checkedIn: true,
        triagePriorityClass: 4,
        modelPriorityClass: 4,
        triageConfidence: 0.82,
        sex: 'M',
        chiefComplaintSystem: 'respiratory',
        draftPrescription: {
          authorEmail: 'aisha.admin@smartq.in',
          symptomsSummary: 'Fever with productive cough and diffuse body ache.',
          testsDone: 'CBC requested at triage desk',
          medications: '',
          conclusion: '',
          adviceNotes: 'Hydration encouraged while awaiting doctor completion.',
          status: 'draft',
        },
      },
      {
        patientEmail: 'rahul.patient@smartq.in',
        tokenNumber: 2,
        status: 'waiting',
        symptoms: 'Chest tightness with breathlessness since early morning',
        joinedMinutesAgo: 28,
        checkedIn: true,
        triagePriorityClass: 2,
        modelPriorityClass: 3,
        triageConfidence: 0.91,
        triageLowConfidence: false,
        sex: 'M',
        chiefComplaintSystem: 'cardiac',
        routingLane: IMMEDIATE_REVIEW_LANE,
        requiresImmediateReview: true,
        escalationReason: 'low SpO2 during nurse triage',
        manualReviewRequired: true,
        nurseTriaged: true,
        nurseTriageNote: 'Senior patient became more breathless while waiting near the desk.',
        nurseVitals: {
          heart_rate: 112,
          respiratory_rate: 28,
          spo2: 89,
          temperature_c: 37.4,
          systolic_bp: 152,
          diastolic_bp: 96,
          gcs_total: 15,
          pain_score: 7,
          news2_score: 8,
          mental_status_triage: 'alert',
        },
        testRecommendations: [
          { test: 'ECG', rationale: 'screen for acute ischemia', urgency: 'immediate' },
          { test: 'Pulse oximetry repeat', rationale: 'confirm worsening oxygenation', urgency: 'immediate' },
        ],
      },
      {
        patientEmail: 'sneha.patient@smartq.in',
        tokenNumber: 3,
        status: 'waiting',
        symptoms: 'Fever, sore throat, and painful swallowing since yesterday',
        joinedMinutesAgo: 20,
        checkedIn: true,
        triagePriorityClass: 4,
        modelPriorityClass: 4,
        triageConfidence: 0.77,
        sex: 'F',
        chiefComplaintSystem: 'respiratory',
      },
      {
        patientEmail: 'naresh.patient@smartq.in',
        tokenNumber: 4,
        status: 'waiting',
        symptoms: 'Dizziness and blood pressure review after missing tablets for one week',
        joinedMinutesAgo: 14,
        checkedIn: false,
        triagePriorityClass: 4,
        modelPriorityClass: 4,
        triageConfidence: 0.69,
        triageLowConfidence: true,
        manualReviewRequired: true,
        sex: 'M',
        chiefComplaintSystem: 'cardiac',
      },
    ],
  },
  {
    doctorEmail: 'dr.rajesh@smartq.in',
    avgConsultationMinutes: 12,
    consultationDurations: [11, 13, 12, 14],
    tokens: [
      {
        patientEmail: 'sanjay.patient@smartq.in',
        tokenNumber: 1,
        status: 'called',
        symptoms: 'Follow-up for right knee pain with swelling after previous injury',
        joinedMinutesAgo: 34,
        calledMinutesAgo: 8,
        consultationStartedMinutesAgo: 6,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 3,
        triageConfidence: 0.88,
        visitType: 'follow_up',
        followUpKey: 'sanjay_ortho_follow_up_source',
        sex: 'M',
        chiefComplaintSystem: 'trauma',
      },
      {
        patientEmail: 'leela.patient@smartq.in',
        tokenNumber: 2,
        status: 'waiting',
        symptoms: 'Left wrist swelling and pain after slipping in the bathroom',
        joinedMinutesAgo: 17,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 4,
        triageConfidence: 0.74,
        nurseTriaged: true,
        nurseTriageNote: 'Visible swelling over distal forearm; pain worsens on movement.',
        nurseVitals: {
          heart_rate: 92,
          respiratory_rate: 18,
          spo2: 98,
          temperature_c: 36.8,
          systolic_bp: 138,
          diastolic_bp: 84,
          gcs_total: 15,
          pain_score: 8,
          news2_score: 2,
          mental_status_triage: 'alert',
        },
        sex: 'F',
        chiefComplaintSystem: 'trauma',
      },
    ],
  },
  {
    doctorEmail: 'dr.ananya@smartq.in',
    avgConsultationMinutes: 11,
    consultationDurations: [10, 11, 12, 9],
    tokens: [
      {
        patientEmail: 'anil.patient@smartq.in',
        tokenNumber: 1,
        status: 'called',
        symptoms: 'Exertional chest pressure review after last week cardiac workup',
        joinedMinutesAgo: 31,
        calledMinutesAgo: 9,
        consultationStartedMinutesAgo: 7,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 3,
        triageConfidence: 0.93,
        visitType: 'follow_up',
        followUpKey: 'anil_cardiology_review_source',
        sex: 'M',
        chiefComplaintSystem: 'cardiac',
      },
      {
        patientEmail: 'geeta.patient@smartq.in',
        tokenNumber: 2,
        status: 'waiting',
        symptoms: 'Palpitations and light-headedness after skipping breakfast',
        joinedMinutesAgo: 18,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 3,
        triageConfidence: 0.79,
        sex: 'F',
        chiefComplaintSystem: 'cardiac',
      },
    ],
  },
  {
    doctorEmail: 'dr.sunita@smartq.in',
    avgConsultationMinutes: 14,
    consultationDurations: [12, 15, 14],
    tokens: [
      {
        patientEmail: 'kiran.patient@smartq.in',
        tokenNumber: 1,
        status: 'waiting',
        symptoms: 'Follow-up for migraine; headache returned with photophobia this morning',
        joinedMinutesAgo: 26,
        checkedIn: false,
        triagePriorityClass: 4,
        modelPriorityClass: 4,
        triageConfidence: 0.68,
        triageLowConfidence: true,
        manualReviewRequired: true,
        visitType: 'follow_up',
        followUpKey: 'kiran_neuro_review',
        sex: 'M',
        chiefComplaintSystem: 'neurological',
      },
    ],
  },
  {
    doctorEmail: 'dr.priya@smartq.in',
    avgConsultationMinutes: 10,
    consultationDurations: [9, 11, 10],
    tokens: [
      {
        patientEmail: 'pooja.patient@smartq.in',
        tokenNumber: 1,
        status: 'waiting',
        symptoms: 'Follow-up for itchy rash flare on arms and neck after detergent exposure',
        joinedMinutesAgo: 22,
        checkedIn: true,
        triagePriorityClass: 4,
        modelPriorityClass: 4,
        triageConfidence: 0.83,
        visitType: 'follow_up',
        followUpKey: 'pooja_derm_review',
        sex: 'F',
        chiefComplaintSystem: 'dermatological',
      },
    ],
  },
  {
    doctorEmail: 'dr.anil@smartq.in',
    avgConsultationMinutes: 13,
    consultationDurations: [12, 13, 14],
    tokens: [
      {
        patientEmail: 'fatima.patient@smartq.in',
        tokenNumber: 1,
        status: 'waiting',
        symptoms: 'Follow-up for acidity; burning pain returned after spicy food',
        joinedMinutesAgo: 24,
        checkedIn: false,
        triagePriorityClass: 4,
        modelPriorityClass: 4,
        triageConfidence: 0.81,
        visitType: 'follow_up',
        followUpKey: 'fatima_gastro_review',
        sex: 'F',
        chiefComplaintSystem: 'gastrointestinal',
      },
    ],
  },
  {
    doctorEmail: 'dr.kavita@smartq.in',
    avgConsultationMinutes: 8,
    consultationDurations: [8, 8, 9],
    tokens: [
      {
        patientEmail: 'aarohi.patient@smartq.in',
        tokenNumber: 1,
        status: 'called',
        symptoms: 'Fever, ear pain, and crying at night since yesterday',
        joinedMinutesAgo: 18,
        calledMinutesAgo: 5,
        consultationStartedMinutesAgo: 3,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 3,
        triageConfidence: 0.86,
        visitType: 'follow_up',
        followUpKey: 'aarohi_peds_review',
        sex: 'F',
        chiefComplaintSystem: 'respiratory',
      },
    ],
  },
  {
    doctorEmail: 'dr.mohan@smartq.in',
    avgConsultationMinutes: 15,
    consultationDurations: [14, 15, 16],
    tokens: [
      {
        patientEmail: 'deepa.patient@smartq.in',
        tokenNumber: 1,
        status: 'waiting',
        symptoms: 'Follow-up for asthma; wheeze returns at night during weather change',
        joinedMinutesAgo: 29,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 3,
        triageConfidence: 0.84,
        visitType: 'follow_up',
        followUpKey: 'deepa_pulmo_review',
        sex: 'F',
        chiefComplaintSystem: 'respiratory',
      },
    ],
  },
  {
    doctorEmail: 'dr.farah@smartq.in',
    avgConsultationMinutes: 9,
    consultationDurations: [8, 10, 9],
    tokens: [
      {
        patientEmail: 'naveen.patient@smartq.in',
        tokenNumber: 1,
        status: 'waiting',
        symptoms: 'Follow-up for fatigue and poor sleep; still waking up tired',
        joinedMinutesAgo: 12,
        checkedIn: false,
        triagePriorityClass: 5,
        modelPriorityClass: 5,
        triageConfidence: 0.64,
        triageLowConfidence: true,
        manualReviewRequired: true,
        visitType: 'follow_up',
        followUpKey: 'naveen_general_review',
        sex: 'M',
        chiefComplaintSystem: 'other',
      },
    ],
  },
  {
    doctorEmail: 'dr.arvind@smartq.in',
    avgConsultationMinutes: 10,
    consultationDurations: [10, 9, 11],
    tokens: [
      {
        patientEmail: 'harsha.patient@smartq.in',
        tokenNumber: 1,
        status: 'waiting',
        symptoms: 'Follow-up for hypertension with occasional chest heaviness on exertion',
        joinedMinutesAgo: 16,
        checkedIn: true,
        triagePriorityClass: 3,
        modelPriorityClass: 3,
        triageConfidence: 0.87,
        visitType: 'follow_up',
        followUpKey: 'harsha_cardiology_review',
        sex: 'M',
        chiefComplaintSystem: 'cardiac',
      },
    ],
  },
];

const modelEvalHistoryTemplates = [
  {
    symptoms: 'broken right leg, left leg, hand, and skull',
    age: 5,
    derivedChiefComplaintSystem: 'trauma',
    normalizedSymptoms: 'broken right and left leg and hand and skull',
    routedSpecialty: 'Orthopaedics',
    primarySpecialist: 'Orthopaedics',
    confidence: 0.33,
    lowConfidence: true,
    extractedSignals: ['trauma complaint', 'severe pain score'],
    alternativeSpecialists: [
      { specialist: 'Orthopaedics', routedSpecialty: 'Orthopaedics', confidence: 0.56, matchedSignals: ['trauma complaint'] },
      { specialist: 'Emergency Medicine', routedSpecialty: 'General OPD', confidence: 0.35, matchedSignals: ['trauma complaint', 'severe pain score'] },
      { specialist: 'General Practice', routedSpecialty: 'General OPD', confidence: 0.09, matchedSignals: [] },
    ],
    reasoning: 'Primary clinical fit is Orthopaedics. Trauma signals are strong but cross-specialty overlap still requires manual review.',
    modelSource: 'specialty_hybrid_v1',
    triagePriorityClass: 3,
    modelPriorityClass: 3,
    triageConfidence: 1,
    triageLowConfidence: false,
    triageRecommendation: 'Urgent',
    triageSource: 'ml_v3',
    guardrailedPriorityClass: 3,
    guardrailedRecommendation: 'Urgent',
    queueSelectedRoute: 'Orthopaedics',
    queueRouteType: 'primary',
    queueRationale: 'Selected Orthopaedics using route hint; queue=0, doctors=1',
    queueCurrentLength: 0,
    queueAvailableDoctors: 1,
    queueAvgWaitMinutes: 0,
    safetyMatches: [],
    testRecommendations: [
      { test: 'Full trauma series X-rays', rationale: 'identify fractures', urgency: 'immediate' },
      { test: 'FAST ultrasound', rationale: 'detect haemoperitoneum', urgency: 'immediate' },
      { test: 'CBC + coagulation screen', rationale: 'baseline haematology', urgency: 'urgent' },
    ],
    testSource: 'rule_based_v1',
    patientName: 'Demo Trauma Child',
  },
  {
    symptoms: 'chest pressure radiating to left arm with sweating',
    age: 58,
    derivedChiefComplaintSystem: 'cardiac',
    normalizedSymptoms: 'chest pressure radiating to left arm with sweating',
    routedSpecialty: 'Cardiology',
    primarySpecialist: 'Cardiology',
    confidence: 0.84,
    lowConfidence: false,
    extractedSignals: ['ischemic chest pain', 'left arm radiation', 'diaphoresis'],
    alternativeSpecialists: [
      { specialist: 'Cardiology', routedSpecialty: 'Cardiology', confidence: 0.84, matchedSignals: ['ischemic chest pain', 'left arm radiation'] },
      { specialist: 'Emergency Medicine', routedSpecialty: 'General OPD', confidence: 0.11, matchedSignals: ['diaphoresis'] },
      { specialist: 'General Practice', routedSpecialty: 'General OPD', confidence: 0.05, matchedSignals: [] },
    ],
    reasoning: 'Symptoms strongly match acute coronary syndrome patterns, so SmartQ prioritised Cardiology.',
    modelSource: 'specialty_hybrid_v1',
    triagePriorityClass: 2,
    modelPriorityClass: 2,
    triageConfidence: 0.96,
    triageLowConfidence: false,
    triageRecommendation: 'Very urgent',
    triageSource: 'ml_v3',
    guardrailedPriorityClass: 2,
    guardrailedRecommendation: 'Very urgent',
    queueSelectedRoute: 'Cardiology',
    queueRouteType: 'primary',
    queueRationale: 'Selected Cardiology because staffed specialist queue had lowest safe wait.',
    queueCurrentLength: 2,
    queueAvailableDoctors: 2,
    queueAvgWaitMinutes: 11,
    safetyMatches: [
      { ruleId: 'cardiac_red_flag', severity: 'high', forcedPriorityClass: 2, preferredRoute: 'Cardiology', rationale: 'classic ischemic chest pain pattern' },
    ],
    testRecommendations: [
      { test: 'ECG', rationale: 'rule out acute coronary syndrome', urgency: 'immediate' },
      { test: 'Troponin I', rationale: 'assess myocardial injury', urgency: 'immediate' },
      { test: '2D Echo', rationale: 'evaluate wall motion changes', urgency: 'urgent' },
    ],
    testSource: 'rule_based_v1',
    patientName: 'Demo Cardiology Walk-in',
  },
  {
    symptoms: 'itchy red rash on forearms after using new detergent',
    age: 27,
    derivedChiefComplaintSystem: 'dermatological',
    normalizedSymptoms: 'itchy red rash on forearms after using new detergent',
    routedSpecialty: 'Dermatology',
    primarySpecialist: 'Dermatology',
    confidence: 0.76,
    lowConfidence: false,
    extractedSignals: ['itchy rash', 'contact trigger', 'forearm distribution'],
    alternativeSpecialists: [
      { specialist: 'Dermatology', routedSpecialty: 'Dermatology', confidence: 0.76, matchedSignals: ['itchy rash', 'contact trigger'] },
      { specialist: 'General Practice', routedSpecialty: 'General OPD', confidence: 0.18, matchedSignals: ['forearm distribution'] },
      { specialist: 'Infectious Disease', routedSpecialty: 'General OPD', confidence: 0.06, matchedSignals: [] },
    ],
    reasoning: 'Localised itchy rash with clear contact trigger strongly supports a dermatology route.',
    modelSource: 'specialty_hybrid_v1',
    triagePriorityClass: 4,
    modelPriorityClass: 4,
    triageConfidence: 0.72,
    triageLowConfidence: false,
    triageRecommendation: 'Less urgent',
    triageSource: 'ml_v3',
    guardrailedPriorityClass: 4,
    guardrailedRecommendation: 'Less urgent',
    queueSelectedRoute: 'Dermatology',
    queueRouteType: 'primary',
    queueRationale: 'Dermatology queue had low load and no safety override was needed.',
    queueCurrentLength: 1,
    queueAvailableDoctors: 1,
    queueAvgWaitMinutes: 8,
    safetyMatches: [],
    testRecommendations: [
      { test: 'Patch test review', rationale: 'consider contact trigger if rash persists', urgency: 'routine' },
    ],
    testSource: 'rule_based_v1',
    patientName: 'Demo Rash Review',
  },
];

const mlOpsLogTemplates = [
  {
    operation: 'triage_predict',
    source: 'triageService',
    url: '/priority',
    result: 'success',
    attempt: 1,
    maxAttempts: 2,
    status: 200,
    latencyMs: 242,
  },
  {
    operation: 'specialty_predict',
    source: 'specialtyService',
    url: '/specialty',
    result: 'failure',
    attempt: 1,
    maxAttempts: 2,
    willRetry: true,
    retryDelayMs: 500,
    status: 503,
    errorCode: 'ECONNRESET',
    errorMessage: 'Upstream specialty model timed out on first attempt.',
    latencyMs: 10002,
  },
  {
    operation: 'specialty_predict',
    source: 'specialtyService',
    url: '/specialty',
    result: 'success',
    attempt: 2,
    maxAttempts: 2,
    status: 200,
    latencyMs: 531,
  },
  {
    operation: 'patient_flow',
    source: 'patientFlowService',
    url: '/patient-flow',
    result: 'success',
    attempt: 1,
    maxAttempts: 1,
    status: 200,
    latencyMs: 417,
  },
  {
    operation: 'tests_recommend',
    source: 'patientFlowService',
    url: '/tests',
    result: 'success',
    attempt: 1,
    maxAttempts: 1,
    status: 200,
    latencyMs: 286,
  },
];

const findUserByEmail = (usersByEmail, email, label) => {
  const user = usersByEmail.get(email);
  if (!user) {
    throw new Error(`Demo seed user not found for ${label}: ${email}`);
  }
  return user;
};

const scoreFromPriorityClass = (priorityClass) =>
  PRIORITY_SCORE_BY_CLASS[priorityClass] || PRIORITY_SCORE_BY_CLASS[5];

const recommendationFromClass = (priorityClass) =>
  TRIAGE_RECOMMENDATION_BY_CLASS[priorityClass] || TRIAGE_RECOMMENDATION_BY_CLASS[5];

const buildCompletedDate = (daysAgo, hour = 10) =>
  new Date(Date.now() - (daysAgo * DAY_MS) + (hour * HOUR_MS));

const buildJoinedDate = (minutesAgo) => new Date(Date.now() - (minutesAgo * MINUTE_MS));

const buildQueueConsultationHistory = (durations = []) =>
  durations.map((durationMinutes, index) => ({
    durationMinutes,
    completedAt: new Date(Date.now() - ((index + 1) * HOUR_MS)),
  }));

const buildPriorityComponents = (age, priorityClass, clinicianOverride = 0) => ({
  age: age >= 60 ? 10 : 5,
  triage: scoreFromPriorityClass(priorityClass),
  symptomNlp: priorityClass <= 3 ? 1 : 0.5,
  ocrFlags: 0,
  clinicianOverride,
});

const buildTokenBase = ({
  patient,
  doctor,
  tokenNumber,
  status,
  position,
  etaMinutes,
  joinedAt,
  calledAt,
  consultationStartedAt,
  symptoms,
  triagePriorityClass,
  modelPriorityClass,
  triageConfidence,
  triageLowConfidence,
  manualReviewRequired,
  routingLane,
  requiresImmediateReview,
  escalationReason,
  checkedIn,
  checkedInAt,
  visitType,
  followUpTokenId,
  sex,
  chiefComplaintSystem,
  nurseTriaged,
  nurseVitals,
  nurseTriageNote,
  testRecommendations,
}) => {
  const priorityClass = triagePriorityClass || 5;
  const priorityScore = scoreFromPriorityClass(priorityClass);
  const recommendation = recommendationFromClass(priorityClass);

  return {
    patient: patient._id,
    doctor: doctor._id,
    tokenNumber,
    position,
    status,
    priority: getPriorityLabel(priorityScore),
    priorityScore,
    etaMinutes,
    checkedIn,
    checkedInAt,
    symptoms,
    intakeLanguage: 'en',
    aiConfidence: triageConfidence,
    ageBasedPriorityScore: patient.age >= 60 ? 10 : 5,
    mlPriorityScore: scoreFromPriorityClass(modelPriorityClass || priorityClass),
    modelPriorityClass,
    triagePriorityClass: priorityClass,
    triageConfidence,
    triageLowConfidence,
    triageRecommendation: recommendation,
    triageAllClassProbs: {
      [String(priorityClass)]: Number(Math.max(triageConfidence, 0.5).toFixed(2)),
    },
    triageModelVersion: 'ml_v3',
    triageSource: routingLane === IMMEDIATE_REVIEW_LANE ? 'nurse_triage' : 'ml_v3',
    overrideReason: requiresImmediateReview ? 'manual_safety_escalation' : 'standard_flow',
    manualReviewRequired,
    routingLane,
    requiresImmediateReview,
    escalationReason: escalationReason || '',
    safetyMatches: requiresImmediateReview
      ? [
          {
            ruleId: 'nurse_red_flag',
            severity: 'high',
            forcedPriorityClass: triagePriorityClass,
            preferredRoute: doctor.specialty || 'General OPD',
            rationale: escalationReason || 'Manual safety escalation',
          },
        ]
      : [],
    priorityComponents: buildPriorityComponents(patient.age, priorityClass, requiresImmediateReview ? 1 : 0),
    priorityFinalScore: priorityScore,
    priorityDecisionTrace: `source=${routingLane === IMMEDIATE_REVIEW_LANE ? 'nurse_triage' : 'ml_v3'};class=${priorityClass};score=${priorityScore};manualReview=${manualReviewRequired}`,
    visitSnapshot: {
      symptoms,
      sex: sex || null,
      chief_complaint_system: chiefComplaintSystem || 'other',
    },
    joinedAt,
    calledAt: calledAt || null,
    consultationStartedAt: consultationStartedAt || null,
    completedAt: null,
    predictedWaitMinutes: etaMinutes,
    actualWaitMinutes: null,
    predictedConsultMinutes: 0,
    actualConsultMinutes: null,
    visitType,
    followUpTokenId: followUpTokenId || null,
    nurseTriaged: Boolean(nurseTriaged),
    nurseTriagedAt: nurseTriaged ? new Date(joinedAt.getTime() + (5 * MINUTE_MS)) : null,
    nurseVitals: nurseTriaged ? nurseVitals || {} : null,
    nurseTriageNote: nurseTriageNote || '',
    testRecommendations: Array.isArray(testRecommendations) ? testRecommendations : [],
    testSuggestedAt: Array.isArray(testRecommendations) && testRecommendations.length
      ? new Date(joinedAt.getTime() + (3 * MINUTE_MS))
      : null,
  };
};

const createUsers = async () => {
  const usersByEmail = new Map();

  for (const userData of seededUsers.users) {
    const user = await User.create(userData);
    usersByEmail.set(user.email, user);
  }

  return usersByEmail;
};

const createHistoricalVisits = async (usersByEmail) => {
  const historicalTokensByKey = new Map();

  for (const template of historicalVisitTemplates) {
    const patient = findUserByEmail(usersByEmail, template.patientEmail, 'historical patient');
    const doctor = findUserByEmail(usersByEmail, template.doctorEmail, 'historical doctor');
    const completedAt = buildCompletedDate(template.daysAgo, template.joinedHour);
    const joinedAt = new Date(completedAt.getTime() - (template.actualWaitMinutes * MINUTE_MS) - (template.actualConsultMinutes * MINUTE_MS));
    const calledAt = new Date(completedAt.getTime() - (template.actualConsultMinutes * MINUTE_MS));
    const consultationStartedAt = calledAt;

    const token = await Token.create({
      patient: patient._id,
      doctor: doctor._id,
      tokenNumber: template.tokenNumber,
      position: 0,
      status: 'completed',
      priority: getPriorityLabel(scoreFromPriorityClass(template.triagePriorityClass)),
      priorityScore: scoreFromPriorityClass(template.triagePriorityClass),
      etaMinutes: 0,
      checkedIn: true,
      checkedInAt: new Date(joinedAt.getTime() + (2 * MINUTE_MS)),
      symptoms: template.symptoms,
      intakeLanguage: 'en',
      aiConfidence: template.triageConfidence || 0.84,
      ageBasedPriorityScore: patient.age >= 60 ? 10 : 5,
      mlPriorityScore: scoreFromPriorityClass(template.triagePriorityClass),
      modelPriorityClass: template.triagePriorityClass,
      triagePriorityClass: template.triagePriorityClass,
      triageConfidence: template.triageConfidence || 0.84,
      triageLowConfidence: false,
      triageRecommendation: recommendationFromClass(template.triagePriorityClass),
      triageAllClassProbs: { [String(template.triagePriorityClass)]: 0.84 },
      triageModelVersion: 'ml_v3',
      triageSource: 'ml_v3',
      overrideReason: 'standard_flow',
      manualReviewRequired: false,
      routingLane: 'normal',
      requiresImmediateReview: false,
      escalationReason: '',
      safetyMatches: [],
      priorityComponents: buildPriorityComponents(patient.age, template.triagePriorityClass),
      priorityFinalScore: scoreFromPriorityClass(template.triagePriorityClass),
      priorityDecisionTrace: `source=ml_v3;class=${template.triagePriorityClass};score=${scoreFromPriorityClass(template.triagePriorityClass)}`,
      visitSnapshot: {
        symptoms: template.symptoms,
      },
      joinedAt,
      calledAt,
      consultationStartedAt,
      completedAt,
      predictedWaitMinutes: template.actualWaitMinutes,
      actualWaitMinutes: template.actualWaitMinutes,
      predictedConsultMinutes: template.actualConsultMinutes,
      actualConsultMinutes: template.actualConsultMinutes,
      visitType: template.visitType || 'new',
    });

    await savePrescriptionForToken(token, doctor, {
      ...template.prescription,
      status: 'finalized',
    });

    historicalTokensByKey.set(template.key, token);
  }

  return historicalTokensByKey;
};

const createActiveQueues = async (usersByEmail, historicalTokensByKey) => {
  let activeTokenCount = 0;

  for (const queueTemplate of activeQueueTemplates) {
    const doctor = findUserByEmail(usersByEmail, queueTemplate.doctorEmail, 'queue doctor');
    const currentCalledToken = queueTemplate.tokens.find((token) =>
      ['called', 'arrived'].includes(token.status)
    );

    const queue = await Queue.create({
      doctor: doctor._id,
      date: getTodayDateString(),
      isActive: true,
      isPaused: false,
      currentToken: currentCalledToken ? currentCalledToken.tokenNumber : 0,
      totalTokensIssued: queueTemplate.tokens.length,
      avgConsultationMinutes: queueTemplate.avgConsultationMinutes,
      consultationHistory: buildQueueConsultationHistory(queueTemplate.consultationDurations),
    });

    let nextNormalPosition = 1;

    for (const tokenTemplate of queueTemplate.tokens) {
      const patient = findUserByEmail(usersByEmail, tokenTemplate.patientEmail, 'queue patient');
      const joinedAt = buildJoinedDate(tokenTemplate.joinedMinutesAgo || 10);
      const calledAt = tokenTemplate.calledMinutesAgo != null
        ? buildJoinedDate(tokenTemplate.calledMinutesAgo)
        : null;
      const consultationStartedAt = tokenTemplate.consultationStartedMinutesAgo != null
        ? buildJoinedDate(tokenTemplate.consultationStartedMinutesAgo)
        : null;
      const routingLane = tokenTemplate.routingLane || 'normal';
      const isNormalWaiting = tokenTemplate.status === 'waiting' && routingLane !== IMMEDIATE_REVIEW_LANE;
      const position = isNormalWaiting ? nextNormalPosition++ : 0;
      const etaMinutes = isNormalWaiting
        ? computeETA(position, queue.avgConsultationMinutes)
        : 0;
      const followUpToken = tokenTemplate.followUpKey
        ? historicalTokensByKey.get(tokenTemplate.followUpKey)
        : null;

      const token = await Token.create(buildTokenBase({
        patient,
        doctor,
        tokenNumber: tokenTemplate.tokenNumber,
        status: tokenTemplate.status,
        position,
        etaMinutes,
        joinedAt,
        calledAt,
        consultationStartedAt,
        symptoms: tokenTemplate.symptoms,
        triagePriorityClass: tokenTemplate.triagePriorityClass,
        modelPriorityClass: tokenTemplate.modelPriorityClass,
        triageConfidence: tokenTemplate.triageConfidence || 0.8,
        triageLowConfidence: Boolean(tokenTemplate.triageLowConfidence),
        manualReviewRequired: Boolean(tokenTemplate.manualReviewRequired),
        routingLane,
        requiresImmediateReview: Boolean(tokenTemplate.requiresImmediateReview),
        escalationReason: tokenTemplate.escalationReason,
        checkedIn: Boolean(tokenTemplate.checkedIn),
        checkedInAt: tokenTemplate.checkedIn ? new Date(joinedAt.getTime() + (3 * MINUTE_MS)) : null,
        visitType: tokenTemplate.visitType || 'new',
        followUpTokenId: followUpToken ? followUpToken._id : null,
        sex: tokenTemplate.sex,
        chiefComplaintSystem: tokenTemplate.chiefComplaintSystem,
        nurseTriaged: Boolean(tokenTemplate.nurseTriaged),
        nurseVitals: tokenTemplate.nurseVitals,
        nurseTriageNote: tokenTemplate.nurseTriageNote,
        testRecommendations: tokenTemplate.testRecommendations,
      }));

      if (tokenTemplate.draftPrescription) {
        const author = findUserByEmail(
          usersByEmail,
          tokenTemplate.draftPrescription.authorEmail || queueTemplate.doctorEmail,
          'draft prescription author'
        );

        await savePrescriptionForToken(token, author, {
          symptomsSummary: tokenTemplate.draftPrescription.symptomsSummary || '',
          testsDone: tokenTemplate.draftPrescription.testsDone || '',
          medications: tokenTemplate.draftPrescription.medications || '',
          conclusion: tokenTemplate.draftPrescription.conclusion || '',
          adviceNotes: tokenTemplate.draftPrescription.adviceNotes || '',
          status: tokenTemplate.draftPrescription.status || 'draft',
        });
      }

      activeTokenCount += 1;
    }
  }

  return activeTokenCount;
};

const seedModelEvalHistory = () => {
  predictionHistory.length = 0;

  const now = Date.now();
  modelEvalHistoryTemplates.forEach((template, index) => {
    predictionHistory.push({
      success: true,
      symptoms: template.symptoms,
      age: template.age,
      derivedChiefComplaintSystem: template.derivedChiefComplaintSystem,
      normalizedSymptoms: template.normalizedSymptoms,
      extractedFactors: template.extractedSignals,
      extractedSignals: template.extractedSignals,
      specialtyScores: template.alternativeSpecialists.map((alt) => ({
        specialty: alt.specialist,
        routedSpecialty: alt.routedSpecialty,
        score: alt.confidence,
        matchedKeywords: alt.matchedSignals,
        matchedSignals: alt.matchedSignals,
      })),
      alternativeSpecialists: template.alternativeSpecialists,
      primarySpecialist: template.primarySpecialist,
      routedSpecialty: template.routedSpecialty,
      queueSelectedRoute: template.queueSelectedRoute,
      queueRouteType: template.queueRouteType,
      queueRationale: template.queueRationale,
      queueCurrentLength: template.queueCurrentLength,
      queueAvailableDoctors: template.queueAvailableDoctors,
      queueAvgWaitMinutes: template.queueAvgWaitMinutes,
      confidence: template.confidence,
      lowConfidence: template.lowConfidence,
      reasoning: template.reasoning,
      modelSource: template.modelSource,
      triagePriorityClass: template.triagePriorityClass,
      triageConfidence: template.triageConfidence,
      triageLowConfidence: template.triageLowConfidence,
      triageRecommendation: template.triageRecommendation,
      triageSource: template.triageSource,
      modelPriorityClass: template.modelPriorityClass,
      guardrailedPriorityClass: template.guardrailedPriorityClass,
      guardrailedRecommendation: template.guardrailedRecommendation,
      safetyMatches: template.safetyMatches,
      testRecommendations: template.testRecommendations,
      testSource: template.testSource,
      priorityFinalScore: scoreFromPriorityClass(template.guardrailedPriorityClass),
      priorityScore: scoreFromPriorityClass(template.modelPriorityClass),
      priorityLabel: getPriorityLabel(scoreFromPriorityClass(template.guardrailedPriorityClass)),
      priorityDecisionTrace: `seeded_demo_case=${template.derivedChiefComplaintSystem};route=${template.routedSpecialty}`,
      timestamp: new Date(now - (index * 30 * MINUTE_MS)).toISOString(),
      patientName: template.patientName,
      patientId: null,
      recommendedDoctor: {
        id: null,
        name: 'Seeded demo route',
        specialty: template.queueSelectedRoute,
      },
    });
  });
};

const seedMlOpsLogs = () => {
  clearMlOpsLogs();

  const now = Date.now();
  mlOpsLogTemplates
    .slice()
    .reverse()
    .forEach((entry, index) => {
      addMlOpsLog({
        ...entry,
        timestamp: new Date(now - ((mlOpsLogTemplates.length - index) * 4 * MINUTE_MS)).toISOString(),
      });
    });
};

const buildCredentialsSummary = () => ({
  superuser: {
    email: 'superadmin@smartq.in',
    password: 'Super@123',
  },
  admin: {
    email: 'aisha.admin@smartq.in',
    password: 'Admin@123',
  },
  doctor: {
    email: 'dr.vikram@smartq.in',
    password: 'Doctor@123',
  },
  nurse: {
    email: 'radha.nurse@smartq.in',
    password: 'Nurse@123',
  },
  patient: {
    email: 'harsha.patient@smartq.in',
    password: 'Patient@123',
  },
});

const resetAndSeedDemoData = async () => {
  await Prescription.deleteMany({});
  await Token.deleteMany({});
  await Queue.deleteMany({});
  await User.deleteMany({});

  predictionHistory.length = 0;
  clearMlOpsLogs();

  const usersByEmail = await createUsers();
  const historicalTokensByKey = await createHistoricalVisits(usersByEmail);
  const activeTokensCreated = await createActiveQueues(usersByEmail, historicalTokensByKey);

  seedModelEvalHistory();
  seedMlOpsLogs();

  const counts = {
    superusersCreated: seededUsers.users.filter((user) => user.role === 'superuser').length,
    adminsCreated: seededUsers.users.filter((user) => user.role === 'admin').length,
    doctorsCreated: seededUsers.users.filter((user) => user.role === 'doctor').length,
    nursesCreated: seededUsers.users.filter((user) => user.role === 'nurse').length,
    patientsCreated: seededUsers.users.filter((user) => user.role === 'patient').length,
    historicalVisitsCreated: historicalVisitTemplates.length,
    activeTokensCreated,
    modelEvalHistorySeeded: predictionHistory.length,
    mlOpsLogsSeeded: mlOpsLogTemplates.length,
  };

  return {
    message: `Demo database reset complete. Created ${counts.superusersCreated} superuser, ${counts.adminsCreated} admins, ${counts.doctorsCreated} doctors, ${counts.nursesCreated} nurses, ${counts.patientsCreated} patients, ${counts.activeTokensCreated} live queue tokens, and ${counts.historicalVisitsCreated} completed visits with prescriptions.`,
    counts,
    credentials: buildCredentialsSummary(),
  };
};

module.exports = {
  resetAndSeedDemoData,
};

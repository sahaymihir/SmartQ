const mongoose = require('mongoose');

// ═══════════════════════════════════════════════════
// TOKEN MODEL
// One token = one patient's spot in one queue session
// ═══════════════════════════════════════════════════
const tokenSchema = new mongoose.Schema({
  patient: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  doctor: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  tokenNumber: {
    type: Number,
    required: true
  },
  position: {
    type: Number,
    required: true
  },
  status: {
    type: String,
    enum: ['waiting', 'called', 'arrived', 'completed', 'no_show', 'cancelled'],
    default: 'waiting'
  },
  priority: {
    type: String,
    enum: ['high', 'medium', 'normal'],
    default: 'normal'
  },
  priorityScore: {
    type: Number,
    default: 5
  },
  // ETA in minutes from time of joining
  etaMinutes: {
    type: Number,
    default: 0
  },
  // Geofence check-in
  checkedIn: {
    type: Boolean,
    default: false
  },
  checkedInAt: {
    type: Date
  },
  // Patient's chief complaint (for AI triage)
  symptoms: {
    type: String,
    default: ''
  },
  // AI model confidence percentage
  aiConfidence: {
    type: Number,
    default: 0
  },
  // SmartQ v2 visit-level triage details
  ageBasedPriorityScore: {
    type: Number,
    default: 5
  },
  mlPriorityScore: {
    type: Number,
    default: null
  },
  triagePriorityClass: {
    type: Number,
    min: 1,
    max: 5,
    default: null
  },
  triageConfidence: {
    type: Number,
    default: 0
  },
  triageLowConfidence: {
    type: Boolean,
    default: false
  },
  triageRecommendation: {
    type: String,
    default: ''
  },
  triageAllClassProbs: {
    type: mongoose.Schema.Types.Mixed,
    default: () => ({})
  },
  triageModelVersion: {
    type: String,
    default: 'rules-v1'
  },
  triageSource: {
    type: String,
    default: 'age_rule_fallback'
  },
  overrideReason: {
    type: String,
    default: 'standard_age_rule'
  },
  manualReviewRequired: {
    type: Boolean,
    default: false
  },
  visitSnapshot: {
    type: mongoose.Schema.Types.Mixed,
    default: () => ({})
  },
  joinedAt: {
    type: Date,
    default: Date.now
  },
  calledAt: {
    type: Date,
    default: null
  },
  consultationStartedAt: {
    type: Date,
    default: null
  },
  completedAt: {
    type: Date,
    default: null
  },
  predictedWaitMinutes: {
    type: Number,
    default: 0
  },
  actualWaitMinutes: {
    type: Number,
    default: null
  },
  predictedConsultMinutes: {
    type: Number,
    default: 0
  },
  actualConsultMinutes: {
    type: Number,
    default: null
  },
  // Snooze tracking
  snoozeCount: {
    type: Number,
    default: 0
  }
}, {
  timestamps: true
});

tokenSchema.index({ doctor: 1, createdAt: 1, status: 1, position: 1 });
tokenSchema.index({ patient: 1, createdAt: 1, status: 1 });

// ═══════════════════════════════════════════════════
// QUEUE MODEL
// One queue per doctor per day
// ═══════════════════════════════════════════════════
const queueSchema = new mongoose.Schema({
  doctor: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true
  },
  date: {
    type: String, // "YYYY-MM-DD"
    required: true
  },
  isActive: {
    type: Boolean,
    default: true
  },
  isPaused: {
    type: Boolean,
    default: false
  },
  currentToken: {
    type: Number,
    default: 0
  },
  totalTokensIssued: {
    type: Number,
    default: 0
  },
  // Moving average of consultation durations (in minutes)
  // Updated after each patient completes consultation
  avgConsultationMinutes: {
    type: Number,
    default: 8   // default assumption: 8 mins per patient
  },
  consultationHistory: [{
    durationMinutes: Number,
    completedAt: Date
  }]
}, {
  timestamps: true
});

// ─── Moving average ETA update helper ──────────────────────
// Call this after each consultation completes
queueSchema.methods.updateAvgConsultation = function (durationMinutes) {
  const history = this.consultationHistory;
  history.push({ durationMinutes, completedAt: new Date() });

  // Keep only last 10 consultations for moving average
  if (history.length > 10) history.shift();

  const avg = history.reduce((sum, h) => sum + h.durationMinutes, 0) / history.length;
  this.avgConsultationMinutes = Math.round(avg);
  this.consultationHistory = history;
};

const Token = mongoose.model('Token', tokenSchema);
const Queue = mongoose.model('Queue', queueSchema);

module.exports = { Token, Queue };

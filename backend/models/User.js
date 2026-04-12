const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

/**
 * User Schema
 *
 * role: "patient", "doctor", or "admin"
 * age: still used as a conservative baseline for queue priority
 * priorityScore: legacy baseline score kept for backward compatibility.
 *   Visit-level triage now lives on Token documents.
 */
const userSchema = new mongoose.Schema({
  name: {
    type: String,
    required: [true, 'Name is required'],
    trim: true
  },
  email: {
    type: String,
    required: [true, 'Email is required'],
    unique: true,
    lowercase: true,
    trim: true
  },
  password: {
    type: String,
    required: [true, 'Password is required'],
    minlength: 6
  },
  phone: {
    type: String,
    required: [true, 'Phone is required'],
    trim: true
  },
  age: {
    type: Number,
    required: [true, 'Age is required'],
    min: 1,
    max: 120
  },
  role: {
    type: String,
    enum: ['patient', 'admin', 'doctor'],
    default: 'patient'
  },
  // Specialty — only relevant for role: 'doctor'
  specialty: {
    type: String,
    trim: true,
    default: ''
  },
  // Legacy baseline priority score for compatibility with older clients.
  // SmartQ v2 uses token-level triage for the final visit decision.
  // Computed priority score — higher = more urgent
  // Seniors (60+): base score 10, others: base score 5
  // Severity bumps added later via queue snooze/triage
  priorityScore: {
    type: Number,
    default: 5
  },
  // Firebase Cloud Messaging device token for push notifications
  fcmToken: {
    type: String,
    default: null
  }
}, {
  timestamps: true
});

// ─── Hash password before saving ──────────────────────────
userSchema.pre('save', async function (next) {
  if (!this.isModified('password')) return next();

  const salt = await bcrypt.genSalt(10);
  this.password = await bcrypt.hash(this.password, salt);

  // Preserve the age-based baseline as a compatibility field.
  this.priorityScore = this.age >= 60 ? 10 : 5;

  next();
});

// ─── Compare entered password with hashed ─────────────────
userSchema.methods.comparePassword = async function (enteredPassword) {
  return await bcrypt.compare(enteredPassword, this.password);
};

// ─── Hide password in JSON responses ──────────────────────
userSchema.methods.toJSON = function () {
  const obj = this.toObject();
  delete obj.password;
  return obj;
};

module.exports = mongoose.model('User', userSchema);

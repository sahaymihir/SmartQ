const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
const {
  isValidEmail,
  isValidPhone,
  normalizeEmail,
  normalizePhone,
} = require('../utils/userValidation');

/**
 * User Schema
 *
 * role: "patient" | "admin" | "doctor" | "nurse" | "superuser"
 * age: used for priority triage (seniors 60+ get bumped up)
 * priorityScore: computed at registration, used by triage algorithm
 * staffId: auto-generated unique ID for clinical staff (doctor/nurse/admin/superuser)
 *   Format: DOC-XXXX / NRS-XXXX / ADM-XXXX / SU-XXXX
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
    trim: true,
    set: normalizeEmail,
    validate: {
      validator: isValidEmail,
      message: 'Email must be in a valid format like name@example.com',
    }
  },
  password: {
    type: String,
    required: [true, 'Password is required'],
    minlength: 6
  },
  phone: {
    type: String,
    required: [true, 'Phone is required'],
    trim: true,
    set: normalizePhone,
    validate: {
      validator: isValidPhone,
      message: 'Phone number must be exactly 10 digits',
    }
  },
  age: {
    type: Number,
    required: [true, 'Age is required'],
    min: 1,
    max: 120
  },
  role: {
    type: String,
    enum: ['patient', 'admin', 'doctor', 'nurse', 'superuser'],
    default: 'patient'
  },
  // Specialty — only relevant for role: 'doctor'
  specialty: {
    type: String,
    trim: true,
    default: ''
  },
  /**
   * Staff ID — unique human-readable identifier for clinical staff.
   * Auto-generated on first save for roles: doctor, nurse, admin, superuser.
   * Patients do not get a staffId.
   * Examples: DOC-0001, NRS-0002, ADM-0001, SU-0001
   */
  staffId: {
    type: String,
    unique: true,
    sparse: true,     // allows many nulls (patients have no staffId)
    trim: true,
    default: undefined
  },
  // Computed priority score — higher = more urgent
  // Seniors (60+): base score 10, others: base score 5
  // Severity bumps added later via queue snooze/triage
  priorityScore: {
    type: Number,
    default: 5
  },
  // Last time patient confirmed arrival at hospital premises.
  // Used to enforce check-in before queue join.
  lastHospitalCheckInAt: {
    type: Date,
    default: null
  },
  // Optional FCM token for device push notifications.
  fcmToken: {
    type: String,
    default: null,
    trim: true
  }
}, {
  timestamps: true
});

// ─── Staff ID prefix map ───────────────────────────────────
const STAFF_ID_PREFIXES = {
  doctor:    'DOC',
  nurse:     'NRS',
  admin:     'ADM',
  superuser: 'SU',
};

// ─── Generate staffId for clinical staff on first save ─────
userSchema.pre('save', async function (next) {
  if (!STAFF_ID_PREFIXES[this.role]) {
    this.staffId = undefined;
    return next();
  }

  if (this.isNew && !this.staffId) {
    const prefix = STAFF_ID_PREFIXES[this.role];
    const count = await this.constructor.countDocuments({ role: this.role });
    this.staffId = `${prefix}-${String(count + 1).padStart(4, '0')}`;
  }
  next();
});

// ─── Hash password before saving ──────────────────────────
userSchema.pre('save', async function (next) {
  if (!this.isModified('password')) return next();

  const salt = await bcrypt.genSalt(10);
  this.password = await bcrypt.hash(this.password, salt);

  // Set base priority score from age
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

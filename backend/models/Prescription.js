const mongoose = require('mongoose');

const prescriptionSchema = new mongoose.Schema({
  tokenId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Token',
    required: true,
    unique: true,
    index: true,
  },
  patientId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true,
  },
  doctorId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    required: true,
    index: true,
  },
  authoredBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'User',
    default: null,
  },
  authorRole: {
    type: String,
    enum: ['doctor', 'admin', 'superuser'],
    default: 'doctor',
  },
  status: {
    type: String,
    enum: ['draft', 'finalized'],
    default: 'draft',
  },
  symptomsSummary: {
    type: String,
    default: '',
  },
  testsDone: {
    type: String,
    default: '',
  },
  medications: {
    type: String,
    default: '',
  },
  conclusion: {
    type: String,
    default: '',
  },
  adviceNotes: {
    type: String,
    default: '',
  },
  finalizedAt: {
    type: Date,
    default: null,
  },
}, {
  timestamps: true,
});

module.exports = mongoose.model('Prescription', prescriptionSchema);

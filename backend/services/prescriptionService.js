const Prescription = require('../models/Prescription');

const normalizeText = (value) => (typeof value === 'string' ? value.trim() : '');

const hasText = (value) => normalizeText(value).length > 0;

const hasLegacyPrescription = (token) => {
  const legacy = token?.prescription || {};
  return Boolean(
    token?.prescriptionId ||
    hasText(legacy.diagnosis) ||
    hasText(legacy.medicines) ||
    hasText(legacy.notes) ||
    legacy.prescribedAt ||
    legacy.uploadedFilePath
  );
};

const buildCompatibilityNotes = ({ symptomsSummary, testsDone, adviceNotes }) => {
  const parts = [];
  if (hasText(symptomsSummary)) {
    parts.push(`Symptoms: ${normalizeText(symptomsSummary)}`);
  }
  if (hasText(testsDone)) {
    parts.push(`Tests: ${normalizeText(testsDone)}`);
  }
  if (hasText(adviceNotes)) {
    parts.push(`Advice: ${normalizeText(adviceNotes)}`);
  }
  return parts.join('\n\n');
};

const buildStructuredFromLegacy = (token) => {
  const legacy = token?.prescription || {};
  return {
    tokenId: token._id,
    patientId: token.patient?._id || token.patient,
    doctorId: token.doctor?._id || token.doctor,
    authoredBy: legacy.prescribedBy || null,
    authorRole: 'doctor',
    status: legacy.prescribedAt ? 'finalized' : 'draft',
    symptomsSummary: '',
    testsDone: '',
    medications: normalizeText(legacy.medicines),
    conclusion: normalizeText(legacy.diagnosis),
    adviceNotes: normalizeText(legacy.notes),
    finalizedAt: legacy.prescribedAt || null,
  };
};

const buildPrescriptionView = (token, prescription) => {
  const legacy = token?.prescription || {};

  return {
    prescriptionId: prescription?._id || token?.prescriptionId || null,
    tokenId: token?._id || null,
    patientId: token?.patient?._id || token?.patient || null,
    doctorId: token?.doctor?._id || token?.doctor || null,
    status: prescription?.status || (legacy.prescribedAt ? 'finalized' : 'draft'),
    symptomsSummary: normalizeText(prescription?.symptomsSummary),
    testsDone: normalizeText(prescription?.testsDone),
    medications: normalizeText(prescription?.medications || legacy.medicines),
    conclusion: normalizeText(prescription?.conclusion || legacy.diagnosis),
    adviceNotes: normalizeText(prescription?.adviceNotes || legacy.notes),
    authoredBy: prescription?.authoredBy || legacy.prescribedBy || null,
    authorRole: prescription?.authorRole || 'doctor',
    finalizedAt: prescription?.finalizedAt || legacy.prescribedAt || null,
    hasPrescription: Boolean(prescription || hasLegacyPrescription(token)),
  };
};

const applyPrescriptionShadowToToken = (token, prescription) => {
  const current = token.prescription?.toObject
    ? token.prescription.toObject()
    : (token.prescription || {});
  const isFinalized = prescription.status === 'finalized';

  token.prescriptionId = prescription._id;
  token.prescription = {
    ...current,
    diagnosis: normalizeText(prescription.conclusion),
    medicines: normalizeText(prescription.medications),
    notes: buildCompatibilityNotes(prescription),
    prescribedAt: isFinalized
      ? (prescription.finalizedAt || current.prescribedAt || new Date())
      : (current.prescribedAt || null),
    prescribedBy: prescription.authoredBy || current.prescribedBy || null,
    source: current.source || 'doctor_typed',
    ocrStatus: isFinalized ? 'doctor_confirmed' : (current.ocrStatus || 'none'),
    ocrConfidence: current.ocrConfidence ?? null,
    ocrExtractedText: current.ocrExtractedText || '',
    uploadedFilePath: current.uploadedFilePath || null,
    needsReview: isFinalized ? false : Boolean(current.needsReview),
  };
};

const findPrescriptionForToken = async (token) => {
  if (!token?._id) {
    return null;
  }

  let prescription = null;
  if (token.prescriptionId) {
    const prescriptionId = token.prescriptionId._id || token.prescriptionId;
    prescription = await Prescription.findById(prescriptionId);
  }
  if (!prescription) {
    prescription = await Prescription.findOne({ tokenId: token._id });
  }

  if (prescription && (!token.prescriptionId || String(token.prescriptionId) !== String(prescription._id))) {
    token.prescriptionId = prescription._id;
    await token.save();
  }

  return prescription;
};

const ensurePrescriptionForToken = async (token) => {
  let prescription = await findPrescriptionForToken(token);
  if (!prescription && hasLegacyPrescription(token)) {
    prescription = await Prescription.create(buildStructuredFromLegacy(token));
    token.prescriptionId = prescription._id;
    await token.save();
  }
  return prescription;
};

const savePrescriptionForToken = async (token, user, payload = {}) => {
  let prescription = await findPrescriptionForToken(token);

  if (!prescription) {
    prescription = new Prescription({
      tokenId: token._id,
      patientId: token.patient?._id || token.patient,
      doctorId: token.doctor?._id || token.doctor,
    });
  }

  prescription.patientId = token.patient?._id || token.patient;
  prescription.doctorId = token.doctor?._id || token.doctor;
  prescription.authoredBy = user?._id || prescription.authoredBy || null;
  prescription.authorRole = ['admin', 'doctor', 'superuser'].includes(user?.role)
    ? user.role
    : (prescription.authorRole || 'doctor');

  if (Object.prototype.hasOwnProperty.call(payload, 'symptomsSummary')) {
    prescription.symptomsSummary = normalizeText(payload.symptomsSummary);
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'testsDone')) {
    prescription.testsDone = normalizeText(payload.testsDone);
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'medications')) {
    prescription.medications = normalizeText(payload.medications);
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'conclusion')) {
    prescription.conclusion = normalizeText(payload.conclusion);
  }
  if (Object.prototype.hasOwnProperty.call(payload, 'adviceNotes')) {
    prescription.adviceNotes = normalizeText(payload.adviceNotes);
  }

  const requestedStatus = payload.status === 'finalized' ? 'finalized' : 'draft';
  prescription.status = prescription.status === 'finalized' ? 'finalized' : requestedStatus;
  if (prescription.status === 'finalized' && !prescription.finalizedAt) {
    prescription.finalizedAt = new Date();
  }

  await prescription.save();
  applyPrescriptionShadowToToken(token, prescription);
  await token.save();

  return prescription;
};

const hasFinalizedPrescription = async (token) => {
  const prescription = await findPrescriptionForToken(token);
  if (prescription?.status === 'finalized') {
    return true;
  }
  return Boolean(token?.prescription?.prescribedAt);
};

module.exports = {
  buildPrescriptionView,
  ensurePrescriptionForToken,
  findPrescriptionForToken,
  hasFinalizedPrescription,
  hasLegacyPrescription,
  normalizeText,
  savePrescriptionForToken,
};

/**
 * prescriptions.js — Express router
 *
 * OCR-based prescription document intake pipeline:
 *
 *   POST   /api/prescriptions/upload            — upload a prescription image/PDF (multer)
 *   POST   /api/prescriptions/:tokenId/ocr-extract — run OCR on an uploaded file and populate draft fields
 *   PATCH  /api/prescriptions/:tokenId/confirm  — doctor reviews + confirms extracted fields
 *
 * OCR extraction delegates to an optional OCR_SERVICE_URL microservice (e.g. a Tesseract wrapper).
 * If OCR_SERVICE_URL is unset, a structured placeholder extraction is returned so the pipeline
 * remains functional during development or when the OCR service is unavailable.
 */

const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs');
const multer = require('multer');
const axios = require('axios');
const { protect, adminOnly } = require('../middleware/authMiddleware');
const { Queue, Token } = require('../models/Queue');
const {
  buildPrescriptionView,
  ensurePrescriptionForToken,
  savePrescriptionForToken,
} = require('../services/prescriptionService');
const {
  diffMinutes,
  recomputeWaitingQueue,
} = require('../utils/queueHelpers');

const OCR_SERVICE_URL = process.env.OCR_SERVICE_URL || null;
const UPLOAD_DIR = path.join(__dirname, '..', 'uploads', 'prescriptions');

// Ensure upload directory exists
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// ─── Multer config ─────────────────────────────────────────────
const ALLOWED_MIME_TYPES = new Set([
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/tiff',
  'application/pdf',
]);

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, UPLOAD_DIR),
  filename: (_req, file, cb) => {
    const ts = Date.now();
    const ext = path.extname(file.originalname).toLowerCase();
    cb(null, `rx_${ts}${ext}`);
  },
});

const fileFilter = (_req, file, cb) => {
  if (ALLOWED_MIME_TYPES.has(file.mimetype)) {
    cb(null, true);
  } else {
    cb(new Error('Only JPEG, PNG, WebP, TIFF and PDF files are accepted'), false);
  }
};

const upload = multer({
  storage,
  fileFilter,
  limits: {
    fileSize: 10 * 1024 * 1024, // 10 MB
    files: 1,
  },
});

const canAccessPrescription = (token, user, { write = false } = {}) => {
  if (!token || !user) {
    return false;
  }

  const tokenDoctorId = String(token.doctor?._id || token.doctor || '');
  const tokenPatientId = String(token.patient?._id || token.patient || '');
  const userId = String(user._id || '');

  if (['admin', 'superuser'].includes(user.role)) {
    return true;
  }

  if (user.role === 'doctor') {
    return tokenDoctorId === userId;
  }

  if (!write && user.role === 'patient') {
    return tokenPatientId === userId && token.status === 'completed';
  }

  return false;
};

const getTokenQueueDate = (token) => {
  const sourceDate = token?.createdAt || new Date();
  return new Date(sourceDate).toISOString().split('T')[0];
};

// ─── OCR extraction helper ─────────────────────────────────────

/**
 * Call the external OCR microservice if configured; otherwise return
 * a structured placeholder so the pipeline can be tested end-to-end.
 */
const extractTextFromFile = async (filePath, mimeType) => {
  if (OCR_SERVICE_URL) {
    try {
      const fileBuffer = fs.readFileSync(filePath);
      const response = await axios.post(
        `${OCR_SERVICE_URL}/extract`,
        { file: fileBuffer.toString('base64'), mimeType },
        { timeout: 30000 }
      );
      const data = response.data || {};
      return {
        rawText: data.text || '',
        confidence: typeof data.confidence === 'number' ? data.confidence : null,
        parsedFields: data.parsedFields || {},
      };
    } catch (err) {
      console.warn('OCR service error:', err.message);
    }
  }

  // Placeholder extraction — returns empty fields so the doctor
  // is prompted to fill them in via the confirm endpoint.
  return {
    rawText: '',
    confidence: null,
    parsedFields: {
      diagnosis: '',
      medicines: '',
      notes: '',
    },
  };
};

// ─── Routes ───────────────────────────────────────────────────

/**
 * POST /api/prescriptions/upload
 * Accepts a prescription image or PDF and attaches it as a draft
 * on the specified token. Requires doctor or admin role.
 *
 * Form fields:
 *   tokenId  — the queue token to attach the file to
 *   file     — the image / PDF (multipart)
 */
router.post('/upload', protect, adminOnly, upload.single('file'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, message: 'No file uploaded' });
    }

    const { tokenId } = req.body;
    if (!tokenId) {
      // Clean up the orphaned upload
      fs.unlink(req.file.path, () => {});
      return res.status(400).json({ success: false, message: 'tokenId is required' });
    }

    const token = await Token.findById(tokenId);
    if (!token) {
      fs.unlink(req.file.path, () => {});
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (req.user.role === 'doctor' && token.doctor.toString() !== req.user._id.toString()) {
      fs.unlink(req.file.path, () => {});
      return res.status(403).json({
        success: false,
        message: 'You can only upload prescriptions for patients in your own queue',
      });
    }

    // Persist the upload path and mark as pending OCR extraction
    if (!token.prescription) token.prescription = {};
    token.prescription.ocrStatus = 'draft_extracted';
    token.prescription.source = 'ocr_extracted';
    token.prescription.uploadedFilePath = req.file.path;
    token.prescription.needsReview = true;
    await token.save();

    res.json({
      success: true,
      message: 'File uploaded. Run OCR extraction to populate prescription fields.',
      tokenId: token._id,
      fileName: req.file.filename,
    });
  } catch (err) {
    if (req.file?.path) fs.unlink(req.file.path, () => {});
    console.error('Prescription upload error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

/**
 * POST /api/prescriptions/:tokenId/ocr-extract
 * Runs OCR on the last uploaded file attached to this token and
 * populates draft prescription fields for doctor review.
 */
router.post('/:tokenId/ocr-extract', protect, adminOnly, async (req, res) => {
  try {
    const token = await Token.findById(req.params.tokenId);
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (req.user.role === 'doctor' && token.doctor.toString() !== req.user._id.toString()) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this token',
      });
    }

    const filePath = token.prescription?.uploadedFilePath;
    if (!filePath || !fs.existsSync(filePath)) {
      return res.status(400).json({
        success: false,
        message: 'No uploaded file found for this token. Upload a file first.',
      });
    }

    const mimeType = filePath.endsWith('.pdf') ? 'application/pdf' : 'image/jpeg';
    const extraction = await extractTextFromFile(filePath, mimeType);

    // Populate draft fields without overwriting existing confirmed data
    if (!token.prescription) token.prescription = {};
    token.prescription.ocrExtractedText = extraction.rawText;
    token.prescription.ocrConfidence = extraction.confidence;
    token.prescription.ocrStatus = 'draft_extracted';
    token.prescription.source = 'ocr_extracted';
    token.prescription.needsReview = true;

    // Pre-fill from parsed fields if extraction returned them
    const pf = extraction.parsedFields || {};
    if (pf.diagnosis) token.prescription.diagnosis = pf.diagnosis;
    if (pf.medicines) token.prescription.medicines = pf.medicines;
    if (pf.notes) token.prescription.notes = pf.notes;

    await token.save();

    res.json({
      success: true,
      message: 'OCR extraction complete. Please review and confirm the fields.',
      ocrStatus: 'draft_extracted',
      ocrConfidence: extraction.confidence,
      extractedText: extraction.rawText,
      draftFields: {
        diagnosis: token.prescription.diagnosis,
        medicines: token.prescription.medicines,
        notes: token.prescription.notes,
      },
    });
  } catch (err) {
    console.error('OCR extract error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

/**
 * PATCH /api/prescriptions/:tokenId/confirm
 * Doctor reviews OCR-extracted fields, optionally edits them, and confirms.
 *
 * Body: { diagnosis?, medicines?, notes? }
 */
router.patch('/:tokenId/confirm', protect, adminOnly, async (req, res) => {
  try {
    const token = await Token.findById(req.params.tokenId).populate('patient', 'name');
    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (req.user.role === 'doctor' && token.doctor.toString() !== req.user._id.toString()) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this token',
      });
    }

    const { diagnosis, medicines, notes } = req.body || {};

    if (!token.prescription) token.prescription = {};

    // Merge confirmed fields (only update non-empty values to preserve existing)
    if (diagnosis != null) token.prescription.diagnosis = diagnosis;
    if (medicines != null) token.prescription.medicines = medicines;
    if (notes != null) token.prescription.notes = notes;

    token.prescription.ocrStatus = 'doctor_confirmed';
    token.prescription.source = 'ocr_confirmed';
    token.prescription.needsReview = false;
    token.prescription.prescribedAt = new Date();
    token.prescription.prescribedBy = req.user._id;

    await token.save();

    res.json({
      success: true,
      message: `Prescription confirmed for ${token.patient?.name || 'patient'}`,
      tokenId: token._id,
      prescription: {
        diagnosis: token.prescription.diagnosis,
        medicines: token.prescription.medicines,
        notes: token.prescription.notes,
        source: token.prescription.source,
        ocrStatus: token.prescription.ocrStatus,
      },
    });
  } catch (err) {
    console.error('Prescription confirm error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.get('/:tokenId', protect, async (req, res) => {
  try {
    const token = await Token.findById(req.params.tokenId)
      .populate('patient', 'name')
      .populate('doctor', 'name specialty');

    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (!canAccessPrescription(token, req.user, { write: false })) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this prescription',
      });
    }

    const prescription = await ensurePrescriptionForToken(token);
    const view = buildPrescriptionView(token, prescription);

    res.json({
      success: true,
      message: view.hasPrescription ? 'Prescription loaded' : 'No prescription recorded yet',
      ...view,
      patientName: token.patient?.name || 'Patient',
      doctorName: token.doctor?.name || 'Doctor',
      doctorSpecialty: token.doctor?.specialty || '',
      reportedSymptoms: token.symptoms || '',
      visitType: token.visitType || 'new',
      completedAt: token.completedAt || null,
      createdAt: token.createdAt,
      canEdit: ['admin', 'superuser'].includes(req.user.role) ||
        (req.user.role === 'doctor' && String(token.doctor?._id || token.doctor) === String(req.user._id)),
    });
  } catch (err) {
    console.error('Prescription fetch error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

router.put('/:tokenId', protect, adminOnly, async (req, res) => {
  try {
    const token = await Token.findById(req.params.tokenId)
      .populate('patient', 'name')
      .populate('doctor', 'name specialty');

    if (!token) {
      return res.status(404).json({ success: false, message: 'Token not found' });
    }

    if (!canAccessPrescription(token, req.user, { write: true })) {
      return res.status(403).json({
        success: false,
        message: 'Access denied to this prescription',
      });
    }

    if (['cancelled', 'no_show'].includes(token.status)) {
      return res.status(400).json({
        success: false,
        message: 'Cannot edit prescriptions for cancelled or no-show visits.',
      });
    }

    const prescription = await savePrescriptionForToken(token, req.user, req.body || {});

    if (prescription.status === 'finalized' && ['waiting', 'called', 'arrived'].includes(token.status)) {
      const now = new Date();
      const startedAt = token.consultationStartedAt || token.calledAt || token.joinedAt || token.createdAt;
      const consultationDuration = diffMinutes(startedAt, now);

      token.status = 'completed';
      token.completedAt = token.completedAt || now;

      if (!Number.isFinite(token.actualWaitMinutes)) {
        token.actualWaitMinutes = diffMinutes(
          token.joinedAt || token.createdAt,
          token.calledAt || now
        );
      }

      if (!Number.isFinite(token.actualConsultMinutes)) {
        token.actualConsultMinutes = consultationDuration;
      }

      await token.save();

      const queueDate = getTokenQueueDate(token);
      const queue = await Queue.findOne({ doctor: token.doctor, date: queueDate });
      if (queue) {
        if (queue.currentToken === token.tokenNumber) {
          queue.currentToken = 0;
        }
        if (Number.isFinite(consultationDuration) && consultationDuration > 0 && consultationDuration < 60) {
          queue.updateAvgConsultation(consultationDuration);
        }
        await queue.save();
        await recomputeWaitingQueue(token.doctor, queue.avgConsultationMinutes, queueDate);
      }
    }

    const view = buildPrescriptionView(token, prescription);

    res.json({
      success: true,
      message: prescription.status === 'finalized'
        ? `Prescription finalized for ${token.patient?.name || 'patient'}`
        : `Prescription draft saved for ${token.patient?.name || 'patient'}`,
      ...view,
      patientName: token.patient?.name || 'Patient',
      doctorName: token.doctor?.name || 'Doctor',
      doctorSpecialty: token.doctor?.specialty || '',
      reportedSymptoms: token.symptoms || '',
      visitType: token.visitType || 'new',
      completedAt: token.completedAt || null,
      createdAt: token.createdAt,
      canEdit: true,
    });
  } catch (err) {
    console.error('Prescription save error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─── Multer error handler ──────────────────────────────────────
// eslint-disable-next-line no-unused-vars
router.use((err, _req, res, _next) => {
  if (err instanceof multer.MulterError || err.message) {
    return res.status(400).json({ success: false, message: err.message });
  }
  res.status(500).json({ success: false, message: 'Server error' });
});

module.exports = router;

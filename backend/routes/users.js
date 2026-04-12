/**
 * /api/users — User management route.
 *
 * Access rules:
 *   superuser: full access — can create / list / delete any role including admin
 *   admin:     can create / list / delete doctor, nurse, patient
 *              cannot manage other admins or superusers
 *
 * Doctors and nurses CANNOT self-register; they must be created here.
 */

const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const rateLimit = require('express-rate-limit');
const { protect, superuserOrAdmin } = require('../middleware/authMiddleware');
const User = require('../models/User');
const {
  buildDuplicateFieldMessage,
  isValidEmail,
  isValidPhone,
  normalizeEmail,
  normalizePhone,
} = require('../utils/userValidation');

// All routes require login + admin or superuser role
router.use(protect, superuserOrAdmin);

// Roles that admins (non-superuser) are allowed to manage
const ADMIN_MANAGEABLE_ROLES = ['doctor', 'nurse', 'patient'];
const ALL_ROLES = ['patient', 'doctor', 'nurse', 'admin', 'superuser'];

// Helper: escape special regex characters in user-supplied strings
const escapeRegex = (str) => String(str).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

// Rate limit for write operations to prevent abuse
const writeLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 30,
  message: { success: false, message: 'Too many requests, please try again later.' },
});

// Rate limit for read operations
const readLimiter = rateLimit({
  windowMs: 60 * 1000,
  max: 120,
  message: { success: false, message: 'Too many requests, please try again later.' },
});

// ─────────────────────────────────────────────────────────────
// GET /api/users?role=&search=&page=&limit=
// List users — superuser sees all; admin sees non-superuser users
// ─────────────────────────────────────────────────────────────
router.get('/', readLimiter, async (req, res) => {
  try {
    const page  = Math.max(1, parseInt(req.query.page  || '1'));
    const limit = Math.min(100, Math.max(1, parseInt(req.query.limit || '50')));
    const skip  = (page - 1) * limit;

    // Validate and sanitize the role query parameter
    const rawRole = req.query.role;
    let roleFilter = null;
    if (rawRole) {
      const normalized = String(rawRole).toLowerCase();
      if (!ALL_ROLES.includes(normalized)) {
        return res.status(400).json({ success: false, message: `Invalid role filter: ${rawRole}` });
      }
      roleFilter = normalized;
    }

    // Sanitize search to prevent regex injection
    const rawSearch = req.query.search;
    const safeSearch = rawSearch ? escapeRegex(rawSearch).slice(0, 200) : null;

    // Build the base filter
    const filter = {};

    // Admins cannot see superuser accounts
    if (req.user.role !== 'superuser') {
      filter.role = { $in: ADMIN_MANAGEABLE_ROLES };
    } else if (roleFilter) {
      filter.role = roleFilter;
    }

    // If admin provides a specific role filter, ensure it is in the allowed list
    if (req.user.role !== 'superuser' && roleFilter) {
      if (!ADMIN_MANAGEABLE_ROLES.includes(roleFilter)) {
        return res.status(403).json({ success: false, message: 'You cannot list users with that role.' });
      }
      filter.role = roleFilter;
    }

    if (safeSearch) {
      filter.$or = [
        { name:    { $regex: safeSearch, $options: 'i' } },
        { email:   { $regex: safeSearch, $options: 'i' } },
        { staffId: { $regex: safeSearch, $options: 'i' } },
      ];
    }

    const [users, total] = await Promise.all([
      User.find(filter)
        .select('-password')
        .sort({ role: 1, name: 1 })
        .skip(skip)
        .limit(limit)
        .lean(),
      User.countDocuments(filter),
    ]);

    res.json({
      success: true,
      users: users.map(formatUser),
      total,
      page,
      pages: Math.ceil(total / limit),
    });
  } catch (err) {
    console.error('List users error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/users
// Create a new user (staff or patient)
// ─────────────────────────────────────────────────────────────
router.post('/', writeLimiter, async (req, res) => {
  try {
    const { name, email, password, phone, age, role, specialty } = req.body;
    const normalizedEmail = normalizeEmail(email);
    const normalizedPhone = normalizePhone(phone);

    if (!name || !normalizedEmail || !password || !normalizedPhone || !age || !role) {
      return res.status(400).json({ success: false, message: 'name, email, password, phone, age, and role are required.' });
    }

    const normalizedRole = String(role).toLowerCase();

    // Validate role and enforce caller permission
    const allRoles = ['patient', 'doctor', 'nurse', 'admin', 'superuser'];
    if (!allRoles.includes(normalizedRole)) {
      return res.status(400).json({ success: false, message: `Invalid role: ${role}` });
    }

    if (req.user.role !== 'superuser' && !ADMIN_MANAGEABLE_ROLES.includes(normalizedRole)) {
      return res.status(403).json({
        success: false,
        message: 'Admins can only create doctor, nurse, or patient accounts.',
      });
    }

    const parsedAge = parseInt(age);
    if (isNaN(parsedAge) || parsedAge < 1 || parsedAge > 120) {
      return res.status(400).json({ success: false, message: 'Age must be between 1 and 120.' });
    }

    if (String(password).length < 6) {
      return res.status(400).json({ success: false, message: 'Password must be at least 6 characters.' });
    }

    if (!isValidEmail(normalizedEmail)) {
      return res.status(400).json({ success: false, message: 'Email must be in a valid format like name@example.com' });
    }

    if (!isValidPhone(normalizedPhone)) {
      return res.status(400).json({ success: false, message: 'Phone number must be exactly 10 digits.' });
    }

    const existing = await User.findOne({ email: normalizedEmail });
    if (existing) {
      return res.status(409).json({ success: false, message: 'Email already registered.' });
    }

    const userData = {
      name:     String(name).trim(),
      email:    normalizedEmail,
      password: String(password),
      phone:    normalizedPhone,
      age:      parsedAge,
      role:     normalizedRole,
    };

    if (normalizedRole === 'doctor' && specialty) {
      userData.specialty = String(specialty).trim();
    }

    const user = await User.create(userData);

    res.status(201).json({
      success: true,
      message: `${normalizedRole.charAt(0).toUpperCase() + normalizedRole.slice(1)} account created successfully.`,
      user: formatUser(user.toObject()),
    });
  } catch (err) {
    console.error('Create user error:', err);
    if (err.name === 'ValidationError') {
      const messages = Object.values(err.errors).map(e => e.message);
      return res.status(400).json({ success: false, message: messages.join(', ') });
    }
    if (err.code === 11000) {
      return res.status(409).json({ success: false, message: buildDuplicateFieldMessage(err) });
    }
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─────────────────────────────────────────────────────────────
// DELETE /api/users/:id
// Delete a user — role-restricted
// ─────────────────────────────────────────────────────────────
router.delete('/:id', writeLimiter, async (req, res) => {
  try {
    const { id } = req.params;

    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ success: false, message: 'Invalid user ID.' });
    }

    // Prevent self-deletion
    if (String(req.user._id) === String(id)) {
      return res.status(400).json({ success: false, message: 'You cannot delete your own account.' });
    }

    const target = await User.findById(id).select('-password');
    if (!target) {
      return res.status(404).json({ success: false, message: 'User not found.' });
    }

    // Admins can only delete manageable roles
    if (req.user.role !== 'superuser' && !ADMIN_MANAGEABLE_ROLES.includes(target.role)) {
      return res.status(403).json({
        success: false,
        message: 'Admins can only delete doctor, nurse, or patient accounts.',
      });
    }

    // Superusers cannot delete other superusers unless they are the only one
    if (req.user.role === 'superuser' && target.role === 'superuser') {
      const count = await User.countDocuments({ role: 'superuser' });
      if (count <= 1) {
        return res.status(400).json({ success: false, message: 'Cannot delete the last superuser account.' });
      }
    }

    await User.findByIdAndDelete(id);

    res.json({
      success: true,
      message: `${target.name} (${target.role}) deleted successfully.`,
      deletedUserId: id,
    });
  } catch (err) {
    console.error('Delete user error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// ─── Helper: safe user fields for API responses ────────────
function formatUser(user) {
  return {
    id:        user._id,
    name:      user.name,
    email:     user.email,
    phone:     user.phone,
    age:       user.age,
    role:      user.role,
    staffId:   user.staffId || null,
    specialty: user.specialty || '',
    priorityScore: user.priorityScore,
    createdAt: user.createdAt,
  };
}

module.exports = router;

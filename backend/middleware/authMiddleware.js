const jwt = require('jsonwebtoken');
const User = require('../models/User');

/**
 * protect — Middleware to verify JWT token.
 * Attaches req.user to the request on success.
 *
 * Usage: router.get('/protected', protect, handler)
 */
const protect = async (req, res, next) => {
  let token;

  if (req.headers.authorization &&
      req.headers.authorization.startsWith('Bearer ')) {
    token = req.headers.authorization.split(' ')[1];
  }

  if (!token) {
    return res.status(401).json({
      success: false,
      message: 'Not authorized. Please log in.'
    });
  }

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = await User.findById(decoded.id).select('-password');

    if (!req.user) {
      return res.status(401).json({
        success: false,
        message: 'User no longer exists.'
      });
    }

    next();
  } catch (err) {
    return res.status(401).json({
      success: false,
      message: 'Invalid or expired token. Please log in again.'
    });
  }
};

/**
 * adminOnly — Restrict route to admin/doctor/superuser role only.
 * Must come AFTER protect middleware.
 *
 * Usage: router.post('/admin/next', protect, adminOnly, handler)
 */
const adminOnly = (req, res, next) => {
  if (req.user && ['admin', 'doctor', 'superuser'].includes(req.user.role)) {
    return next();
  }
  return res.status(403).json({
    success: false,
    message: 'Access denied. Admins or doctors only.'
  });
};

/**
 * staffOnly — Restrict route to admin, doctor, or nurse.
 * Used for nurse-triage and emergency intake endpoints.
 * Must come AFTER protect middleware.
 */
const staffOnly = (req, res, next) => {
  if (req.user && ['admin', 'doctor', 'nurse', 'superuser'].includes(req.user.role)) {
    return next();
  }
  return res.status(403).json({
    success: false,
    message: 'Access denied. Clinical staff only.'
  });
};

/**
 * superuserOrAdmin — Restrict route to superuser and admin.
 * Used for user-management endpoints.
 * Must come AFTER protect middleware.
 */
const superuserOrAdmin = (req, res, next) => {
  if (req.user && ['admin', 'superuser'].includes(req.user.role)) {
    return next();
  }
  return res.status(403).json({
    success: false,
    message: 'Access denied. Admin or superuser only.'
  });
};

/**
 * superuserOnly — Restrict route to superuser only.
 * Must come AFTER protect middleware.
 */
const superuserOnly = (req, res, next) => {
  if (req.user && req.user.role === 'superuser') {
    return next();
  }
  return res.status(403).json({
    success: false,
    message: 'Access denied. Superuser only.'
  });
};

module.exports = { protect, adminOnly, staffOnly, superuserOrAdmin, superuserOnly };

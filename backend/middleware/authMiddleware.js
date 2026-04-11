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
 * adminOnly — Restrict route to admin/doctor role only.
 * Must come AFTER protect middleware.
 *
 * Usage: router.post('/admin/next', protect, adminOnly, handler)
 */
const adminOnly = (req, res, next) => {
  if (req.user && ['admin', 'doctor'].includes(req.user.role)) {
    return next();
  }
  return res.status(403).json({
    success: false,
    message: 'Access denied. Admins or doctors only.'
  });
};

module.exports = { protect, adminOnly };

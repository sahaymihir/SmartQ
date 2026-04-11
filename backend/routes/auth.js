const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const { protect } = require('../middleware/authMiddleware');

// ─── Helper: generate JWT ──────────────────────────────────
const generateToken = (userId) => {
  return jwt.sign(
    { id: userId },
    process.env.JWT_SECRET,
    { expiresIn: '7d' }
  );
};

// ─────────────────────────────────────────────────────────────
// POST /api/auth/register
// Body: { name, email, password, phone, age, role }
// ─────────────────────────────────────────────────────────────
router.post('/register', async (req, res) => {
  try {
    const { name, email, password, phone, age, role } = req.body;

    // Basic validation
    if (!name || !email || !password || !phone || !age) {
      return res.status(400).json({
        success: false,
        message: 'All fields are required'
      });
    }

    // Check if email already exists
    const existingUser = await User.findOne({ email });
    if (existingUser) {
      return res.status(409).json({
        success: false,
        message: 'Email already registered. Please login.'
      });
    }

    // Create user (password hashed in pre-save hook)
    const user = await User.create({
      name,
      email,
      password,
      phone,
      age: parseInt(age),
      role: role || 'patient'
    });

    const token = generateToken(user._id);

    res.status(201).json({
      success: true,
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        role: user.role,
        age: user.age,
        priorityScore: user.priorityScore
      }
    });

  } catch (err) {
    console.error('Register error:', err);

    // Mongoose validation error
    if (err.name === 'ValidationError') {
      const messages = Object.values(err.errors).map(e => e.message);
      return res.status(400).json({ success: false, message: messages.join(', ') });
    }

    res.status(500).json({ success: false, message: 'Server error during registration' });
  }
});

// ─────────────────────────────────────────────────────────────
// POST /api/auth/login
// Body: { email, password }
// ─────────────────────────────────────────────────────────────
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;

    if (!email || !password) {
      return res.status(400).json({
        success: false,
        message: 'Email and password are required'
      });
    }

    // Find user — explicitly select password (hidden by default)
    const user = await User.findOne({ email }).select('+password');
    if (!user) {
      return res.status(401).json({
        success: false,
        message: 'Invalid email or password'
      });
    }

    // Compare password
    const isMatch = await user.comparePassword(password);
    if (!isMatch) {
      return res.status(401).json({
        success: false,
        message: 'Invalid email or password'
      });
    }

    const token = generateToken(user._id);

    res.json({
      success: true,
      token,
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        role: user.role,
        age: user.age,
        priorityScore: user.priorityScore
      }
    });

  } catch (err) {
    console.error('Login error:', err);
    res.status(500).json({ success: false, message: 'Server error during login' });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/auth/doctors — List doctors for queue selection
// ─────────────────────────────────────────────────────────────
router.get('/doctors', async (req, res) => {
  try {
    const doctors = await User.find({ role: 'doctor' })
      .select('_id name')
      .sort({ name: 1 })
      .lean();

    res.json({
      success: true,
      doctors: doctors.map((doctor) => ({
        id: doctor._id,
        name: doctor.name,
      })),
    });
  } catch (err) {
    console.error('Doctor list error:', err);
    res.status(500).json({ success: false, message: 'Server error while loading doctors' });
  }
});

// ─────────────────────────────────────────────────────────────
// GET /api/auth/me — Get current user (protected)
// ─────────────────────────────────────────────────────────────
router.get('/me', protect, async (req, res) => {
  res.json({
    success: true,
    user: req.user
  });
});

module.exports = router;

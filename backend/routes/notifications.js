/**
 * notifications.js — Express router
 *
 * Endpoints:
 *   POST   /api/notifications/register-device   — store FCM device token for the current user
 *   DELETE /api/notifications/register-device   — remove FCM device token on logout
 */

const express = require('express');
const router = express.Router();
const { protect } = require('../middleware/authMiddleware');
const User = require('../models/User');

// POST /api/notifications/register-device
// Body: { fcmToken: string }
router.post('/register-device', protect, async (req, res) => {
  const { fcmToken } = req.body;

  if (!fcmToken || typeof fcmToken !== 'string' || fcmToken.length < 10) {
    return res.status(400).json({
      success: false,
      message: 'A valid fcmToken string is required',
    });
  }

  try {
    await User.updateOne({ _id: req.user._id }, { fcmToken });
    res.json({ success: true, message: 'Device registered for push notifications' });
  } catch (err) {
    console.error('register-device error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

// DELETE /api/notifications/register-device
// Unregisters the device token (call on logout or permission revoked)
router.delete('/register-device', protect, async (req, res) => {
  try {
    await User.updateOne({ _id: req.user._id }, { fcmToken: null });
    res.json({ success: true, message: 'Device unregistered from push notifications' });
  } catch (err) {
    console.error('unregister-device error:', err);
    res.status(500).json({ success: false, message: 'Server error' });
  }
});

module.exports = router;

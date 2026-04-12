/**
 * notificationService.js
 *
 * Sends push notifications to patients via Firebase Cloud Messaging (FCM).
 * Uses the legacy FCM HTTP API via axios so that no extra SDK dependency is needed —
 * only axios (already a backend dependency) and the FCM_SERVER_KEY environment variable.
 *
 * Set FCM_SERVER_KEY in your .env file to enable push delivery.
 * If the key is absent, the service logs a warning and silently skips the push.
 */

const axios = require('axios');
const User = require('../models/User');

const FCM_API_URL = 'https://fcm.googleapis.com/fcm/send';
const FCM_SERVER_KEY = process.env.FCM_SERVER_KEY || null;

// ─── Core dispatch ─────────────────────────────────────────────
const sendFcmPush = async (fcmToken, notification, data = {}) => {
  if (!FCM_SERVER_KEY) {
    console.warn('⚠️  FCM_SERVER_KEY not set — skipping push notification');
    return;
  }

  if (!fcmToken) {
    return;
  }

  const payload = {
    to: fcmToken,
    priority: 'high',
    notification: {
      title: notification.title,
      body: notification.body,
      sound: 'default',
    },
    data: {
      ...data,
      click_action: 'FLUTTER_NOTIFICATION_CLICK',
    },
  };

  try {
    await axios.post(FCM_API_URL, payload, {
      headers: {
        Authorization: `key=${FCM_SERVER_KEY}`,
        'Content-Type': 'application/json',
      },
      timeout: 10000,
    });
  } catch (err) {
    // Push failures are non-fatal — the patient app polls as fallback.
    console.error('FCM push failed:', err.message);
  }
};

// ─── Notify helpers ─────────────────────────────────────────────

/**
 * Notify a patient that it is their turn in the queue.
 */
const notifyTokenCalled = async (patientId, tokenNumber) => {
  try {
    const user = await User.findById(patientId).select('fcmToken').lean();
    if (!user?.fcmToken) return;

    await sendFcmPush(
      user.fcmToken,
      {
        title: '🔔 It\'s Your Turn!',
        body: `Token #${tokenNumber} — Please proceed to the consultation room now.`,
      },
      { event: 'token_called', tokenNumber: String(tokenNumber) }
    );
  } catch (err) {
    console.error('notifyTokenCalled error:', err.message);
  }
};

/**
 * Notify a patient that their ETA has been updated.
 */
const notifyEtaUpdated = async (patientId, tokenNumber, etaMinutes) => {
  try {
    const user = await User.findById(patientId).select('fcmToken').lean();
    if (!user?.fcmToken) return;

    const etaText = etaMinutes === 0 ? 'Next up!' : `~${etaMinutes} min`;
    await sendFcmPush(
      user.fcmToken,
      {
        title: '⏱ Queue Update',
        body: `Token #${tokenNumber}: estimated wait ${etaText}`,
      },
      { event: 'eta_updated', tokenNumber: String(tokenNumber), etaMinutes: String(etaMinutes) }
    );
  } catch (err) {
    console.error('notifyEtaUpdated error:', err.message);
  }
};

/**
 * Notify a patient that the queue has been paused.
 */
const notifyQueuePaused = async (patientId, doctorName, paused) => {
  try {
    const user = await User.findById(patientId).select('fcmToken').lean();
    if (!user?.fcmToken) return;

    const state = paused ? 'paused' : 'resumed';
    await sendFcmPush(
      user.fcmToken,
      {
        title: paused ? '⏸ Queue Paused' : '▶ Queue Resumed',
        body: `The queue for ${doctorName || 'your doctor'} has been ${state}.`,
      },
      { event: paused ? 'queue_paused' : 'queue_resumed' }
    );
  } catch (err) {
    console.error('notifyQueuePaused error:', err.message);
  }
};

module.exports = {
  notifyTokenCalled,
  notifyEtaUpdated,
  notifyQueuePaused,
};

const User = require('../models/User');
const { Token } = require('../models/Queue');
const { ACTIVE_TOKEN_STATUSES, buildDayQuery, getTodayDateString } = require('../utils/queueHelpers');
const { notifyEtaUpdated, notifyQueuePaused, notifyTokenCalled } = require('./notificationService');

const notifyCalledToken = async (token) => {
  if (!token) {
    return;
  }
  await notifyTokenCalled(token.patient?._id || token.patient, token.tokenNumber);
};

const notifyWaitingDoctorEtaChanges = async (
  doctorId,
  dateString = getTodayDateString()
) => {
  const tokens = await Token.find({
    doctor: doctorId,
    status: 'waiting_doctor',
    createdAt: buildDayQuery(dateString),
  })
    .select('patient tokenNumber etaMinutes')
    .lean();

  await Promise.all(tokens.map((token) =>
    notifyEtaUpdated(token.patient, token.tokenNumber, Number(token.etaMinutes || 0))
  ));
};

const notifyDoctorQueuePausedState = async (
  doctorId,
  paused,
  dateString = getTodayDateString()
) => {
  const doctor = await User.findById(doctorId).select('name').lean();
  const tokens = await Token.find({
    doctor: doctorId,
    status: { $in: ACTIVE_TOKEN_STATUSES },
    createdAt: buildDayQuery(dateString),
  })
    .select('patient')
    .lean();

  const uniquePatientIds = [...new Set(tokens.map((token) => String(token.patient || '')).filter(Boolean))];
  await Promise.all(uniquePatientIds.map((patientId) =>
    notifyQueuePaused(patientId, doctor?.name || 'your doctor', paused)
  ));
};

module.exports = {
  notifyCalledToken,
  notifyDoctorQueuePausedState,
  notifyWaitingDoctorEtaChanges,
};

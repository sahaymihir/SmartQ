const { Queue, Token } = require('../models/Queue');

const ACTIVE_TOKEN_STATUSES = ['waiting', 'called', 'arrived'];
const TRIAGE_PRIORITY_SCORES = {
  high: 10,
  medium: 7,
  normal: 5,
};

const getTodayDateString = () => new Date().toISOString().split('T')[0];

const getDayBounds = (dateString = getTodayDateString()) => {
  const [year, month, day] = dateString.split('-').map(Number);
  const start = new Date(Date.UTC(year, month - 1, day, 0, 0, 0, 0));
  const end = new Date(Date.UTC(year, month - 1, day + 1, 0, 0, 0, 0));
  return { start, end };
};

const buildDayQuery = (dateString = getTodayDateString()) => {
  const { start, end } = getDayBounds(dateString);
  return { $gte: start, $lt: end };
};

const computeETA = (position, avgConsultationMinutes) => {
  const safePosition = Math.max(1, Number(position) || 1);
  const safeAverage = Math.max(0, Number(avgConsultationMinutes) || 0);
  return Math.round(Math.max(0, (safePosition - 1) * safeAverage));
};

const getPriorityLabel = (priorityScore) => {
  if ((priorityScore || 0) >= TRIAGE_PRIORITY_SCORES.high) return 'high';
  if ((priorityScore || 0) >= TRIAGE_PRIORITY_SCORES.medium) return 'medium';
  return 'normal';
};

const getTodayQueue = async (doctorId, dateString = getTodayDateString()) => {
  let queue = await Queue.findOne({ doctor: doctorId, date: dateString });
  if (!queue) {
    queue = await Queue.create({ doctor: doctorId, date: dateString });
  }
  return queue;
};

const loadWaitingTokens = async (doctorId, dateString = getTodayDateString()) => {
  return Token.find({
    doctor: doctorId,
    status: 'waiting',
    createdAt: buildDayQuery(dateString),
  }).sort({ position: 1, joinedAt: 1, createdAt: 1 });
};

const persistWaitingOrder = async (tokens, avgConsultationMinutes) => {
  if (!tokens.length) {
    return [];
  }

  const operations = [];
  tokens.forEach((token, index) => {
    const newPosition = index + 1;
    const liveEta = computeETA(newPosition, avgConsultationMinutes);
    const needsUpdate =
      token.position !== newPosition || token.etaMinutes !== liveEta;

    if (needsUpdate) {
      operations.push({
        updateOne: {
          filter: { _id: token._id },
          update: {
            $set: {
              position: newPosition,
              etaMinutes: liveEta,
            },
          },
        },
      });
      token.position = newPosition;
      token.etaMinutes = liveEta;
    }
  });

  if (operations.length > 0) {
    await Token.bulkWrite(operations);
  }

  return tokens;
};

const recomputeWaitingQueue = async (doctorId, avgConsultationMinutes, dateString = getTodayDateString()) => {
  const tokens = await loadWaitingTokens(doctorId, dateString);
  return persistWaitingOrder(tokens, avgConsultationMinutes);
};

const promoteTokenByPriority = async (
  doctorId,
  tokenId,
  avgConsultationMinutes,
  dateString = getTodayDateString()
) => {
  const tokens = await loadWaitingTokens(doctorId, dateString);
  const currentIndex = tokens.findIndex((token) => token._id.toString() === tokenId.toString());

  if (currentIndex === -1) {
    return [];
  }

  const movingToken = tokens[currentIndex];
  const targetIndex = tokens.findIndex(
    (token) =>
      token._id.toString() !== tokenId.toString() &&
      (token.priorityScore || 0) < (movingToken.priorityScore || 0)
  );

  if (targetIndex !== -1 && targetIndex < currentIndex) {
    tokens.splice(currentIndex, 1);
    tokens.splice(targetIndex, 0, movingToken);
  }

  return persistWaitingOrder(tokens, avgConsultationMinutes);
};

const diffMinutes = (startAt, endAt) => {
  if (!startAt || !endAt) {
    return null;
  }
  const delta = new Date(endAt).getTime() - new Date(startAt).getTime();
  if (!Number.isFinite(delta) || delta < 0) {
    return null;
  }
  return Math.round(delta / 60000);
};

module.exports = {
  ACTIVE_TOKEN_STATUSES,
  TRIAGE_PRIORITY_SCORES,
  buildDayQuery,
  computeETA,
  diffMinutes,
  getDayBounds,
  getPriorityLabel,
  getTodayDateString,
  getTodayQueue,
  persistWaitingOrder,
  promoteTokenByPriority,
  recomputeWaitingQueue,
};

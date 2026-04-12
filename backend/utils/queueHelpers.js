const { Queue, Token } = require('../models/Queue');

const ACTIVE_TOKEN_STATUSES = ['waiting', 'called', 'arrived'];
const NORMAL_ROUTING_LANE = 'normal';
const IMMEDIATE_REVIEW_LANE = 'immediate_review';
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

const getRoutingLane = (token = {}) =>
  token.routingLane === IMMEDIATE_REVIEW_LANE ? IMMEDIATE_REVIEW_LANE : NORMAL_ROUTING_LANE;

const isImmediateReviewToken = (token = {}) =>
  getRoutingLane(token) === IMMEDIATE_REVIEW_LANE || Boolean(token.requiresImmediateReview);

const getOperationalPriorityClass = (token = {}) => {
  if (isImmediateReviewToken(token)) {
    return 1;
  }

  const triageClass = Number(token.triagePriorityClass);
  if (Number.isInteger(triageClass) && triageClass >= 1 && triageClass <= 5) {
    return triageClass;
  }

  return 5;
};

const getOperationalPriorityScore = (token = {}) => {
  const value = Number(token.priorityFinalScore ?? token.priorityScore ?? 0);
  return Number.isFinite(value) ? value : 0;
};

const getTokenTime = (value) => {
  const millis = new Date(value || 0).getTime();
  return Number.isFinite(millis) ? millis : 0;
};

const compareTokensByUrgency = (left = {}, right = {}) => {
  const classDiff = getOperationalPriorityClass(left) - getOperationalPriorityClass(right);
  if (classDiff !== 0) {
    return classDiff;
  }

  const scoreDiff = getOperationalPriorityScore(right) - getOperationalPriorityScore(left);
  if (scoreDiff !== 0) {
    return scoreDiff;
  }

  const joinedDiff = getTokenTime(left.joinedAt || left.createdAt) - getTokenTime(right.joinedAt || right.createdAt);
  if (joinedDiff !== 0) {
    return joinedDiff;
  }

  const createdDiff = getTokenTime(left.createdAt) - getTokenTime(right.createdAt);
  if (createdDiff !== 0) {
    return createdDiff;
  }

  return (Number(left.tokenNumber) || 0) - (Number(right.tokenNumber) || 0);
};

const sortWaitingTokensForCall = (tokens = []) => {
  const waitingTokens = [...tokens];

  waitingTokens.sort((left, right) => {
    const laneDiff =
      (isImmediateReviewToken(left) ? 0 : 1) -
      (isImmediateReviewToken(right) ? 0 : 1);
    if (laneDiff !== 0) {
      return laneDiff;
    }

    if (isImmediateReviewToken(left) && isImmediateReviewToken(right)) {
      return compareTokensByUrgency(left, right);
    }

    const positionDiff = (Number(left.position) || 0) - (Number(right.position) || 0);
    if (positionDiff !== 0) {
      return positionDiff;
    }

    return compareTokensByUrgency(left, right);
  });

  return waitingTokens;
};

const sortActiveQueueTokens = (tokens = []) => {
  const activeTokens = [...tokens];
  const statusRank = {
    called: 0,
    arrived: 1,
    waiting: 2,
  };

  activeTokens.sort((left, right) => {
    const leftStatus = statusRank[left.status] ?? 9;
    const rightStatus = statusRank[right.status] ?? 9;
    if (leftStatus !== rightStatus) {
      return leftStatus - rightStatus;
    }

    if (left.status === 'waiting' && right.status === 'waiting') {
      const laneDiff =
        (isImmediateReviewToken(left) ? 0 : 1) -
        (isImmediateReviewToken(right) ? 0 : 1);
      if (laneDiff !== 0) {
        return laneDiff;
      }

      if (isImmediateReviewToken(left) && isImmediateReviewToken(right)) {
        return compareTokensByUrgency(left, right);
      }

      const positionDiff = (Number(left.position) || 0) - (Number(right.position) || 0);
      if (positionDiff !== 0) {
        return positionDiff;
      }

      return compareTokensByUrgency(left, right);
    }

    const calledDiff = getTokenTime(left.calledAt || left.updatedAt || left.joinedAt)
      - getTokenTime(right.calledAt || right.updatedAt || right.joinedAt);
    if (calledDiff !== 0) {
      return calledDiff;
    }

    return compareTokensByUrgency(left, right);
  });

  return activeTokens;
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
    routingLane: { $ne: IMMEDIATE_REVIEW_LANE },
    createdAt: buildDayQuery(dateString),
  }).sort({ position: 1, joinedAt: 1, createdAt: 1 });
};

const getNextWaitingToken = async (doctorId, dateString = getTodayDateString()) => {
  const tokens = await Token.find({
    doctor: doctorId,
    status: 'waiting',
    createdAt: buildDayQuery(dateString),
  });

  return sortWaitingTokensForCall(tokens)[0] || null;
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
      compareTokensByUrgency(movingToken, token) < 0
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
  IMMEDIATE_REVIEW_LANE,
  NORMAL_ROUTING_LANE,
  TRIAGE_PRIORITY_SCORES,
  buildDayQuery,
  compareTokensByUrgency,
  computeETA,
  diffMinutes,
  getDayBounds,
  getNextWaitingToken,
  getPriorityLabel,
  getRoutingLane,
  getTodayDateString,
  getTodayQueue,
  getOperationalPriorityClass,
  isImmediateReviewToken,
  persistWaitingOrder,
  promoteTokenByPriority,
  recomputeWaitingQueue,
  sortActiveQueueTokens,
  sortWaitingTokensForCall,
};

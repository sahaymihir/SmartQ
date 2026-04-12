/**
 * In-memory store for symptom prediction history.
 * Shared between doctors.js (writes) and admin.js (reads).
 * Will be replaced by a Mongoose model in future iterations.
 */
const predictionHistory = [];

module.exports = predictionHistory;

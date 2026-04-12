const express = require('express');
const mongoose = require('mongoose');
const cors = require('cors');
require('dotenv').config();

const requiredEnvVars = ['MONGO_URI', 'JWT_SECRET'];
const missingEnvVars = requiredEnvVars.filter((key) => !process.env[key]);

if (missingEnvVars.length > 0) {
  console.error(`❌ Missing required environment variables: ${missingEnvVars.join(', ')}`);
  process.exit(1);
}

const authRoutes = require('./routes/auth');
const queueRoutes = require('./routes/queue');
const adminRoutes = require('./routes/admin');
const doctorsRoutes = require('./routes/doctors');

const app = express();

// ─── Middleware ───────────────────────────────────────────
// Render sits behind a reverse proxy; trust it so rate limiting keys by client IP correctly.
app.set('trust proxy', 1);
app.use(cors());
app.use(express.json());

// ─── Health Check ─────────────────────────────────────────
app.get('/', (req, res) => {
  res.json({ status: 'SmartQ API is running 🚀' });
});

// ─── Routes ───────────────────────────────────────────────
app.use('/api/auth', authRoutes);
app.use('/api/queue', queueRoutes);   // protected — Week 2
app.use('/api/admin', adminRoutes);   // protected — Week 3
app.use('/api/doctors', doctorsRoutes); // doctors list + symptom predict

// ─── Global Error Handler ─────────────────────────────────
app.use((err, req, res, next) => {
  console.error(err.stack);
  res.status(500).json({ success: false, message: 'Internal server error' });
});

// ─── Connect to MongoDB Atlas & Start Server ──────────────
const PORT = process.env.PORT || 5000;

mongoose.connect(process.env.MONGO_URI)
  .then(() => {
    console.log('✅ Connected to MongoDB Atlas');
    app.listen(PORT, () => {
      console.log(`🚀 Server running on http://localhost:${PORT}`);
    });
  })
  .catch((err) => {
    console.error('❌ MongoDB connection failed:', err.message);
    process.exit(1);
  });
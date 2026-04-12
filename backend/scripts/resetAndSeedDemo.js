const path = require('path');
const mongoose = require('mongoose');
require('dotenv').config({ path: path.join(__dirname, '..', '.env') });

const { resetAndSeedDemoData } = require('../services/demoSeedService');

const run = async () => {
  if (!process.env.MONGO_URI) {
    throw new Error('MONGO_URI is required to run the demo seed script.');
  }

  await mongoose.connect(process.env.MONGO_URI);

  try {
    const result = await resetAndSeedDemoData();
    console.log(result.message);
    console.log(JSON.stringify(result.counts, null, 2));
    console.log(JSON.stringify(result.credentials, null, 2));
  } finally {
    await mongoose.disconnect();
  }
};

run()
  .then(() => {
    console.log('SmartQ demo seed finished successfully.');
  })
  .catch((error) => {
    console.error('SmartQ demo seed failed:', error);
    process.exit(1);
  });

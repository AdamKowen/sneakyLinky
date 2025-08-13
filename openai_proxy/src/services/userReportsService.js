const repo = require('../repositories/userReportsRepository');
const validator = require('validator');

function validateUUID(uuid) {
  if (!validator.isUUID(uuid + '')) {
    const e = new Error('Invalid UUID');
    e.statusCode = 400;
    e.publicMessage = 'Invalid UUID';
    throw e;
  }
}

function validatePositiveInt(n) {
  const num = parseInt(n, 10);
  if (isNaN(num) || num <= 0) {
    const e = new Error('Invalid number');
    e.statusCode = 400;
    e.publicMessage = 'Invalid number';
    throw e;
  }
  return num;
}

function validateReportInput({ url, systemClassification, userClassification, userReason }) {
  if (!url || !validator.isURL(url)) {
    const e = new Error('Invalid URL');
    e.statusCode = 400;
    e.publicMessage = 'Invalid URL';
    throw e;
  }
  if (![0, 1].includes(Number(systemClassification))) {
    const e = new Error('Invalid systemClassification');
    e.statusCode = 400;
    e.publicMessage = 'Invalid systemClassification';
    throw e;
  }
  if (![0, 1].includes(Number(userClassification))) {
    const e = new Error('Invalid userClassification');
    e.statusCode = 400;
    e.publicMessage = 'Invalid userClassification';
    throw e;
  }
  if (!userReason || typeof userReason !== 'string' || userReason.trim().length === 0) {
    const e = new Error('Invalid userReason');
    e.statusCode = 400;
    e.publicMessage = 'Invalid userReason';
    throw e;
  }
}

// 1. Get stats
async function getReportStatsService() {
  return repo.getReportStats();
}

// 2. Get top N unreviewed
async function getTopUnreviewedService(n) {
  const num = validatePositiveInt(n);
  return repo.getTopUnreviewed(num);
}

// 3. Find by UUID
async function findByUUIDService(uuid) {
  validateUUID(uuid);
  const report = await repo.findByUUID(uuid);
  if (!report) {
    const e = new Error('Report not found');
    e.statusCode = 404;
    e.publicMessage = 'Report not found';
    throw e;
  }
  return report;
}

// 4. Delete by UUIDs
async function deleteByUUIDsService(uuids) {
  if (!Array.isArray(uuids) || uuids.length === 0) {
    const e = new Error('UUIDs array required');
    e.statusCode = 400;
    e.publicMessage = 'UUIDs array required';
    throw e;
  }
  uuids.forEach(validateUUID);
  const deleted = await repo.deleteByUUIDs(uuids);
  return { deleted };
}

// 5. Create or increment report
async function createOrIncrementReportService(input) {
  validateReportInput(input);
  return repo.createOrIncrementReport(input);
}

// 6. Update adminDecision
async function updateAdminDecisionService(uuid, adminDecision) {
  validateUUID(uuid);
  if (![0, 1].includes(Number(adminDecision))) {
    const e = new Error('Invalid adminDecision');
    e.statusCode = 400;
    e.publicMessage = 'Invalid adminDecision';
    throw e;
  }
  const report = await repo.updateAdminDecision(uuid, Number(adminDecision));
  if (!report) {
    const e = new Error('Report not found');
    e.statusCode = 404;
    e.publicMessage = 'Report not found';
    throw e;
  }
  return report;
}

module.exports = {
  getReportStatsService,
  getTopUnreviewedService,
  findByUUIDService,
  deleteByUUIDsService,
  createOrIncrementReportService,
  updateAdminDecisionService
};

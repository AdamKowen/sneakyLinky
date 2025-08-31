const UserReports = require('../models/UserReports');
const { Op } = require('sequelize');

// 1. Get stats: total, unclassified, classified
async function getReportStats() {
  const total = await UserReports.count();
  const unclassified = await UserReports.count({ where: { adminDecision: null } });
  const classified = total - unclassified;
  return { total, unclassified, classified };
}

// 2. Get top N unreviewed by reportCount DESC
async function getTopUnreviewed(n) {
  return UserReports.findAll({
    where: { adminDecision: null },
    order: [['reportCount', 'DESC']],
    limit: n
  });
}

// 3. Find by UUID
async function findByUUID(uuid) {
  return UserReports.findByPk(uuid);
}

// 4. Delete by UUIDs (array)
async function deleteByUUIDs(uuids) {
  return UserReports.destroy({ where: { id: uuids } });
}

// 5. Upsert or increment reportCount
async function createOrIncrementReport({ url, systemClassification, userClassification, userReason }) {
  // Try to find existing report for this URL and same system/user classification
  const [report, created] = await UserReports.findOrCreate({
    where: {
      url,
      systemClassification,
      userClassification,
      userReason
    },
    defaults: {
      reportCount: 1
    }
  });
  if (!created) {
    // If found, increment reportCount
    await report.increment('reportCount');
    await report.reload();
  }
  return report;
}

// 6. Update adminDecision by UUID
async function updateAdminDecision(uuid, adminDecision) {
  const report = await UserReports.findByPk(uuid);
  if (!report) return null;
  report.adminDecision = adminDecision;
  await report.save();
  return report;
}

module.exports = {
  getReportStats,
  getTopUnreviewed,
  findByUUID,
  deleteByUUIDs,
  createOrIncrementReport,
  updateAdminDecision
};

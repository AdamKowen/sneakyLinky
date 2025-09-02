const express = require('express');
const router = express.Router();
const service = require('../../../services/userReportsService');
const verifyToken = require('../../../middleware/auth/verifyToken');
const logger = require('../../../utils/logger');


// POST /v1/userReports
// Body: { url, systemClassification, userClassification, userReason }
router.post('/', async (req, res) => {
  logger.info(`[userReports] POST / (body=${JSON.stringify(req.body)})`);
  try {
    const report = await service.createOrIncrementReportService(req.body);
    logger.debug(`[userReports] created/incremented report: ${JSON.stringify(report)}`);
    res.status(201).json(report);
  } catch (err) {
    logger.error(`[userReports] POST / error: ${err.message}`);
    
    // If the service provides details (for validation errors), include them
    const response = { error: err.publicMessage || 'internal error' };
    if (err.details) {
      response.details = err.details;
    }
    
    res.status(err.statusCode || 500).json(response);
  }
});



// GET /v1/userReports/stats
router.get('/stats', verifyToken , async (req, res) => {
  logger.info('[userReports] GET /stats');
  try {
    const stats = await service.getReportStatsService();
    logger.debug(`[userReports] stats: ${JSON.stringify(stats)}`);
    res.json(stats);
  } catch (err) {
    logger.error(`[userReports] GET /stats error: ${err.message}`);
    res.status(err.statusCode || 500).json({ error: err.publicMessage || 'internal error' });
  }
});

// GET /v1/userReports/top/:n
router.get('/top/:n', verifyToken , async (req, res) => {
  logger.info(`[userReports] GET /top/${req.params.n}`);
  try {
    const reports = await service.getTopUnreviewedService(req.params.n);
    logger.debug(`[userReports] top reports: ${JSON.stringify(reports)}`);
    res.json(reports);
  } catch (err) {
    logger.error(`[userReports] GET /top/${req.params.n} error: ${err.message}`);
    res.status(err.statusCode || 500).json({ error: err.publicMessage || 'internal error' });
  }
});

// GET /v1/userReports/:uuid
router.get('/:uuid', verifyToken , async (req, res) => {
  logger.info(`[userReports] GET /${req.params.uuid}`);
  try {
    const report = await service.findByUUIDService(req.params.uuid);
    logger.debug(`[userReports] found report: ${JSON.stringify(report)}`);
    res.json(report);
  } catch (err) {
    logger.error(`[userReports] GET /${req.params.uuid} error: ${err.message}`);
    res.status(err.statusCode || 500).json({ error: err.publicMessage || 'internal error' });
  }
});

// DELETE /v1/userReports
// Body: { uuids: [ ... ] }
router.delete('/', verifyToken , async (req, res) => {
  logger.info(`[userReports] DELETE / (uuids=${JSON.stringify(req.body.uuids)})`);
  try {
    const { uuids } = req.body;
    const result = await service.deleteByUUIDsService(uuids);
    logger.debug(`[userReports] deleted: ${JSON.stringify(result)}`);
    res.json(result);
  } catch (err) {
    logger.error(`[userReports] DELETE / error: ${err.message}`);
    res.status(err.statusCode || 500).json({ error: err.publicMessage || 'internal error' });
  }
});

// PUT /v1/userReports/:uuid/adminDecision
// Body: { adminDecision: 0 | 1 }
router.put('/:uuid/adminDecision', verifyToken, async (req, res) => {
  logger.info(`[userReports] PUT /${req.params.uuid}/adminDecision (body=${JSON.stringify(req.body)})`);
  try {
    const { adminDecision } = req.body;
    const report = await service.updateAdminDecisionService(req.params.uuid, adminDecision);
    logger.debug(`[userReports] updated adminDecision: ${JSON.stringify(report)}`);
    res.json(report);
  } catch (err) {
    logger.error(`[userReports] PUT /${req.params.uuid}/adminDecision error: ${err.message}`);
    res.status(err.statusCode || 500).json({ error: err.publicMessage || 'internal error' });
  }
});

module.exports = router;

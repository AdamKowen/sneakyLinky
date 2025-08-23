const express = require('express');
const router  = express.Router();
const verifyToken = require('../../../middleware/auth/verifyToken');
const logger  = require('../../../utils/logger');
const validator = require('validator');
const { 
  updateDomainSuspicion,
   deleteDomainFromDB,
    checkDomainDB,
    addDomainToDB,
    getFirstN,
    getDomainsCount,
    searchBySubstring,
    getDomainStats
} = require('../../../services/domainService');


// ── apply authentication middleware to all routes in this file ───
// This will ensure that all domain routes require a valid JWT token
if (process.env.NODE_ENV !== "test")
  router.use('/domain', verifyToken);


/**
 * GET /v1/domain/limit/:n
 * Returns the first n rows from the Domain table (by createdAt ASC).
 * Optional query params: ?sortBy=<column>&dir=ASC|DESC
 */
router.get('/domain/limit/:n', async (req, res) => {
  try {
    logger.info(`GET /domain/limit/${req.params.n} called`);
    const { sortBy, dir } = req.query;
    const rows = await getFirstN(req.params.n, sortBy, dir);
    return res.json(rows);
  } catch (err) {
    const status = err.statusCode || 500;
    const msg = err.publicMessage || 'internal error';
    logger.error(`GET /domain/limit/${req.params.n} failed: ${err.message}`);
    return res.status(status).json({ error: msg });
  }
});

/**
 * PATCH /v1/domain/:name
 * Updates the 'suspicious' flag for a given domain.
 * Body: { suspicious: 0 | 1 | true | false }
 */
router.patch('/domain/:name', async (req, res) => {
  const rawName = (req.params.name || '').trim().toLowerCase();
  logger.debug(`[PATCH] Received request to update suspicious for domain: ${rawName}`);
  if (!rawName || !validator.isFQDN(rawName)) {
    logger.debug(`[PATCH] Invalid domain name: ${rawName}`);
    return res.status(400).json({ error: 'Invalid domain name' });
  }

  const { suspicious } = req.body || {};
  logger.debug(`[PATCH] suspicious value received: ${suspicious}`);
  let flag;
  if (suspicious === 0 || suspicious === 1) flag = suspicious;
  else if (typeof suspicious === 'boolean') flag = suspicious ? 1 : 0;
  else {
    logger.debug(`[PATCH] Invalid suspicious value: ${suspicious}`);
    return res.status(400).json({ error: "'suspicious' must be 0/1 or boolean" });
  }

  try {
    logger.debug(`[PATCH] Calling updateDomainSuspicion for ${rawName} with flag ${flag}`);
    const ok = await updateDomainSuspicion(rawName, flag); // true/false
    if (!ok) {
      logger.debug(`[PATCH] Domain not found: ${rawName}`);
      return res.status(404).json({ error: 'Domain not found' });
    }

    logger.debug(`[PATCH] Fetching updated row for domain: ${rawName}`);
    const updatedRow = await checkDomainDB(rawName);

    logger.info(`[DOMAIN] Updated suspicious for ${rawName} -> ${flag}`);
    return res.json(updatedRow);
  } catch (err) {
    logger.error(`PATCH /domain/${rawName} failed: ${err.message}`);
    return res.status(500).json({ error: 'internal error' });
  }
});


/**
 * DELETE /v1/domain/:name
 * Deletes a domain row by name.
 */
router.delete('/domain/:name', async (req, res) => {
  const rawName = (req.params.name || '').trim().toLowerCase();
  if (!rawName || !validator.isFQDN(rawName)) {
    return res.status(400).json({ error: 'Invalid domain name' });
  }
  try {
    const ok = await deleteDomainFromDB(rawName); // true if deleted, false if not found
    if (!ok) {
      return res.status(404).json({ error: 'Domain not found' });
    }
    logger.info(`[DOMAIN] Deleted domain ${rawName}`);
    return res.json({ message: 'Domain deleted', name: rawName });
  } catch (err) {
    logger.error(`DELETE /domain/${rawName} failed: ${err.message}`);
    return res.status(500).json({ error: 'internal error' });
  }
});

/**
 * GET /v1/domain/count
 * Returns the total number of rows in the Domain table.
 */
router.get('/domain/stats', async (_req, res) => {
  try {
    const stats = await getDomainStats();
    return res.json(stats); // { total, suspicious, safe }
  } catch (err) {
    logger.error(`GET /domain/stats failed: ${err.message}`);
    return res.status(500).json({ error: 'internal error' });
  }
});


/**
 * GET /v1/domain/:name
 * Returns a list of domains whose name contains the provided substring (case-insensitive).
 */
router.get('/domain/:name', async (req, res) => {
  try {
    const results = await searchBySubstring(req.params.name);
    return res.json(results);
  } catch (err) {
    logger.error(`GET /domain/${req.params.name} failed: ${err.message}`);
    return res
      .status(err.statusCode || 500)
      .json({ error: err.publicMessage || 'internal error' });
  }
});

/**
 * POST /v1/domain
 * Creates a new domain row.
 * Body: { name: string, suspicious: 0|1|true|false|'0'|'1'|'true'|'false' }
 */
router.post('/domain', async (req, res) => {
  const rawName = String(req.body?.name || '').trim().toLowerCase();
  const s = req.body?.suspicious;

  if (!rawName || !validator.isFQDN(rawName)) {
    return res.status(400).json({ error: 'Invalid domain name' });
  }

  let suspiciousValue;
  if (s === 0 || s === 1) suspiciousValue = s;
  else if (typeof s === 'boolean') suspiciousValue = s ? 1 : 0;
  else if (s === '0' || s === '1') suspiciousValue = Number(s);
  else if (typeof s === 'string' && (s.toLowerCase() === 'true' || s.toLowerCase() === 'false')) {
    suspiciousValue = s.toLowerCase() === 'true' ? 1 : 0;
  } else {
    return res.status(400).json({ error: "'suspicious' must be 0/1 or boolean" });
  }

  try {
    const created = await addDomainToDB(rawName, suspiciousValue);
    logger.info(`[DOMAIN] Created ${rawName} (suspicious=${suspiciousValue})`);
    return res.status(201).json(created);
  } catch (err) {
    if (err.name && err.name.includes('Unique')) {
      return res.status(409).json({ error: 'Domain already exists' });
    }
    logger.error(`POST /domain failed for ${rawName}: ${err.message}`);
    return res.status(500).json({ error: 'internal error' });
  }
});

module.exports = router;

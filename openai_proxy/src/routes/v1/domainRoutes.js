const express = require('express');
const router  = express.Router();
const Domain  = require('../../models/Domain');
const logger  = require('../../utils/logger');

//TODO: REMOVE THIS FILE
// This file is a placeholder for future domain-related routes.
// Currently, it only contains a few example endpoints.
// For now, it serves as a starting point for domain management features.

/**
 * GET /v1/domain/first
 * Returns the first row ever inserted into the Domain table.
 * (ordered by createdAt ASC, since 'name' is the primary key)
 */
router.get('/domain/first', async (req, res) => {
  try {
    // Earliest row by insertion time
    const first = await Domain.findOne({ order: [['createdAt', 'ASC']] });

    if (!first) {
      return res.status(404).json({ error: 'no rows in Domain' });
    }
    res.json(first);                // returns full JSON (name, suspicious, timestamps)
  } catch (err) {
    logger.error(`GET /domain/first failed: ${err.message}`);
    res.status(500).json({ error: 'internal error' });
  }
});

/**
 * GET /v1/domain/all
 * Returns all rows from the Domain table.
 */
router.get('/domain/all', async (req, res) => {
  try {
    const all = await Domain.findAll({ order: [['createdAt', 'ASC']] });
    res.json(all);
  } catch (err) {
    logger.error(`GET /domain/all failed: ${err.message}`);
    res.status(500).json({ error: 'internal error' });
  }
});

/**
 * GET /v1/domain/limit/:n
 * Returns the first n rows from the Domain table (by createdAt ASC).
 */
router.get('/domain/limit/:n', async (req, res) => {
  const n = parseInt(req.params.n, 10);
  if (isNaN(n) || n < 1) {
    return res.status(400).json({ error: 'Invalid limit number' });
  }
  try {
    const limited = await Domain.findAll({ order: [['createdAt', 'ASC']], limit: n });
    res.json(limited);
  } catch (err) {
    logger.error(`GET /domain/limit/${n} failed: ${err.message}`);
    res.status(500).json({ error: 'internal error' });
  }
});

module.exports = router;

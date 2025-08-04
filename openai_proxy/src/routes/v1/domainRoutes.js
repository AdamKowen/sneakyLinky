const express = require('express');
const router  = express.Router();
const Domain  = require('../../models/Domain');
const logger  = require('../../utils/logger');

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

module.exports = router;

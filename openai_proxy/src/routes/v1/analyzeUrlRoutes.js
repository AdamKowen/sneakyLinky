const express = require('express');
const { checkDomainDB, addDomainToDB, deleteDomainFromDB } = require('../../services/domainService');
const { extractDomain } = require('../../utils/parseDomain');
const { externalUrlAnalyzer } = require('../../middleware/externalDB/gsbClient');
const { analyzeUrl } = require('../../middleware/openai/openaiClient');
const logger = require('../../utils/logger');
const validator = require('validator');

const router = express.Router();

/**
 * Checks external threat intelligence via VirusTotal.
 *
 * @param {string} url - The full URL to check.
 * @returns {Promise<null|object>} - Result object if malicious, otherwise null.
 */
async function checkExternalDB(url) {
  try {
    const resultFlag = await externalUrlAnalyzer(url);
    if (resultFlag === 1) {
      return {
        phishing_score: 1,
        suspicion_reasons: ['This link has been identified as malicious by external threat intelligence'],
        recommended_actions: [],
        source: 'external-db'
      };
    }
    return null;
  } catch (err) {
    logger.error(`External DB check failed: ${err.message}`);
    return null;
  }
}

/**
 * POST /analyze-url
 * Analyzes a given URL for phishing risks.
 */
router.post('/analyze-url', async (req, res) => {
  const start = Date.now();
  const { url } = req.body || {};

  logger.info(`[IN] POST /analyze-url | URL length: ${url?.length || 0}`);

  if (!url) {
    logger.warn('Missing URL in request body');
    return res.status(400).json({ error: 'Missing URL' });
  }

  try {
    const domain = extractDomain(url);
    if (!domain) {
      logger.warn(`Invalid URL format: ${url}`);
      return res.status(400).json({ error: 'Invalid URL' });
    }

    if (!validator.isFQDN(domain)) {
      logger.warn(`Invalid domain format: ${domain}`);
      return res.status(400).json({ error: 'Invalid domain format' });
    }

    // Local database check
    const dbResult = await checkDomainDB(domain);
    const oneYearMs = 365 * 24 * 60 * 60 * 1000;
    const recentlyUpdated =
      dbResult?.updatedAt &&
      Date.now() - new Date(dbResult.updatedAt).getTime() < oneYearMs;

    if (dbResult && recentlyUpdated) {
      const duration = Date.now() - start;
      logger.info(`[OUT] /analyze-url | source: local-db | domain: ${domain} | duration: ${duration}ms`);
      return res.json({
        phishing_score: dbResult.suspicious,
        suspicion_reasons: dbResult.suspicion_reasons || [],
        recommended_actions: dbResult.recommended_actions || [],
        source: 'local-db',
        domain,
      });
    } else if (dbResult) {
      await deleteDomainFromDB(domain);
      logger.info(`[analyze-url] Stale record removed: ${domain}`);
    }

    // External threat intel check
    logger.debug(`[analyze-url] Checking external threat intelligence for domain: ${domain}, url: ${url}`);
    const externalResult = await checkExternalDB(url);
    logger.debug(`[analyze-url] External threat intelligence result for domain: ${domain}:`, externalResult);
    if (externalResult) {
      await addDomainToDB(domain, externalResult.phishing_score > 0.5 ? 1 : 0);
      const duration = Date.now() - start;
      logger.info(`[OUT] /analyze-url | source: external-db | domain: ${domain} | duration: ${duration}ms`);
      return res.json({ ...externalResult, domain });
    }

    // AI-based analysis
    const aiResult = await analyzeUrl(url);

    try {
      await addDomainToDB(domain, aiResult.phishing_score > 0.5 ? 1 : 0);
    } catch (dbErr) {
      logger.error(`Failed saving domain to DB: ${dbErr.message}`);
      if (dbErr.name === 'SequelizeValidationError') {
        return res.status(400).json({ error: dbErr.message });
      }
      throw dbErr;
    }

    const duration = Date.now() - start;
    logger.info(`[OUT] /analyze-url | source: openai | domain: ${domain} | score: ${aiResult.phishing_score} | duration: ${duration}ms`);

    return res.json({ ...aiResult, source: 'openai', domain });

  } catch (err) {
    logger.error(`Error analyzing URL: ${err.message}`);
    return res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;
/**
 * @file analyzeMessageRoutes.js
 * @description Express route for analyzing a full text message (e.g. SMS or email body)
 *              using the OpenAI API to detect phishing content.
 */

const express = require('express');
const router = express.Router();
const { analyzeMessage } = require('../../middleware/openai/openaiClient');
const logger = require('../../utils/logger');

require('dotenv').config();

/**
 * Maximum number of characters allowed for a message, pulled from environment variable.
 * @type {number}
 */
const MESSAGE_MAX_TOKENS = parseInt(process.env.MESSAGE_MAX_TOKENS, 10);

/**
 * @route POST /analyze-message
 * @summary Analyzes a full text message for phishing risks.
 * 
 * @param {Object} req - Express request object
 * @param {string} req.body.message - The full text message to analyze
 * 
 * @param {Object} res - Express response object
 * @returns {Object} - JSON result of the analysis from OpenAI API
 * 
 * @example
 * // Request body:
 * {
 *   "message": "Click here to claim your prize now!"
 * }
 * 
 * // Response:
 * {
 *   "phishing_score": 0.87,
 *   "suspicion_reasons": [...],
 *   "recommended_actions": [...],
 *   "source": "openai"
 * }
 */
router.post('/analyze-message', async (req, res) => {
  const start = Date.now();
  const { message } = req.body;

  logger.info(`[IN] POST /analyze-message | Message length: ${message?.length || 0}`);

  if (!message) {
    logger.warn(`Missing message text`);
    return res.status(400).json({ error: 'Missing message text' });
  }

  if (message.length > MESSAGE_MAX_TOKENS) {
    logger.warn(`Message too long: ${message.length} > ${MESSAGE_MAX_TOKENS}`);
    return res.status(400).json({ error: `Message must be a string up to ${MESSAGE_MAX_TOKENS} characters` });
  }

  try {
    const result = await analyzeMessage(message);
    const durationMs = Date.now() - start;

    logger.info(`[OUT] /analyze-message completed in ${durationMs}ms | Score: ${result.phishing_score}`);

    res.json(result);
  } catch (err) {
    logger.error(`Error analyzing message: ${err.message}`);
    res.status(500).json({ error: 'Internal server error' });
  }
});

module.exports = router;

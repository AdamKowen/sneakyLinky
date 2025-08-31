require('dotenv').config();

const axios = require('axios');
const qs = require('qs');
const logger = require('../../utils/logger');

// -----------------------------------------------------------------------------
// Constants
// -----------------------------------------------------------------------------
const GSB_API_KEY = process.env.GSB_API_KEY;
const MALICIOUS = 1;
const HARMLESS = 0;
const UNDETECTED = -1;

// -----------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------
/**
 * Validates that a URL is a non-empty string and valid according to the URL constructor.
 *
 * @param {string} url - The URL to validate.
 * @returns {boolean} True if the URL is valid, false otherwise.
 */
function isValidUrl(url) {
    try {
        new URL(url);
        return true;
    } catch {
        return false;
    }
}

// -----------------------------------------------------------------------------
// Main Function
// -----------------------------------------------------------------------------




/**
 * Analyzes a URL against Google Safe Browsing and returns a simple flag:
 * MALICIOUS (1), HARMLESS (0), or UNDETECTED (-1‑on‑error).
 *
 * @param {string} url – full http/https URL
 * @returns {Promise<number>} MALICIOUS | HARMLESS | UNDETECTED
 */
async function externalUrlAnalyzer(url) {
  if (!isValidUrl(url)) {
    logger.warn(`[GSB] Invalid URL input: ${url?.substring?.(0, 100)}...`);
    throw new Error(`Invalid URL: ${url}`);
  }

  const endpoint =
    `https://safebrowsing.googleapis.com/v4/threatMatches:find?key=${GSB_API_KEY}`;

  const body = {
    client: {
      clientId:      'sneakyLinky-service',
      clientVersion: '1.0.0'
    },
    threatInfo: {
      threatTypes:      ['MALWARE', 'SOCIAL_ENGINEERING'],  
      platformTypes:    ['ANY_PLATFORM'],
      threatEntryTypes: ['URL'],
      threatEntries:    [{ url }]
    }
  };

  logger.debug(`[GSB] POST ${endpoint}`);
  logger.debug(`[GSB] Payload: ${JSON.stringify(body)}`);

  try {
    const { data, status } = await axios.post(endpoint, body, {
      timeout: 5000,
      headers: { 'Content-Type': 'application/json' }
    });

    logger.debug(`[GSB] Raw response: ${JSON.stringify(data)}`);

    const isMalicious = !!data.matches;

    return isMalicious ? MALICIOUS : HARMLESS;

  } catch (err) {
    logger.error(`[GSB] Request failed: ${err.message}`);
    return UNDETECTED; // keep contract with caller
  }
}


module.exports = {
    externalUrlAnalyzer
};

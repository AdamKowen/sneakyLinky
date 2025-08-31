/**
 * @file openaiClient.js
 * @description Provides phishing analysis using OpenAI's Chat API for both URLs and full messages.
 */

const axios = require('axios');
require('dotenv').config();
const { CYBERSEC_PROMPT } = require('./prompt');
const logger = require('../../utils/logger');

const API_KEY = process.env.OPENAI_API_KEY;
const URL_MODEL = process.env.URL_MODEL;
const MESSAGE_MODEL = process.env.MESSAGE_MODEL;
const MESSAGE_MAX_TOKENS = Number(process.env.MESSAGE_MAX_TOKENS);
const URL_MAX_TOKENS = Number(process.env.URL_MAX_TOKENS);

/**
 * Validates that a message is a non-empty string and within token limits.
 * @param {string} message - The message to validate.
 * @returns {boolean} True if the message is valid, false otherwise.
 */
function isMessageValid(message) {
  return typeof message === 'string' && message.length <= MESSAGE_MAX_TOKENS && message.length > 0;
}

/**
 * Validates that a URL is a non-empty string and within token limits.
 * @param {string} url - The URL to validate.
 * @returns {boolean} True if the URL is valid, false otherwise.
 */
function isURLValid(url) {
  return typeof url === 'string' && url.length <= URL_MAX_TOKENS && url.length > 0;
}

/**
 * Analyzes a URL for phishing risks using OpenAI's Chat Completion API.
 * @async
 * @param {string} url - The URL to analyze.
 * @returns {Promise<Object>} Parsed JSON response from OpenAI describing phishing likelihood and metadata.
 * @throws {Error} If the URL is invalid or the OpenAI request fails.
 */
async function analyzeUrl(url) {
  if (!isURLValid(url)) {
    logger.warn(`Invalid URL input received: ${url?.substring?.(0, 100)}...`);
    throw new Error(`url must be a string up to ${URL_MAX_TOKENS} characters`);
  }

  const prompt = `${CYBERSEC_PROMPT} ${url}`;
  logger.info(`Calling OpenAI to analyze URL (length: ${url.length})`);

  try {
    const response = await axios.post(
      'https://api.openai.com/v1/chat/completions',
      {
        model: URL_MODEL,
        temperature: 0,
        max_tokens: URL_MAX_TOKENS,
        messages: [
          { role: 'system', content: 'You are a helpful assistant.' },
          { role: 'user', content: prompt }
        ]
      },
      {
        headers: {
          Authorization: `Bearer ${API_KEY}`,
          'Content-Type': 'application/json'
        },
        timeout: 20000
      }
    );

    const raw = response.data.choices[0].message.content;
    const parsed = JSON.parse(raw);
    logger.debug(`OpenAI URL result: ${JSON.stringify(parsed)}`);
    return parsed;
  } catch (error) {
    logger.error(`OpenAI URL analysis failed: ${error.message}`);
    throw error;
  }
}

/**
 * Analyzes a full message (e.g., email, SMS) for phishing risks using OpenAI's Chat Completion API.
 * @async
 * @param {string} message - The full message text to analyze.
 * @returns {Promise<Object>} Parsed JSON response from OpenAI describing phishing likelihood and metadata.
 * @throws {Error} If the message is invalid or the OpenAI request fails.
 */
async function analyzeMessage(message) {
  if (!isMessageValid(message)) {
    logger.warn(`Invalid message input received (length: ${message?.length || 0})`);
    throw new Error(`Message must be a string up to ${MESSAGE_MAX_TOKENS} characters`);
  }

  const prompt = `${CYBERSEC_PROMPT} ${message}`;
  logger.info(`Calling OpenAI to analyze message (length: ${message.length})`);

  try {
    const response = await axios.post(
      'https://api.openai.com/v1/chat/completions',
      {
        model: MESSAGE_MODEL,
        temperature: 0,
        max_tokens: MESSAGE_MAX_TOKENS,
        messages: [
          { role: 'system', content: 'You are a helpful assistant.' },
          { role: 'user', content: prompt }
        ]
      },
      {
        headers: {
          Authorization: `Bearer ${API_KEY}`,
          'Content-Type': 'application/json'
        },
        timeout: 20000
      }
    );

    const raw = response.data.choices[0].message.content;
    const parsed = JSON.parse(raw);
    logger.debug(`OpenAI message result: ${JSON.stringify(parsed)}`);
    return parsed;
  } catch (error) {
    logger.error(`OpenAI message analysis failed: ${error.message}`);
    throw error;
  }
}

module.exports = {
  analyzeUrl,
  analyzeMessage
};

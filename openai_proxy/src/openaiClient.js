/**
 * Provides the analyzeUrl function for phishing risk analysis using OpenAI API.
 *
 * @file openaiClient.js
 * @author sneakyLinky
 * @description Handles communication with OpenAI API for phishing detection.
 */

const axios = require('axios');
require('dotenv').config();
const { CYBERSEC_PROMPT, MESSAGE_LENGTH_LIMIT } = require('./config');

const API_KEY = process.env.OPENAI_API_KEY;

/**
 * Validates that the message is a non-empty string up to /config.js-MESSAGE_LENGTH_LIMIT characters.
 * @param {string} message - The message to validate.
 * @returns {boolean} True if valid, false otherwise.
 */
function isMessageValid(message) {
    return typeof message === 'string' && message.length <= MESSAGE_LENGTH_LIMIT && message.length > 0;
}

/**
 * Analyzes a message for phishing risk using OpenAI API.
 * @param {string} message - The message or URL to analyze.
 * @returns {Promise<Object>} The parsed JSON result from OpenAI.
 * @throws {Error} If the message is invalid or the API call fails.
 */
async function analyzeUrl(message) {
    if (!isMessageValid(message)) {
        throw new Error(`Message must be a string up to ${MESSAGE_LENGTH_LIMIT} characters`);
    }
    const prompt = `${CYBERSEC_PROMPT} ${message}`;

    try {
        const response = await axios.post('https://api.openai.com/v1/chat/completions',
            {
                model: 'gpt-4.1-nano',
                temperature: 0,
                max_tokens: 300,
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
        return JSON.parse(raw);
    } catch (error) {
        throw error;
    }
}

module.exports = { analyzeUrl };

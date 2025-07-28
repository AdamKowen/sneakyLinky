/**
 * @file logger.js
 * @description Winston-based logging utility.
 * In development: logs to both console and file (`logs/dev.log`).
 * In production: logs only to stdout, which Cloud Run picks up automatically.
 */

const winston = require('winston');
const path = require('path');
require('dotenv').config();

/**
 * Defines the format of log output.
 * @param {Object} info - The log information object.
 * @param {string} info.level - The log level (e.g., info, error).
 * @param {string} info.message - The log message.
 * @param {string} info.timestamp - Timestamp of the log entry.
 * @returns {string} - Formatted log string.
 */
const logFormat = winston.format.printf(({ level, message, timestamp }) => {
  return `${timestamp} [${level.toUpperCase()}]: ${message}`;
});

/**
 * Configured Winston logger instance.
 * 
 * In development:
 * - Logs to console.
 * - Logs to a file at `logs/dev.log`.
 * 
 * In production:
 * - Logs only to console (stdout), for Cloud Run to capture.
 * 
 * @type {winston.Logger}
 */
const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info', // Default to 'info' if not set
  format: winston.format.combine(
    winston.format.timestamp(),
    logFormat
  ),
  transports: [],
});

// Add transports depending on environment
if (process.env.NODE_ENV !== 'production') {
  logger.add(new winston.transports.Console());
  logger.add(new winston.transports.File({ filename: path.join('logs', 'dev.log') }));
} else {
  logger.add(new winston.transports.Console());
}

module.exports = logger;

/**
 * @file index.js
 * @description Entry point of the Express-based API application.
 *              Initializes middleware, routes, logging, error handling,
 *              and synchronizes the database using Sequelize.
 */

const express = require('express');
const app = express();
const logger = require('./utils/logger');
const { sequelize } = require('./config/db');

// ─────────────────────────────────────────────────────────────────────────────
// Middleware
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Enables automatic parsing of incoming JSON requests.
 */
app.use(express.json());
logger.debug('Express JSON middleware initialized');

// ─────────────────────────────────────────────────────────────────────────────
// Database sync (development only)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Syncs Sequelize models with the database in non-production environments.
 * Uses { force: true } to drop and recreate all tables.
 */
if (process.env.NODE_ENV !== 'production') {
  sequelize.sync({ force: true })
    .then(() => logger.info('Database synchronized with { force: true }'))
    .catch(err => logger.error(`Database sync error: ${err.message}`));
}


// ─────────────────────────────────────────────────────────────────────────────
// Middleware – JSON parsing error handling
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Catches malformed JSON errors and returns 400 Bad Request.
 *
 * @param {Error} err - Error thrown by Express
 * @param {Request} req - Incoming request
 * @param {Response} res - HTTP response
 * @param {Function} next - Express next middleware function
 */
app.use((err, req, res, next) => {
  if (err instanceof SyntaxError && err.status === 400 && 'body' in err) {
    logger.warn('Invalid JSON received');
    return res.status(400).json({ error: 'Invalid JSON' });
  }
  next(err);
});

// ─────────────────────────────────────────────────────────────────────────────
// Routes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * @module routes/v1/analyzeUrlRoutes
 * @description API routes for analyzing URLs
 */
const v1UrlRoutes = require('./routes/v1/analyzeUrlRoutes');

/**
 * @module routes/v1/analyzeMessageRoutes
 * @description API routes for analyzing full messages
 */
const v1MessageRoutes = require('./routes/v1/analyzeMessageRoutes');

// Register routes
app.use('/v1', v1UrlRoutes);
logger.debug('Loaded /v1/analyze-url routes');

app.use('/v1', v1MessageRoutes);
logger.debug('Loaded /v1/analyze-message routes');

// ─────────────────────────────────────────────────────────────────────────────
// Global error handler
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Handles uncaught errors in the request-response lifecycle.
 *
 * @param {Error} err - Thrown error
 * @param {Request} req
 * @param {Response} res
 * @param {Function} next
 */
app.use((err, req, res, next) => {
  logger.error(`Unhandled error: ${err.message}`);
  res.status(500).json({ error: 'Internal server error' });
});

// ─────────────────────────────────────────────────────────────────────────────
// Export app for testing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Exports the Express app instance for integration testing.
 */
module.exports = app;

// ─────────────────────────────────────────────────────────────────────────────
// Server bootstrap (only when run directly)
// ─────────────────────────────────────────────────────────────────────────────

if (require.main === module) {
  const PORT = process.env.PORT || 3000;
  app.listen(PORT, () => {
    logger.info(`Server running on port ${PORT}`);
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Graceful shutdown handlers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Closes the database connection and exits the process on SIGINT (Ctrl+C).
 */
process.on('SIGINT', async () => {
  logger.info('SIGINT received. Shutting down gracefully...');
  try {
    await sequelize.close();
    logger.info('Database connection closed.');
    process.exit(0);
  } catch (err) {
    logger.error(`Error closing database: ${err.message}`);
    process.exit(1);
  }
});

/**
 * Closes the database connection and exits the process on SIGTERM.
 * Useful for environments like Docker or cloud deployments.
 */
process.on('SIGTERM', async () => {
  logger.info('SIGTERM received. Shutting down gracefully...');
  try {
    await sequelize.close();
    logger.info('Database connection closed.');
    process.exit(0);
  } catch (err) {
    logger.error(`Error closing database: ${err.message}`);
    process.exit(1);
  }
});

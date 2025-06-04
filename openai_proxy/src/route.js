/**
 * All Express route definitions for the OpenAI proxy server.
 * Centralizes the route handlers for easier maintenance.
 *
 * @file route.js
 * @author sneakyLinky
 * @param {import('express').Express} app - The Express app instance
 * @param {Function} analyzeUrl - The function to analyze a message for phishing risk
 */
function registerRoutes(app, analyzeUrl) {
  // Analyze message for phishing risk
  app.post('/analyze-url', async (req, res) => {
    const { message } = req.body;
    if (!message) {
      return res.status(400).json({ error: 'Missing message' });
    }
    try {
      const result = await analyzeUrl(message);
      res.json(result);
    } catch (error) {
      res.status(500).json({ error: error.message });
    }
  });

  // Health check endpoint
  app.get('/health', (req, res) => {
    res.status(200).json({ status: 'OK' });
  });
}

module.exports = { registerRoutes };

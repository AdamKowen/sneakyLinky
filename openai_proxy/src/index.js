/**
 * Main entry point for the OpenAI Proxy Server.
 * Provides endpoints for analyzing messages for phishing risk using OpenAI.
 *
 * @file index.js
 * @author sneakyLinky
 * @description Express server for securely proxying OpenAI API requests and analyzing URLs/messages for phishing detection.
 */

require('dotenv').config();
const express = require('express');
const { analyzeUrl } = require('./openaiClient');
const { registerRoutes } = require('./route');
const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());

// Register all routes (analyze-url, health, etc.)
registerRoutes(app, analyzeUrl);

app.listen(PORT, () => {
  console.log(`OpenAI Proxy server running on port ${PORT}`);
});


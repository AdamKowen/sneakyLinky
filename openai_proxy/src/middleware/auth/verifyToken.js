const jwt = require('jsonwebtoken');
const logger = require('../../utils/logger');

module.exports = function verifyToken(req, res, next) {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) return res.status(401).json({ error: 'Missing token' });

  try {
    req.user = jwt.verify(token, process.env.JWT_SECRET); 
    next();
  } catch (err) {
    logger.warn('Invalid token');
    res.status(401).json({ error: 'Invalid token' });
  }
};

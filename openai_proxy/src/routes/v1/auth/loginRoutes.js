const express    = require('express');
const bcrypt     = require('bcrypt');
const jwt        = require('jsonwebtoken');
const validator  = require('validator');              

const adminService = require('../../../services/adminService');
const logger       = require('../../../utils/logger');

const router = express.Router();

/*────────────────────  POST /v1/auth/login  ────────────────────*
 * Body: { "email": "...", "password": "..." }
 * Success: { "token": "eyJhbGci..." }
 */
router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    logger.info(`[LOGIN] Attempt for email: ${email}`);

    /* ---------- Validation ---------- */
    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }
    if (!validator.isEmail(email)) {
      return res.status(400).json({ error: 'Invalid email format' });
    }

    // Root admin credentials from ENV
    const rootEmail = process.env.ADMIN_EMAIL;
    const rootPasswordHash = process.env.ADMIN_PASSWORD_HASH;

    if (email === rootEmail && await bcrypt.compare(password, rootPasswordHash)) {
      const payload = { id: 0, email: rootEmail, role: 'admin' };
      const token = jwt.sign(payload, process.env.JWT_SECRET, {
        expiresIn: process.env.JWT_EXPIRES_IN || '2h',
      });
      logger.info(`[LOGIN] Success for ROOT admin: ${email}`);
      return res.json({ token });
    }

    /* ---------- Authenticate ---------- */
    const admin = await adminService.getAdminByEmail(email);
    if (!admin || !(await bcrypt.compare(password, admin.passwordHash))) {
      logger.warn(`[LOGIN] Failed login for ${email}`);
      return res.status(401).json({ error: 'Bad credentials' });
    }

    /* ---------- Issue JWT ---------- */
    const payload = { id: admin.id, email: admin.email, role: 'admin' };
    const token = jwt.sign(payload, process.env.JWT_SECRET, {
      expiresIn: process.env.JWT_EXPIRES_IN || '2h',
    });

    logger.info(`[LOGIN] Success for email: ${email}`);
    res.json({ token });

  } catch (err) {
    logger.error(`[LOGIN] Server error: ${err.message}`);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;

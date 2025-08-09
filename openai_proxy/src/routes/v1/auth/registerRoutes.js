const express    = require('express');
const validator  = require('validator');           
const adminService = require('../../../services/adminService');
const logger       = require('../../../utils/logger');
const authenticateJWT = require('../../../middleware/auth/verifyToken');

const router = express.Router();

/**
 * Middlewares for /register route.
 */
const guards = [authenticateJWT];

/*───────────────────────────────  POST /v1/auth/register  ───────────────────────────────*/
router.post('/register', ...guards, async (req, res) => {
  try {
    const { email, password } = req.body;

    /* ---------- Validation ---------- */
    if (!email || !password) {
      return res.status(400).json({ error: 'Email and password are required' });
    }
    if (!validator.isEmail(email)) {                          
      return res.status(400).json({ error: 'Invalid email format' });
    }
    if (password.length < 8) {
      return res.status(400).json({ error: 'Password must be at least 8 characters' });
    }

    /* ---------- Uniqueness ---------- */
    if (await adminService.getAdminByEmail(email)) {
      return res.status(409).json({ error: 'Email already registered' });
    }

    /* ---------- Create admin ---------- */
    const newAdmin = await adminService.createAdmin(email, password);
    logger.info(`[REGISTER] New admin ${email} (id ${newAdmin.id})`);
    res.status(201).json({ message: 'Admin created', id: newAdmin.id });

  } catch (err) {
    logger.error(`[REGISTER] Error: ${err.message}`);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;

/**
 * @file adminRepository.js
 * @description Data-access layer for the **admins** table.
 *              All functions return plain JavaScript objects (or booleans),
 *              keeping upper layers detached from Sequelize internals.
 */

const bcrypt = require('bcrypt');
const Admin  = require('../models/Admin');

const SALT_ROUNDS = Number(process.env.BCRYPT_SALT_ROUNDS || 12);

/* ────────────────────────────────────────────────────────────────────────── */
/*  CRUD helpers                                                             */
/* ────────────────────────────────────────────────────────────────────────── */

/**
 * Creates a new administrator record.
 *
 * @async
 * @param {Object}  param0
 * @param {string}  param0.email          – E-mail (unique, lower-cased).
 * @param {string}  param0.password       – Plain-text password to hash.
 * @returns {Promise<Object>}             Plain object of the created admin.
 * @returns {Promise<{ id: number, email: string, password: string, createdAt: string, updatedAt: string }>} 
 *                                        The created admin object, including the hashed password field.
 */
async function create({ email, password }) {
  const lowerCasedEmail = email.toLowerCase();
  const passwordHash = await bcrypt.hash(password, SALT_ROUNDS);
  const admin = await Admin.create({ email: lowerCasedEmail, passwordHash });
  return admin.toJSON();
}

/**
 * Finds an admin by primary key.
 *
 * @async
 * @param   {number} id                   – Admin ID.
 * @returns {Promise<Object|null>}        Plain object or `null` if not found.
 */
async function findById(id) {
  const admin = await Admin.findByPk(id);
  return admin ? admin.toJSON() : null;
}

/**
 * Finds an admin by e-mail address.
 *
 * @async
 * @param   {string} email                – E-mail (case-insensitive).
 * @returns {Promise<Object|null>}        Plain object or `null` if not found.
 */
async function findByEmail(email) {
  const admin = await Admin.findOne({ where: { email } });
  return admin ? admin.toJSON() : null;
}

/**
 * Updates an admin’s password.
 *
 * @async
 * @param  {number} id                    – Admin ID.
 * @param  {string} newPlainPassword      – New plain-text password.
 * @returns {Promise<boolean>}            `true` if exactly one row updated.
 */
async function updatePassword(id, newPlainPassword) {
  const passwordHash = await bcrypt.hash(newPlainPassword, SALT_ROUNDS);
  const [rows] = await Admin.update({ passwordHash }, { where: { id } });
  return rows === 1;
}

/**
 * Deletes an admin record.
 *
 * @async
 * @param   {number} id                   – Admin ID.
 * @returns {Promise<boolean>}            `true` if exactly one row deleted.
 */
async function remove(id) {
  const rows = await Admin.destroy({ where: { id } });
  return rows === 1;
}

module.exports = {
  create,
  findById,
  findByEmail,
  updatePassword,
  remove,
};

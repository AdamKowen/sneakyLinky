
const adminRepo = require('../repositories/adminRepository');
const validator = require('validator');

/**
 * Creates a new admin after basic input validation.
 * @param {string} email - Admin email address
 * @param {string} password - Plain text password
 * @returns {Promise<Object>} The created admin object
 */
async function createAdmin(email, password) {
  if (typeof email !== 'string' || !validator.isEmail(email)) {
    throw new Error('Invalid email');
  }
  if (typeof password !== 'string' || password.length < 8) {
    throw new Error('Password must be at least 8 characters');
  }
  return adminRepo.create({ email, password });
}

/**
 * Finds an admin by ID.
 * @param {number} id - Admin ID
 * @returns {Promise<Object|null>} The admin object or null if not found
 */
async function getAdminById(id) {
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('Invalid admin id');
  }
  return adminRepo.findById(id);
}

/**
 * Finds an admin by email address.
 * @param {string} email - Admin email address
 * @returns {Promise<Object|null>} The admin object or null if not found
 */
async function getAdminByEmail(email) {
  if (typeof email !== 'string' || !validator.isEmail(email)) {
    throw new Error('Invalid email');
  }
  return adminRepo.findByEmail(email.toLowerCase());
}

/**
 * Updates an admin's password.
 * @param {number} id - Admin ID
 * @param {string} newPassword - New plain text password
 * @returns {Promise<boolean>} True if the password was updated
 */
async function updateAdminPassword(id, newPassword) {
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('Invalid admin id');
  }
  if (typeof newPassword !== 'string' || newPassword.length < 8) {
    throw new Error('Password must be at least 8 characters');
  }
  return adminRepo.updatePassword(id, newPassword);
}

/**
 * Deletes an admin by ID.
 * @param {number} id - Admin ID
 * @returns {Promise<boolean>} True if the admin was deleted
 */
async function deleteAdmin(id) {
  if (!Number.isInteger(id) || id <= 0) {
    throw new Error('Invalid admin id');
  }
  return adminRepo.remove(id);
}

module.exports = {
  createAdmin,
  getAdminById,
  getAdminByEmail,
  updateAdminPassword,
  deleteAdmin,
};

const Domain = require('../models/Domain');
const logger = require('../utils/logger');

/**
 * Finds a domain record by its name (case-insensitive).
 *
 * This function trims and lowercases the input name,
 * and searches for an exact match in the database.
 *
 * @param {string} name - The domain name to search for (e.g., "example.com").
 * @returns {Promise<Domain|null>} A Promise that resolves to the Domain object if found, or null if not.
 */
async function findByName(name) {
  try {
    const result = await Domain.findOne({ where: { name: name.trim().toLowerCase() } });
    return result;
  } catch (err) {
    throw err;
  }
}


/**
 * Adds a new domain to the database.
 *
 * @param {string} name - The domain name (e.g., "example.com").
 * @param {boolean} [suspicious=1] - Whether the domain is suspicious.
 * @returns {Promise<Domain>} - The created Domain record.
 */
async function addDomain(name, suspicious = 1) {
  try {
    const created = await Domain.create({ name: name.trim().toLowerCase(), suspicious });
    return created;
  } catch (err) {
    throw err;
  }
}


module.exports = { 
    findByName,
    addDomain,
 };

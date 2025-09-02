const Domain = require('../models/Domain');
const { Op } = require('sequelize');

/**
 * Finds a domain record by its name (case-insensitive).
 * also increments the access count by 1.
 *
 * This function trims and lowercases the input name,
 * and searches for an exact match in the database.
 *
 * @param {string} name - The domain name to search for (e.g., "example.com").
 * @returns {Promise<Domain|null>} A Promise that resolves to the Domain object if found, or null if not.
 */
async function findByName(name) {
  const normalized = name.trim().toLowerCase();

  // Check if the domain exists
  const domain = await Domain.findOne({ where: { name: normalized } });

  if (domain) {
    // Increment the access count
    await Domain.increment('access_count', { by: 1, where: { name: normalized } });
  }
  return domain;   // returns null if not found
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


/**
 * Marks a domain as suspicious / clean.
 *
 * @param {string} name        fully-qualified domain 
 * @param {number} suspicious  0 = clean, 1 = suspicious  
 * @returns {Promise<number>}  number of rows updated (0|1)
 */
async function updateSuspicion(name, suspicious = 1) {
  try {
    const [affectedRows] = await Domain.update(
      { suspicious },
      {
        where: { name: name }, 
        limit: 1                             // safety guard
      }
    );
    return affectedRows;   // 1 - success, 0 - not found
  } catch (err) {
    throw err;
  }
}


/**
 * Deletes a domain record from the DB.
 *
 * @param {string} name  Fully-qualified domain (e.g. "example.com")
 * @returns {Promise<number>}  number of rows deleted (0 = not found, 1 = deleted)
 */
async function deleteDomain(name) {
  try {
    const normalized = name.trim().toLowerCase();
    const deletedRows = await Domain.destroy({
      where: { name: normalized },
      limit: 1          // safety guard
    });
    return deletedRows; // 1 on success, 0 if no such domain
  } catch (err) {
    throw err;
  }
}


async function findFirstNRepo(n, sortBy, dir) {
  return Domain.findAll({ order: [[sortBy, dir]], limit: n });
}


async function countDomains() {
  return Domain.count();
}


async function countSuspiciousDomains() {
  return Domain.count({ where: { suspicious: 1 } });
}


async function searchBySubstringRepo(q) {
  return Domain.findAll({
    where: { name: { [Op.iLike]: `%${q}%` } },
    order: [['createdAt', 'ASC']],
  });
}


async function getLatestHotset(filter, limit) {
  const result = await Domain.findAll({
    attributes: ['name'],
    where: filter,
    order: [['access_count', 'DESC']],
    limit
  });

  return result.map(record => record.name);
}




module.exports = {
    findByName,
    addDomain,
    updateSuspicion,
    deleteDomain,
    findFirstNRepo,
    countDomains,
    countSuspiciousDomains,
    searchBySubstringRepo,
    getLatestHotset
};

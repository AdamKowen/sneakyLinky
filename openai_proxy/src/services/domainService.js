const {
  findByName,
  addDomain,
  updateSuspicion,
  deleteDomain,
  findFirstNRepo,
  countDomains,
  countSuspiciousDomains,
  searchBySubstringRepo
} = require('../repositories/domainRepository');

const logger = require('../utils/logger');

const { rawAttributes } = require('../models/Domain');

async function checkDomainDB(name) {
  logger.debug(`[checkDomainDB] Checking domain: ${name}`);
  if (!name || typeof name !== 'string') {
    logger.warn('[checkDomainDB] Invalid domain name input');
    throw new Error('Domain name is required and must be a string');
  }
  try {
    const domain = await findByName(name);
    if (!domain) {
      logger.debug(`[checkDomainDB] Domain not found: ${name}`);
      return null;
    }
    logger.debug(`[checkDomainDB] Domain found: ${name}`);
    return domain;
  } catch (err) {
    logger.error(`[checkDomainDB] DB error: ${err.message}`);
    throw err;
  }
}

async function addDomainToDB(name, suspicious = 1) {
  logger.debug(`[addDomainToDB] Adding domain: ${name}, suspicious: ${suspicious}`);
  if (!name || typeof name !== 'string') {
    logger.warn('[addDomainToDB] Invalid domain name input');
    throw new Error('Domain name is required and must be a string');
  }
  try {
    const domain = await findByName(name);
    if (domain) {
      logger.debug(`[addDomainToDB] Domain already exists: ${name}`);
      return domain;
    }
    const added = await addDomain(name, suspicious);
    logger.debug(`[addDomainToDB] Domain added: ${name}`);
    return added;
  } catch (err) {
    logger.error(`[addDomainToDB] DB error: ${err.message}`);
    throw err;
  }
}


/**
 * Updates the ‘suspicious’ flag for a given domain.
 *
 * @param {string}  name        domain to update
 * @param {number} [flag]       0 = clean, 1 = suspicious  (default 1)
 * @returns {Promise<boolean>}  true if a row was updated, false if not found
 */
async function updateDomainSuspicion(name, flag = 1) {
  logger.debug(
    `[updateDomainSuspicion] Updating domain: ${name}, suspicious: ${flag}`
  );
  if (!name || typeof name !== 'string') {
    logger.warn('[updateDomainSuspicion] Invalid domain name input');
    throw new Error('Domain name is required and must be a string');
  }
  try {
    const affected = await updateSuspicion(name, flag); // returns 0 | 1
    if (affected === 0) {
      logger.debug(`[updateDomainSuspicion] Domain not found: ${name}`);
      return false;
    }
    logger.debug(`[updateDomainSuspicion] Domain updated: ${name}`);
    return true;
  } catch (err) {
    logger.error(`[updateDomainSuspicion] DB error: ${err.message}`);
    throw err;
  }
}


/**
 * Deletes a domain record from the DB.
 *
 * @param  {string} name  fully-qualified domain
 * @return {Promise<boolean>} true = deleted, false = not found
 */
async function deleteDomainFromDB(name) {
  logger.debug(`[deleteDomainFromDB] Deleting domain: ${name}`);
  if (!name || typeof name !== 'string') {
    logger.warn('[deleteDomainFromDB] Invalid domain name input');
    throw new Error('Domain name is required and must be a string');
  }
  try {
    const deleted = await deleteDomain(name); // returns 0|1
    if (deleted === 0) {
      logger.debug(`[deleteDomainFromDB] Domain not found: ${name}`);
      return false;
    }
    logger.debug(`[deleteDomainFromDB] Domain deleted: ${name}`);
    return true;
  } catch (err) {
    logger.error(`[deleteDomainFromDB] DB error: ${err.message}`);
    throw err;
  }
}


//******************************************************************************//
//-----------------------------------getFirstN-----------------------------------//
//******************************************************************************//

/** * Validates and normalizes the limit parameter.
 *
 * @param {number|string} nLike - The limit value to validate.
 */
function validateLimit(nLike) {
  const n = parseInt(nLike, 10);
  if (isNaN(n) || n <= 0) {
    const e = new Error('Invalid limit number');
    e.statusCode = 400;
    e.publicMessage = 'Invalid limit number';
    throw e;
  }

  return n > 1000 ? 1000 : n;  // cap at 1000
}

function validateSort(sortByLike = 'createdAt', dirLike = 'ASC') {
  const allowedCols = Object.keys(rawAttributes);
  const sortBy = String(sortByLike);
  if (!allowedCols.includes(sortBy)) {
    const e = new Error('Invalid sort column');
    e.statusCode = 400;
    e.publicMessage = 'Invalid sort column';
    throw e;
  }

  const dir = String(dirLike).toUpperCase();
  if (!['ASC', 'DESC'].includes(dir)) {
    const e = new Error('Invalid sort direction');
    e.statusCode = 400;
    e.publicMessage = 'Invalid sort direction (use ASC or DESC)';
    throw e;
  }
  return { sortBy, dir };
}

async function getFirstN(nLike, sortBy, dir) {
  const n = validateLimit(nLike);
  const { sortBy: s, dir: d } = validateSort(sortBy, dir);
  logger.debug(`[getFirstN] limit=${n}, sortBy=${s}, dir=${d}`);
  return findFirstNRepo(n, s, d);
}


async function getDomainsCount() {
  try {
    const total = await countDomains();
    logger.debug(`[getDomainsCount] total=${total}`);
    return total;
  } catch (err) {
    logger.error(`[getDomainsCount] DB error: ${err.message}`);
    throw err;
  }
}


async function searchBySubstring(qLike) {
  const q = String(qLike || '').trim().toLowerCase();
  if (!q) {
    const e = new Error('Invalid query');
    e.statusCode = 400;
    e.publicMessage = 'Invalid query';
    throw e;
  }
  logger.debug(`[searchBySubstring] q="${q}"`);
  return searchBySubstringRepo(q);
}

async function getDomainStats() {
  try {
    const [total, suspicious] = await Promise.all([
      countDomains(),
      countSuspiciousDomains()
    ]);
    const safe = total - suspicious;
    return { total, suspicious, safe };
  } catch (err) {
    logger.error(`[getDomainStats] DB error: ${err.message}`);
    throw err;
  }
}


module.exports = { 
    checkDomainDB,
    addDomainToDB,
    updateDomainSuspicion,
    deleteDomainFromDB,
    getFirstN,
    getDomainsCount,
    searchBySubstring,
    getDomainStats
};

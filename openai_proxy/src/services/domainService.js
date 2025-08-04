const { findByName, addDomain } = require('../repositories/domainRepository');
const logger = require('../utils/logger');

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


module.exports = { 
    checkDomainDB,
    addDomainToDB,
    updateDomainSuspicion,
    deleteDomainFromDB
 };

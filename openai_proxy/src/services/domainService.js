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

module.exports = { 
    checkDomainDB,
    addDomainToDB
 };

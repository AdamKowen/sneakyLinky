const express = require('express');
const router = express.Router();
const service = require('../../../services/domainHotsetService');
const logger = require('../../../utils/logger');


router.head('/latest-version', async (req, res) => {
    try {
        const latestVersion = await service.getLatestVersionNumber();
        res.set('Latest-Version', latestVersion);
        res.status(200).end();
    } catch (error) {
        console.error('Error fetching latest version:', error);
        res.status(500).json({ error: 'Internal Server Error' });
    }
});

router.get('/record/:version', async (req, res) => {
    const startTime = Date.now();
    const version = Number(req.params.version);
    
    logger.info(`[DomainHotset] GET /record/${version}`);
    
    try {
        if (!Number.isInteger(version) || version < 1) {
            logger.warn(`[DomainHotset] GET /record/${version} - Invalid version number`);
            return res.status(400).json({ error: 'Invalid version number' });
        }

        const record = await service.getVersionRecord(version);
        
        logger.debug(`[DomainHotset] GET /record/${version} - Record found`);
        logger.info(`[DomainHotset] GET /record/${version} - time taken: ${Date.now() - startTime}ms`);
        
        res.status(200).json({ record });
    } catch (error) {
        const elapsed = Date.now() - startTime;
        if (error.message === 'Record not found') {
            logger.warn(`[DomainHotset] GET /record/${version} - Record not found - Response time: ${elapsed}ms`);
            return res.status(404).json({ error: 'Version not found' });
        }
        
        logger.error(`[DomainHotset] GET /record/${version} - Error: ${error.message} - Response time: ${elapsed}ms`);
        res.status(500).json({ error: 'Internal Server Error' });
    }
});

router.get('/:version', async (req, res) => {
    const startTime = Date.now();
    const version = Number(req.params.version);
    
    logger.info(`[DomainHotset] GET /${version}`);
    let record = {
        whiteSnapshot: null,
        blackSnapshot: null,
        whiteAdd:      [],
        whiteRemove:   [],
        blackAdd:      [],
        blackRemove:   []
    };
    try {
        if (!Number.isInteger(version) || version < 1) {
            logger.warn(`[DomainHotset] GET /${version} - Invalid version number`);
            return res.status(400).json({ error: 'Invalid version number' });
        }

        const latestVersion = await service.getLatestVersionNumber();
        if (version > latestVersion) {
            logger.warn(`[DomainHotset] GET /${version} - Version not found`);
            return res.status(404).json({ error: 'Version not found' });
        }
        if (version === latestVersion) {
            logger.debug(`[DomainHotset] GET /${version} - Returning empty record for latest version`);
            logger.info(`[DomainHotset] GET /${version} - time taken: ${Date.now() - startTime}ms`);
            return res.status(200).json({ record });
        }
        if (version < await service.getOldestVersionNumber()) {
            const latestVersionRecord = await service.getVersionRecord(latestVersion);
            record.whiteSnapshot = latestVersionRecord.whiteSnapshot;
            record.blackSnapshot = latestVersionRecord.blackSnapshot;

            logger.debug(`[DomainHotset] GET /${version} - Returning full snapshots for version older than oldest`);
            logger.info(`[DomainHotset] GET /${version} - time taken: ${Date.now() - startTime}ms`);
            return res.status(200).json({ record });
        }
        
        const delta = await service.getDeltaChangesByVersion(version);
        record.whiteAdd    = delta.whiteAdd;
        record.whiteRemove = delta.whiteRemove;
        record.blackAdd    = delta.blackAdd;
        record.blackRemove = delta.blackRemove;

        logger.debug(`[DomainHotset] GET /${version} - Returning delta changes for version ${version}`);
        logger.info(`[DomainHotset] GET /${version} - time taken: ${Date.now() - startTime}ms`);
        return res.status(200).json({ record });
    } catch (error) {
        const elapsed = Date.now() - startTime;
        logger.error(`[DomainHotset] GET /${version} - Error: ${error.message} - Response time: ${elapsed}ms`);
        res.status(500).json({ error: 'Internal Server Error' });
    }
});

module.exports = router;




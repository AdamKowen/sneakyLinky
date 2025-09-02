const { get, update } = require('../models/Domain');
const repo = require('../repositories/domainHotsetRepository');
const { getLatestHotset } = require('../repositories/domainRepository');

const MAX_WHITE_LIST_SIZE = Number(process.env.MAX_WHITE_LIST_SIZE || 1000);
const MAX_BLACK_LIST_SIZE = Number(process.env.MAX_BLACK_LIST_SIZE || 1000);
const MAX_RECORDS_COUNT = Number(process.env.MAX_RECORDS_COUNT || 5);
const WHITE = 0;
const BLACK = 1;

//-------------------helpers--------------------------------------


function computeDelta(oldSnapShot, newSnapShot) {
    const oldSet = new Set(oldSnapShot);
    const newSet = new Set(newSnapShot);

    const add = [...newSet].filter(name => !oldSet.has(name)); // in new but not in old
    const remove = [...oldSet].filter(name => !newSet.has(name)); // in old but not in new

    return { add, remove };
}


async function updateOtherRecordsDelta(count, newRecord) {

    if (count <= 1) return; // no other records to update
    let version = newRecord.version;
    while (true) {
        const currentRecord = await repo.findByVersion(--version);
        if (!currentRecord) break; // no more records

        // calculate the delta
        const { add: whiteAdd, remove: whiteRemove } = computeDelta(
            currentRecord.whiteSnapshot,
            newRecord.whiteSnapshot
        );

        const { add: blackAdd, remove: blackRemove } = computeDelta(
            currentRecord.blackSnapshot,
            newRecord.blackSnapshot
        );

        // update the current record
        await repo.updateByVersion(currentRecord.version, {
            whiteAdd,
            whiteRemove,
            blackAdd,
            blackRemove
        });
    }
    
}

//-------------------API functions--------------------------------------

async function createDomainHotsetVersion() {

    try {
        // create the new version record
        const newRecord = {
            whiteSnapshot: await getLatestHotset({ suspicious: WHITE }, MAX_WHITE_LIST_SIZE),
            blackSnapshot: await getLatestHotset({ suspicious: BLACK }, MAX_BLACK_LIST_SIZE),
            whiteAdd:      null,
            whiteRemove:   null,
            blackAdd:      null,
            blackRemove:   null
        };

        //insert the new record
        const result = await repo.create(newRecord);

        // check that the count of the records is MAX_RECORDS_COUNT+1
        // if so, delete the oldest record
        const count = await repo.count();
        if (count > MAX_RECORDS_COUNT) {
            const oldest = await repo.findOldestVersion();
            if (oldest) {
                await repo.deleteByVersion(oldest.version);
            }
        }
        // update other records delta to the new record
        await updateOtherRecordsDelta(count, result);

        // return the new record
        return result;
    } catch (error) {
        throw error;
    }

}

async function getLatestVersionNumber() {
    try {
        const record = await repo.findLatest();
        if (!record) throw new Error('Record not found');
        return record.version;
    } catch (error) {
        throw error;
    }
}

async function getDeltaChangesByVersion(version) {
    try {
        const record = await repo.findByVersion(version);
        if (!record) throw new Error('Record not found');
        return {
            whiteAdd:    record.whiteAdd,
            whiteRemove: record.whiteRemove,
            blackAdd:    record.blackAdd,
            blackRemove: record.blackRemove
        };
    } catch (error) {
        throw error;
    }
}

async function getOldestVersionNumber() {
    try {
        const record = await repo.findOldestVersion();
        if (!record) throw new Error('Record not found');
        return record.version;
    } catch (error) {
        throw error;
    }
}

async function getVersionRecord(version) {
    try {
        const record = await repo.findByVersion(version);
        if (!record) throw new Error('Record not found');
        return record;
    } catch (error) {
        throw error;
    }
}

module.exports = {
    createDomainHotsetVersion,
    getLatestVersionNumber,
    getDeltaChangesByVersion,
    getOldestVersionNumber,
    getVersionRecord
};


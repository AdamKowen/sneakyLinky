const DomainHotset = require('../models/DomainHotset');
const { Op } = require('sequelize');


async function create(record) {
    return DomainHotset.create(record);
};

async function findLatest() {
    return DomainHotset.findOne({
        order: [['version', 'DESC']]
    })
};


async function findByVersion(version) {
    return DomainHotset.findOne({
        where: { version }
    })
};


async function count() {
    return DomainHotset.count();
};


async function deleteByVersion(version) {
    return DomainHotset.destroy({
        where: { version },
        limit: 1 // safety guard
    });
};


async function updateByVersion(version, data) {
    return DomainHotset.update(data, {
        where: { version }
    });
};

async function  findOldestVersion() {
    return DomainHotset.findOne({
        order: [['version', 'ASC']]
    });
};

module.exports = {
  create,
  findLatest,
  findByVersion,
  count,
  deleteByVersion,
  updateByVersion,
  findOldestVersion,
};
